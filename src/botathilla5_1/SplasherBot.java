package botathilla5_1;

import battlecode.common.*;

/**
 * botathilla5_1 Splasher:
 * - adaptive splash threshold by round/pressure
 * - frontier-weighted splash value
 * - refill hysteresis tuned for faster return to productive paint
 */
public class SplasherBot {

    private static MapLocation exploreLoc = null;
    private static int exploreSetRound = 0;
    private static MapLocation spawnTower = null;

    private static final double REFILL_LOW = 0.13;
    private static final double REFILL_HIGH = 0.46;
    private static final int REFILL_MAX_DIST = 100;

    private enum State { EXPLORE, REFILL }
    private static State state = State.EXPLORE;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();

        if (spawnTower == null) {
            initSpawnTower(rc, me);
            exploreLoc = Nav.pickExploreTarget(me, RobotPlayer.myID * 37 + round / 30);
            exploreSetRound = round;
        }

        Messaging.readMessages();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[] nearby = rc.senseNearbyMapInfos();

        for (RobotInfo a : allies) {
            if (a.type.isTowerType()) Messaging.registerTower(a.location, a.type);
        }
        Messaging.relayToNearbyTower(allies);

        state = decide(rc, me, enemies);
        GreedyCore.traceDecision(rc, "SPLASHER", "STATE", state.name(), 0, 0, 0);

        switch (state) {
            case REFILL -> refillState(rc, me, allies);
            case EXPLORE -> exploreState(rc, me, nearby, enemies);
        }

        me = rc.getLocation();

        if (rc.isActionReady() && rc.getPaint() >= 45) {
            MapLocation target = bestSplashTarget(rc, me, enemies);
            if (target != null && rc.canAttack(target)) {
                rc.attack(target);
                GreedyCore.traceDecision(rc, "SPLASHER", "SPLASH", target.x + "," + target.y, 1.0, 0.0, 0);
            }
        }

        Nav.recordPosition(me);
    }

    private static State decide(RobotController rc, MapLocation me, RobotInfo[] enemies) {
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;
        double pressure = localPressure(enemies);

        if ((state != State.REFILL && paintRatio < REFILL_LOW)
                || (state == State.REFILL && paintRatio < REFILL_HIGH)) {
            MapLocation pt = Messaging.nearestRefillTower(me);
            if (pt != null && me.distanceSquaredTo(pt) <= REFILL_MAX_DIST) return State.REFILL;
        }

        if (pressure > 4.5 && paintRatio < 0.22) return State.REFILL;
        return State.EXPLORE;
    }

    private static void refillState(RobotController rc, MapLocation me, RobotInfo[] allies) throws GameActionException {
        RobotInfo visPaint = null;
        int bpd = Integer.MAX_VALUE;
        RobotInfo visAny = null;
        int bad = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && a.paintAmount > 30) {
                int d = me.distanceSquaredTo(a.location);
                if (a.type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER && d < bpd) {
                    bpd = d;
                    visPaint = a;
                }
                if (d < bad) {
                    bad = d;
                    visAny = a;
                }
            }
        }
        RobotInfo vis = visPaint != null ? visPaint : visAny;
        MapLocation target = vis != null ? vis.location : Messaging.nearestRefillTower(me);
        if (target == null) target = spawnTower != null ? spawnTower : me;

        if (me.distanceSquaredTo(target) <= 2) {
            if (vis != null) {
                int space = rc.getType().paintCapacity - rc.getPaint();
                int amt = Math.min(space, vis.paintAmount);
                if (amt > 0 && rc.canTransferPaint(target, -amt)) rc.transferPaint(target, -amt);
            }
        } else {
            Nav.bugNav(target);
        }
    }

    private static void exploreState(RobotController rc, MapLocation me,
                                     MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        int round = rc.getRoundNum();
        if (me.distanceSquaredTo(exploreLoc) <= 8 || (round - exploreSetRound) > 30 || Nav.isLikelyStuck(me, 4)) {
            exploreLoc = Nav.pickExploreTarget(me, RobotPlayer.myID * 37 + round / 30);
            exploreSetRound = round;
        }

        if (!rc.isMovementReady()) return;

        MapLocation cluster = findPaintableCluster(nearby);
        if (cluster != null && me.distanceSquaredTo(cluster) > 4) {
            Nav.safeFuzzyMove(cluster, enemies);
        } else {
            Nav.bugNav(exploreLoc);
        }
    }

    private static MapLocation bestSplashTarget(RobotController rc, MapLocation me, RobotInfo[] enemies) throws GameActionException {
        MapLocation bestLoc = null;
        double bestVal = -1e9;
        double second = -1e9;
        int rejected = 0;

        double pressure = localPressure(enemies);
        int minNeed = minSplashValue(rc.getRoundNum(), pressure);

        MapLocation[] locs = rc.getAllLocationsWithinRadiusSquared(me, rc.getType().actionRadiusSquared);
        for (MapLocation loc : locs) {
            if (Clock.getBytecodesLeft() < 1900) break;
            if (!rc.canAttack(loc)) {
                rejected++;
                continue;
            }

            int enemyPaint = 0;
            int emptyPaint = 0;
            int allyPaint = 0;
            int frontier = 0;
            int[][] offsets = {
                    {0, 0},
                    {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1},
                    {-2, 0}, {2, 0}, {0, -2}, {0, 2}
            };

            for (int[] d : offsets) {
                MapLocation t = loc.translate(d[0], d[1]);
                if (!rc.canSenseLocation(t)) continue;
                MapInfo info = rc.senseMapInfo(t);
                if (info.isWall() || info.hasRuin()) continue;
                PaintType p = info.getPaint();
                if (p.isEnemy()) enemyPaint++;
                else if (p == PaintType.EMPTY) emptyPaint++;
                else allyPaint++;
                if ((p.isEnemy() || p == PaintType.EMPTY) && isFrontierTile(rc, t)) frontier++;
            }

            double risk = 0.0;
            for (RobotInfo e : enemies) {
                if (e.type.isTowerType() && loc.distanceSquaredTo(e.location) <= e.type.actionRadiusSquared) {
                    risk += 2.0;
                } else if (!e.type.isTowerType() && loc.distanceSquaredTo(e.location) <= 8) {
                    risk += 0.4;
                }
            }

            double value = GreedyCore.score(
                    emptyPaint * 0.9 + frontier,
                    enemyPaint * 1.7,
                    frontier * 1.2,
                    1.0,
                    0.3,
                    risk,
                    1.2 + allyPaint * 0.7);

            if (value > bestVal) {
                second = bestVal;
                bestVal = value;
                bestLoc = loc;
            } else if (value > second) {
                second = value;
            }
        }

        if (bestLoc != null && bestVal >= minNeed) {
            GreedyCore.traceDecision(rc, "SPLASHER", "TARGET", bestLoc.x + "," + bestLoc.y, bestVal, second, rejected);
            return bestLoc;
        }
        return null;
    }

    private static MapLocation findPaintableCluster(MapInfo[] nearby) {
        int sx = 0, sy = 0, w = 0;
        for (MapInfo info : nearby) {
            if (info.isWall() || info.hasRuin()) continue;
            PaintType p = info.getPaint();
            if (p == PaintType.EMPTY || p.isEnemy()) {
                int weight = p.isEnemy() ? 3 : 1;
                sx += info.getMapLocation().x * weight;
                sy += info.getMapLocation().y * weight;
                w += weight;
            }
        }
        if (w < 4) return null;
        return new MapLocation(sx / w, sy / w);
    }

    private static int minSplashValue(int round, double pressure) {
        int v = round < 120 ? 4 : round < 260 ? 3 : 2;
        if (pressure > 3.2) v--;
        return Math.max(1, v);
    }

    private static double localPressure(RobotInfo[] enemies) {
        double p = 0;
        for (RobotInfo e : enemies) {
            if (e.type == UnitType.SOLDIER) p += 1.3;
            else if (e.type == UnitType.SPLASHER) p += 1.0;
            else if (e.type == UnitType.MOPPER) p += 0.8;
            else p += 0.5;
        }
        return p;
    }

    private static boolean isFrontierTile(RobotController rc, MapLocation loc) throws GameActionException {
        for (Direction d : RobotPlayer.DIRS) {
            MapLocation adj = loc.add(d);
            if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) return true;
        }
        return false;
    }

    private static void initSpawnTower(RobotController rc, MapLocation me) throws GameActionException {
        spawnTower = me;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo r : rc.senseNearbyRobots(4, RobotPlayer.myTeam)) {
            if (r.type.isTowerType()) {
                int d = me.distanceSquaredTo(r.location);
                if (d < bd) {
                    bd = d;
                    spawnTower = r.location;
                }
            }
        }
    }
}

