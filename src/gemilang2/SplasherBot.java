package gemilang2;

import battlecode.common.*;

public class SplasherBot {

    private static MapLocation exploreLoc = null;
    private static int exploreSetRound = 0;
    private static MapLocation spawnTower = null;
    private static boolean retreatActive = false;

    private static final double REFILL_LOW = 0.12;
    private static final double REFILL_HIGH = 0.44;
    private static final int REFILL_MAX_DIST = 120;

    private static final int IDX_RETREAT = 0;
    private static final int IDX_REFILL = 1;
    private static final int IDX_PRESSURE = 2;
    private static final int IDX_EXPLORE = 3;

    private static final GreedyCore.Metric METRIC = new GreedyCore.Metric();

    private enum State { RETREAT, REFILL, PRESSURE, EXPLORE }
    private static State state = State.EXPLORE;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();
        GreedyCore.startTurn(rc, METRIC);

        if (spawnTower == null) {
            initSpawnTower(rc, me);
            exploreLoc = Nav.extendToEdge(me, RobotPlayer.DIRS[(RobotPlayer.myID * 37 + round / 30) % 8]);
            exploreSetRound = round;
        }

        Messaging.readMessages();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[] nearby = rc.senseNearbyMapInfos();

        for (RobotInfo a : allies) if (a.type.isTowerType()) Messaging.registerTower(a.location, a.type);
        Messaging.relayToNearbyTower(allies);

        METRIC.enemyPaintSeen += countEnemyPaint(nearby);

        state = decide(rc, me, nearby, allies, enemies);

        switch (state) {
            case RETREAT -> retreatState(rc, me, allies, enemies);
            case REFILL -> refillState(rc, me, allies);
            case PRESSURE -> pressureState(rc, me, nearby, enemies);
            case EXPLORE -> exploreState(rc, me, nearby, enemies);
        }

        Nav.recordPosition(rc.getLocation());
        GreedyCore.endTurn(rc, METRIC);
        GreedyCore.traceMetric(rc, "SPLASHER", METRIC);
    }

    private static State decide(RobotController rc, MapLocation me, MapInfo[] nearby,
                                RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;
        double pressure = countEnemyPaint(nearby) * 0.15 + countNonTowerEnemies(enemies);

        MapLocation refill = findVisibleRefillTower(allies, me);
        if (refill == null) refill = Messaging.nearestRefillTower(me);

        MapLocation splashTarget = bestSplashTarget(rc, me, enemies);

        double[] score = new double[4];
        boolean[] feasible = new boolean[4];
        int[] dist = new int[4];
        int[] tie = new int[4];

        feasible[IDX_RETREAT] = retreatActive || paintRatio < 0.14 || pressure >= 3.8;
        score[IDX_RETREAT] = GreedyCore.score(0.2, 0.4, 5.2, 1.2, 0.3, paintRatio, 0.2);
        dist[IDX_RETREAT] = 0;
        tie[IDX_RETREAT] = 1;

        int refillDist = refill == null ? 999 : me.distanceSquaredTo(refill);
        feasible[IDX_REFILL] = refill != null && refillDist <= REFILL_MAX_DIST
                && ((state != State.REFILL && paintRatio < REFILL_LOW) || (state == State.REFILL && paintRatio < REFILL_HIGH));
        score[IDX_REFILL] = GreedyCore.score((1.0 - paintRatio) * 4.2, 0, 2.0, 2.6, 0.3, pressure * 0.2, refillDist * 0.03);
        dist[IDX_REFILL] = refillDist;
        tie[IDX_REFILL] = GreedyCore.tieId(refill);

        int splashDist = splashTarget == null ? 999 : me.distanceSquaredTo(splashTarget);
        feasible[IDX_PRESSURE] = splashTarget != null && rc.getPaint() >= 35;
        score[IDX_PRESSURE] = GreedyCore.score(3.8, 2.4, 3.8, 1.2, 0.5, pressure * 0.5, 1.1 + splashDist * 0.01);
        dist[IDX_PRESSURE] = splashDist;
        tie[IDX_PRESSURE] = GreedyCore.tieId(splashTarget);

        int exDist = exploreLoc == null ? 999 : me.distanceSquaredTo(exploreLoc);
        feasible[IDX_EXPLORE] = true;
        score[IDX_EXPLORE] = GreedyCore.score(2.3, 0.4, 2.2, 1.0, 0.4, pressure * 0.3, 0.6);
        dist[IDX_EXPLORE] = exDist;
        tie[IDX_EXPLORE] = GreedyCore.tieId(exploreLoc);

        int[] rejected = new int[1];
        double[] second = new double[1];
        int best = GreedyCore.pickBest(score, feasible, dist, tie, rejected, second);
        if (best < 0) best = IDX_EXPLORE;
        State next = switch (best) {
            case IDX_RETREAT -> State.RETREAT;
            case IDX_REFILL -> State.REFILL;
            case IDX_PRESSURE -> State.PRESSURE;
            default -> State.EXPLORE;
        };

        if (next == State.RETREAT && !retreatActive) {
            retreatActive = true;
            METRIC.retreatEntries++;
        } else if (retreatActive && next != State.RETREAT) {
            retreatActive = false;
            METRIC.retreatRecoveries++;
        }
        GreedyCore.traceDecision(rc, "SPLASHER", "STATE", score[best], second[0], rejected[0], next.name());
        return next;
    }

    private static void retreatState(RobotController rc, MapLocation me, RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        MapLocation target = findVisibleRefillTower(allies, me);
        if (target == null) target = Messaging.nearestRefillTower(me);
        if (target == null) target = spawnTower != null ? spawnTower : me;

        RobotInfo nearest = nearestNonTowerEnemy(me, enemies);
        if (rc.isMovementReady()) {
            if (nearest != null) {
                Direction away = me.directionTo(nearest.location).opposite();
                if (!Nav.fuzzyMove(away)) Nav.bugNav(target, enemies);
            } else Nav.bugNav(target, enemies);
        }
    }

    private static void refillState(RobotController rc, MapLocation me, RobotInfo[] allies) throws GameActionException {
        MapLocation target = findVisibleRefillTower(allies, me);
        if (target == null) target = Messaging.nearestRefillTower(me);
        if (target == null) target = spawnTower != null ? spawnTower : me;

        if (me.distanceSquaredTo(target) <= 2) {
            int need = rc.getType().paintCapacity - rc.getPaint();
            if (need > 0 && rc.canTransferPaint(target, -need)) rc.transferPaint(target, -need);
        } else Nav.bugNav(target);
    }

    private static void pressureState(RobotController rc, MapLocation me, MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        if (rc.isActionReady() && rc.getPaint() >= 35) {
            MapLocation target = bestSplashTarget(rc, me, enemies);
            if (target != null && rc.canAttack(target)) {
                SplashScore s = scoreSplash(rc, target, enemies);
                if (s.value > -0.05) {
                    rc.attack(target);
                    METRIC.effectivePaintActions += 1 + s.enemyPaint + s.frontier;
                    METRIC.enemyPaintCleaned += s.enemyPaint;
                    if (s.frontier > 0) {
                        METRIC.frontierAttempts++;
                        METRIC.frontierConverted++;
                    }
                }
            }
        }

        if (rc.isMovementReady()) {
            MapLocation focus = Messaging.contestedRuinAt != null && rc.getRoundNum() - Messaging.contestedRuinRound <= 5 ? Messaging.contestedRuinAt : null;
            if (focus == null) focus = findPaintCluster(nearby);
            if (focus != null && me.distanceSquaredTo(focus) > 4) Nav.safeFuzzyMove(focus, enemies);
            else Nav.bugNav(exploreLoc, enemies);
        }
    }

    private static void exploreState(RobotController rc, MapLocation me, MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        int round = rc.getRoundNum();
        if (me.distanceSquaredTo(exploreLoc) <= 8 || (round - exploreSetRound) > 30) {
            exploreLoc = Nav.extendToEdge(me, RobotPlayer.DIRS[(RobotPlayer.myID * 37 + round / 30) % 8]);
            exploreSetRound = round;
        }
        if (Messaging.enemyBlobAt != null && round - Messaging.enemyBlobRound <= 4) exploreLoc = Messaging.enemyBlobAt;

        if (rc.isMovementReady()) {
            MapLocation focus = Messaging.contestedRuinAt != null && rc.getRoundNum() - Messaging.contestedRuinRound <= 5 ? Messaging.contestedRuinAt : null;
            if (focus == null) focus = findPaintCluster(nearby);
            if (focus != null && me.distanceSquaredTo(focus) > 4) Nav.safeFuzzyMove(focus, enemies);
            else Nav.bugNav(exploreLoc, enemies);
        }

        if (rc.isActionReady() && rc.getPaint() >= 35) {
            MapLocation target = bestSplashTarget(rc, rc.getLocation(), enemies);
            if (target != null && rc.canAttack(target)) {
                SplashScore s = scoreSplash(rc, target, enemies);
                if (s.value > 0.0) {
                    rc.attack(target);
                    METRIC.effectivePaintActions += 1 + s.enemyPaint + s.frontier;
                    METRIC.enemyPaintCleaned += s.enemyPaint;
                }
            }
        }
    }

    private static MapLocation bestSplashTarget(RobotController rc, MapLocation me, RobotInfo[] enemies) throws GameActionException {
        MapLocation[] locs = rc.getAllLocationsWithinRadiusSquared(me, rc.getType().actionRadiusSquared);
        MapLocation best = null;
        double bestValue = -9999;
        double second = -9999;
        int rejected = 0;

        for (MapLocation loc : locs) {
            if (Clock.getBytecodesLeft() < 2000) break;
            if (!rc.canAttack(loc)) {
                rejected++;
                continue;
            }
            SplashScore s = scoreSplash(rc, loc, enemies);
            if (s.value > bestValue) {
                second = bestValue;
                bestValue = s.value;
                best = loc;
            } else if (s.value > second) second = s.value;
        }
        if (best != null) GreedyCore.traceDecision(rc, "SPLASHER", "SPLASH_TARGET", bestValue, second, rejected, GreedyCore.locLabel(best));
        return best;
    }

    private static SplashScore scoreSplash(RobotController rc, MapLocation center, RobotInfo[] enemies) throws GameActionException {
        int enemyPaint = 0;
        int emptyPaint = 0;
        int allyPaint = 0;
        int frontier = 0;

        int[][] offsets = {
                {0, 0},
                {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1},
                {-2, 0}, {2, 0}, {0, -2}, {0, 2}
        };
        for (int i = 0; i < offsets.length; i++) {
            MapLocation t = center.translate(offsets[i][0], offsets[i][1]);
            if (!rc.canSenseLocation(t)) continue;
            MapInfo info = rc.senseMapInfo(t);
            if (info.isWall() || info.hasRuin()) continue;
            PaintType p = info.getPaint();
            if (p.isEnemy()) enemyPaint++;
            else if (p == PaintType.EMPTY) emptyPaint++;
            else allyPaint++;
            if ((p.isEnemy() || p == PaintType.EMPTY) && isFrontierTile(rc, t, info)) frontier++;
        }

        double risk = 0.1;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType() && center.distanceSquaredTo(e.location) <= e.type.actionRadiusSquared) risk += 2.2;
            else if (!e.type.isTowerType() && center.distanceSquaredTo(e.location) <= 8) risk += 0.4;
        }
        double value = GreedyCore.score(emptyPaint * 0.9 + frontier, enemyPaint * 1.7, frontier * 1.3, 1.0, 0.35, risk, 1.1 + allyPaint * 0.6);
        return new SplashScore(enemyPaint, frontier, value);
    }

    private static MapLocation findPaintCluster(MapInfo[] nearby) {
        int sx = 0, sy = 0, c = 0;
        for (MapInfo info : nearby) {
            if (info.isWall() || info.hasRuin()) continue;
            PaintType p = info.getPaint();
            if (p == PaintType.EMPTY || p.isEnemy()) {
                sx += info.getMapLocation().x;
                sy += info.getMapLocation().y;
                c++;
            }
        }
        if (c < 3) return null;
        return new MapLocation(sx / c, sy / c);
    }

    private static MapLocation findVisibleRefillTower(RobotInfo[] allies, MapLocation me) {
        RobotInfo bestPaint = null;
        int bestPaintDist = Integer.MAX_VALUE;
        RobotInfo bestAny = null;
        int bestAnyDist = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (!a.type.isTowerType() || a.paintAmount <= 30) continue;
            int d = me.distanceSquaredTo(a.location);
            if (a.type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER && d < bestPaintDist) {
                bestPaint = a;
                bestPaintDist = d;
            }
            if (d < bestAnyDist) {
                bestAny = a;
                bestAnyDist = d;
            }
        }
        if (bestPaint != null) return bestPaint.location;
        return bestAny == null ? null : bestAny.location;
    }

    private static boolean isFrontierTile(RobotController rc, MapLocation loc, MapInfo info) throws GameActionException {
        PaintType p = info.getPaint();
        if (!(p == PaintType.EMPTY || p.isEnemy())) return false;
        for (Direction d : RobotPlayer.DIRS) {
            MapLocation adj = loc.add(d);
            if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) return true;
        }
        return false;
    }

    private static int countEnemyPaint(MapInfo[] nearby) {
        int c = 0;
        for (MapInfo info : nearby) if (info.getPaint().isEnemy()) c++;
        return c;
    }

    private static int countNonTowerEnemies(RobotInfo[] enemies) {
        int c = 0;
        for (RobotInfo e : enemies) if (!e.type.isTowerType()) c++;
        return c;
    }

    private static RobotInfo nearestNonTowerEnemy(MapLocation me, RobotInfo[] enemies) {
        RobotInfo best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            int d = me.distanceSquaredTo(e.location);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
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

    private static final class SplashScore {
        final int enemyPaint;
        final int frontier;
        final double value;

        SplashScore(int enemyPaint, int frontier, double value) {
            this.enemyPaint = enemyPaint;
            this.frontier = frontier;
            this.value = value;
        }
    }
}



