package gemilang2;

import battlecode.common.*;

public class MopperBot {

    private static MapLocation exploreLoc = null;
    private static int exploreSetRound = 0;

    private static final double REFILL_LOW = 0.16;

    private static final int IDX_REFILL = 0;
    private static final int IDX_CLEANUP = 1;
    private static final int IDX_SUPPORT = 2;
    private static final int IDX_SKIRMISH = 3;
    private static final int IDX_PATROL = 4;

    private static final GreedyCore.Metric METRIC = new GreedyCore.Metric();

    private enum State { REFILL, CLEANUP, SUPPORT, SKIRMISH, PATROL }
    private static State state = State.PATROL;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();
        GreedyCore.startTurn(rc, METRIC);

        if (exploreLoc == null) setNewExploreTarget(me, round);

        Messaging.readMessages();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[] nearby = rc.senseNearbyMapInfos();

        for (RobotInfo a : allies) if (a.type.isTowerType()) Messaging.registerTower(a.location, a.type);
        Messaging.relayToNearbyTower(allies);
        METRIC.enemyPaintSeen += countEnemyPaint(nearby);

        state = decide(rc, me, nearby, allies, enemies, round);

        switch (state) {
            case REFILL -> refill(rc, me, allies);
            case CLEANUP -> cleanupState(rc, me, nearby, enemies);
            case SUPPORT -> supportState(rc, me, allies, enemies, round);
            case SKIRMISH -> skirmishState(rc, me, enemies);
            case PATROL -> patrolState(rc, me, nearby, enemies, round);
        }

        transferPaintToAlly(rc, rc.getLocation(), allies);
        Nav.recordPosition(rc.getLocation());
        GreedyCore.endTurn(rc, METRIC);
        GreedyCore.traceMetric(rc, "MOPPER", METRIC);
    }

    private static State decide(RobotController rc, MapLocation me, MapInfo[] nearby,
                                RobotInfo[] allies, RobotInfo[] enemies, int round) {
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;
        int enemyPaint = countEnemyPaint(nearby);
        MapLocation refill = findVisibleTower(allies, me);
        if (refill == null) refill = Messaging.nearestRefillTower(me);
        MapLocation cleanup = nearestEnemyPaint(nearby, me, 20);
        MapLocation support = supportTarget(round);
        RobotInfo skirmish = nearestNonTowerEnemy(me, enemies);

        double[] score = new double[5];
        boolean[] feasible = new boolean[5];
        int[] dist = new int[5];
        int[] tie = new int[5];

        int refillDist = refill == null ? 999 : me.distanceSquaredTo(refill);
        feasible[IDX_REFILL] = refill != null && paintRatio < REFILL_LOW;
        score[IDX_REFILL] = GreedyCore.score((1 - paintRatio) * 4.0, 0.1, 2.0, 2.2, 0.4, enemyPaint * 0.1, refillDist * 0.03);
        dist[IDX_REFILL] = refillDist;
        tie[IDX_REFILL] = GreedyCore.tieId(refill);

        int cleanupDist = cleanup == null ? 999 : me.distanceSquaredTo(cleanup);
        feasible[IDX_CLEANUP] = cleanup != null;
        score[IDX_CLEANUP] = GreedyCore.score(2.1, enemyPaint * 0.9, 3.0, 1.0, 0.5, 0.3, cleanupDist * 0.07);
        dist[IDX_CLEANUP] = cleanupDist;
        tie[IDX_CLEANUP] = GreedyCore.tieId(cleanup);

        int supportDist = support == null ? 999 : me.distanceSquaredTo(support);
        feasible[IDX_SUPPORT] = support != null;
        score[IDX_SUPPORT] = GreedyCore.score(1.2, 1.0, 3.4, 0.9, 1.4, 0.4, supportDist * 0.06);
        dist[IDX_SUPPORT] = supportDist;
        tie[IDX_SUPPORT] = GreedyCore.tieId(support);

        int skDist = skirmish == null ? 999 : me.distanceSquaredTo(skirmish.location);
        feasible[IDX_SKIRMISH] = skirmish != null;
        score[IDX_SKIRMISH] = GreedyCore.score(0.8, 1.8, 2.3, 0.7, 0.4, skirmish == null ? 0 : skirmish.health * 0.02, 0.8);
        dist[IDX_SKIRMISH] = skDist;
        tie[IDX_SKIRMISH] = skirmish == null ? Integer.MAX_VALUE : skirmish.ID;

        feasible[IDX_PATROL] = true;
        score[IDX_PATROL] = GreedyCore.score(1.4, enemyPaint * 0.2, 1.8, 0.8, 0.8, 0.2, 0.4);
        dist[IDX_PATROL] = me.distanceSquaredTo(exploreLoc);
        tie[IDX_PATROL] = GreedyCore.tieId(exploreLoc);

        int[] rejected = new int[1];
        double[] second = new double[1];
        int best = GreedyCore.pickBest(score, feasible, dist, tie, rejected, second);
        if (best < 0) best = IDX_PATROL;
        State next = switch (best) {
            case IDX_REFILL -> State.REFILL;
            case IDX_CLEANUP -> State.CLEANUP;
            case IDX_SUPPORT -> State.SUPPORT;
            case IDX_SKIRMISH -> State.SKIRMISH;
            default -> State.PATROL;
        };
        GreedyCore.traceDecision(rc, "MOPPER", "STATE", score[best], second[0], rejected[0], next.name());
        return next;
    }

    private static void refill(RobotController rc, MapLocation me, RobotInfo[] allies) throws GameActionException {
        MapLocation target = findVisibleTower(allies, me);
        if (target == null) target = Messaging.nearestRefillTower(me);
        if (target == null) {
            Nav.fuzzyMove(RobotPlayer.DIRS[RobotPlayer.myID % 8]);
            return;
        }
        if (me.distanceSquaredTo(target) <= 2) {
            int need = rc.getType().paintCapacity - rc.getPaint();
            if (need > 0 && rc.canTransferPaint(target, -need)) rc.transferPaint(target, -need);
        } else Nav.bugNav(target);
    }

    private static void cleanupState(RobotController rc, MapLocation me, MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        if (rc.isActionReady()) tryBestMopAction(rc, me, nearby, enemies);
        MapLocation target = nearestEnemyPaint(nearby, me, 20);
        if (target != null && rc.isMovementReady() && !Nav.inEnemyTowerRange(target, enemies)) {
            Nav.safeFuzzyMove(target, enemies);
        }
    }

    private static void supportState(RobotController rc, MapLocation me, RobotInfo[] allies, RobotInfo[] enemies, int round) throws GameActionException {
        MapLocation target = supportTarget(round);
        if (target != null && rc.isMovementReady()) Nav.safeFuzzyMove(target, enemies);
        if (rc.isActionReady()) tryBestMopAction(rc, rc.getLocation(), rc.senseNearbyMapInfos(), enemies);
        if (target != null && rc.getLocation().distanceSquaredTo(target) <= 20 && rc.getPaint() > 30) {
            sendSignalToTower(allies, Messaging.encodeNeedMopper(target));
        }
    }

    private static void skirmishState(RobotController rc, MapLocation me, RobotInfo[] enemies) throws GameActionException {
        RobotInfo target = nearestNonTowerEnemy(me, enemies);
        if (target == null) {
            state = State.PATROL;
            return;
        }
        if (rc.isActionReady() && me.distanceSquaredTo(target.location) <= 2 && rc.canAttack(target.location)) {
            rc.attack(target.location);
            METRIC.effectivePaintActions++;
            if (target.paintAmount > 0) METRIC.enemyPaintCleaned++;
        }
        if (rc.isMovementReady()) Nav.safeFuzzyMove(target.location, enemies);
    }

    private static void patrolState(RobotController rc, MapLocation me, MapInfo[] nearby, RobotInfo[] enemies, int round) throws GameActionException {
        if (me.distanceSquaredTo(exploreLoc) <= 8 || (round - exploreSetRound) > 40) setNewExploreTarget(me, round);
        MapLocation target = nearestEnemyPaint(nearby, me, 20);
        if (target == null) target = supportTarget(round);
        if (target == null) target = exploreLoc;

        if (rc.isMovementReady()) Nav.safeFuzzyMove(target, enemies);
        if (rc.isActionReady()) tryBestMopAction(rc, rc.getLocation(), rc.senseNearbyMapInfos(), enemies);
    }

    private static boolean tryBestMopAction(RobotController rc, MapLocation me, MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        Direction bestSwing = null;
        int bestCount = 0;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (!rc.canMopSwing(dir)) continue;
            int count = countSwingTargets(me, dir, enemies);
            if (count > bestCount) {
                bestCount = count;
                bestSwing = dir;
            }
        }
        if (bestSwing != null && bestCount > 0) {
            rc.mopSwing(bestSwing);
            METRIC.effectivePaintActions += bestCount;
            METRIC.enemyPaintCleaned += bestCount;
            return true;
        }

        MapLocation best = null;
        double bestScore = -9999;
        double second = -9999;
        int rejected = 0;

        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            if (me.distanceSquaredTo(e.location) <= 2 && rc.canAttack(e.location)) {
                double s = GreedyCore.score(0.6, 1.6, 2.2, 0.6, 0.2, e.health * 0.02, 0.7);
                if (s > bestScore) {
                    second = bestScore;
                    bestScore = s;
                    best = e.location;
                } else if (s > second) second = s;
            }
        }

        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1600) break;
            if (!info.getPaint().isEnemy() || info.isWall() || info.hasRuin()) continue;
            MapLocation loc = info.getMapLocation();
            if (!rc.canAttack(loc) || me.distanceSquaredTo(loc) > 8) {
                rejected++;
                continue;
            }
            boolean frontier = isFrontierTile(rc, loc, info);
            double s = GreedyCore.score(1.3, 1.3, frontier ? 1.8 : 0.5, 0.7, 0.2, 0.2, me.distanceSquaredTo(loc) * 0.08);
            if (s > bestScore) {
                second = bestScore;
                bestScore = s;
                best = loc;
            } else if (s > second) second = s;
        }

        if (best == null) return false;
        MapInfo before = rc.canSenseLocation(best) ? rc.senseMapInfo(best) : null;
        boolean frontier = before != null && isFrontierTile(rc, best, before);
        if (frontier) METRIC.frontierAttempts++;
        rc.attack(best);
        METRIC.effectivePaintActions++;
        if (before != null && before.getPaint().isEnemy()) METRIC.enemyPaintCleaned++;
        if (frontier) METRIC.frontierConverted++;
        GreedyCore.traceDecision(rc, "MOPPER", "MOP_TARGET", bestScore, second, rejected, GreedyCore.locLabel(best));
        return true;
    }

    private static void transferPaintToAlly(RobotController rc, MapLocation me, RobotInfo[] allies) throws GameActionException {
        if (!rc.isActionReady()) return;
        int myPaint = rc.getPaint();
        if (myPaint < rc.getType().paintCapacity * 0.4) return;

        RobotInfo bestTarget = null;
        double lowestRatio = 0.4;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() || me.distanceSquaredTo(a.location) > 2) continue;
            double ratio = (double) a.paintAmount / a.type.paintCapacity;
            if (ratio < lowestRatio) {
                lowestRatio = ratio;
                bestTarget = a;
            }
        }
        if (bestTarget != null) {
            int give = Math.min(myPaint / 3, bestTarget.type.paintCapacity - bestTarget.paintAmount);
            if (give > 0 && rc.canTransferPaint(bestTarget.location, give)) rc.transferPaint(bestTarget.location, give);
        }
    }

    private static MapLocation supportTarget(int round) {
        if (Messaging.contestedRuinAt != null && round - Messaging.contestedRuinRound <= 6) return Messaging.contestedRuinAt;
        if (Messaging.needMopperAt != null) return Messaging.needMopperAt;
        if (Messaging.enemyBlobAt != null && round - Messaging.enemyBlobRound <= 6) return Messaging.enemyBlobAt;
        return null;
    }

    private static MapLocation findVisibleTower(RobotInfo[] allies, MapLocation me) {
        RobotInfo best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (!a.type.isTowerType() || a.paintAmount <= 20) continue;
            int d = me.distanceSquaredTo(a.location);
            if (d < bd) {
                bd = d;
                best = a;
            }
        }
        return best == null ? null : best.location;
    }

    private static MapLocation nearestEnemyPaint(MapInfo[] nearby, MapLocation me, int maxDist) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo info : nearby) {
            if (!info.getPaint().isEnemy() || info.isWall() || info.hasRuin()) continue;
            int d = me.distanceSquaredTo(info.getMapLocation());
            if (d <= maxDist && d < bd) {
                bd = d;
                best = info.getMapLocation();
            }
        }
        return best;
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

    private static int countSwingTargets(MapLocation me, Direction dir, RobotInfo[] enemies) {
        int count = 0;
        Direction left = dir.rotateLeft().rotateLeft();
        Direction right = dir.rotateRight().rotateRight();
        MapLocation s1 = me.add(dir);
        MapLocation s2 = s1.add(dir);
        MapLocation[] targets = {s1, s1.add(left), s1.add(right), s2, s2.add(left), s2.add(right)};
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            for (MapLocation t : targets) {
                if (e.location.equals(t)) {
                    count++;
                    break;
                }
            }
        }
        return count;
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

    private static void setNewExploreTarget(MapLocation me, int round) {
        int dirIdx = (RobotPlayer.myID * 37 + round / 40) % 8;
        exploreLoc = Nav.extendToEdge(me, RobotPlayer.DIRS[dirIdx]);
        exploreSetRound = round;
    }

    private static void sendSignalToTower(RobotInfo[] allies, int msg) throws GameActionException {
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType() && Messaging.trySendMessage(ally.location, msg)) return;
        }
    }
}
