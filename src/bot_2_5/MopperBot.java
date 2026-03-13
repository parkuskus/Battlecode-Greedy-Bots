package bot_2_5;

import battlecode.common.*;

/**
 * Greedy Mopper — stable exploration, paint transfer, enemy cleanup.
 * Same logic as bot_2_4 with package fix.
 */
public class MopperBot {

    private static MapLocation exploreLoc = null;
    private static int         exploreSetRound = 0;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();

        if (exploreLoc == null) {
            setNewExploreTarget(rc, me, round);
        }

        Messaging.readMessages();
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[]   nearby  = rc.senseNearbyMapInfos();

        for (RobotInfo a : allies) {
            if (a.type.isTowerType()) Messaging.registerTower(a.location, a.type);
        }
        Messaging.relayToNearbyTower(allies);

        mopAction(rc, me, nearby, enemies);
        transferPaintToAlly(rc, me, allies);
        moveLogic(rc, me, nearby, allies, enemies, round);

        Nav.recordPosition(rc.getLocation());
    }

    /* ============================================================ */

    private static void mopAction(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        if (!rc.isActionReady()) return;

        for (RobotInfo e : enemies) {
            if (!e.type.isTowerType() && me.distanceSquaredTo(e.location) <= 2) {
                if (rc.canAttack(e.location)) {
                    rc.attack(e.location);
                    return;
                }
            }
        }

        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1000) return;
            MapLocation loc = info.getMapLocation();
            if (info.getPaint().isEnemy()
                && me.distanceSquaredTo(loc) <= rc.getType().actionRadiusSquared
                && rc.canAttack(loc)) {
                rc.attack(loc);
                return;
            }
        }
    }

    private static void transferPaintToAlly(RobotController rc, MapLocation me,
            RobotInfo[] allies) throws GameActionException {
        if (!rc.isActionReady()) return;
        int myPaint = rc.getPaint();
        int myMax   = rc.getType().paintCapacity;
        if (myPaint < myMax * 0.4) return;

        RobotInfo bestTarget = null;
        double lowestRatio = 0.4;

        for (RobotInfo a : allies) {
            if (a.type.isTowerType()) continue;
            if (me.distanceSquaredTo(a.location) > 2) continue;
            double ratio = (double) a.paintAmount / a.type.paintCapacity;
            if (ratio < lowestRatio) {
                lowestRatio = ratio;
                bestTarget = a;
            }
        }

        if (bestTarget != null) {
            int give = Math.min(myPaint / 3,
                bestTarget.type.paintCapacity - bestTarget.paintAmount);
            if (give > 0 && rc.canTransferPaint(bestTarget.location, give))
                rc.transferPaint(bestTarget.location, give);
        }
    }

    /* ============================================================ */

    private static void moveLogic(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies,
            int round) throws GameActionException {

        if (!rc.isMovementReady()) return;

        RobotInfo nearestEnemy = null;
        int nearestDist = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            int d = me.distanceSquaredTo(e.location);
            if (d < nearestDist) { nearestDist = d; nearestEnemy = e; }
        }
        if (nearestEnemy != null && nearestDist <= 20) {
            Nav.fuzzyMove(nearestEnemy.location);
            return;
        }

        MapLocation enemyPaint = findNearestEnemyPaint(nearby);
        if (enemyPaint != null && me.distanceSquaredTo(enemyPaint) <= 25) {
            Nav.fuzzyMove(enemyPaint);
            return;
        }

        boolean hasEnemyPaintNearby = false;
        for (MapInfo info : nearby) {
            if (info.getPaint().isEnemy()) { hasEnemyPaintNearby = true; break; }
        }
        if (hasEnemyPaintNearby) {
            RobotInfo nearSoldier = null;
            int bestSD = Integer.MAX_VALUE;
            for (RobotInfo a : allies) {
                if (a.type == UnitType.SOLDIER) {
                    int d = me.distanceSquaredTo(a.location);
                    if (d < bestSD) { bestSD = d; nearSoldier = a; }
                }
            }
            if (nearSoldier != null) {
                Nav.fuzzyMove(nearSoldier.location);
                return;
            }
        }

        if (me.distanceSquaredTo(exploreLoc) <= 8
            || (round - exploreSetRound) > 40) {
            setNewExploreTarget(rc, me, round);
        }
        Nav.bugNav(exploreLoc);
    }

    private static MapLocation findNearestEnemyPaint(MapInfo[] nearby) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        MapLocation me = RobotPlayer.rc.getLocation();
        for (MapInfo info : nearby) {
            if (info.getPaint().isEnemy()) {
                int d = me.distanceSquaredTo(info.getMapLocation());
                if (d < bestDist) { bestDist = d; best = info.getMapLocation(); }
            }
        }
        return best;
    }

    private static void setNewExploreTarget(RobotController rc, MapLocation me,
            int round) {
        int cycle = round / 40;
        int dirIdx = (RobotPlayer.myID * 3 + cycle) % 8;
        Direction dir = RobotPlayer.DIRS[dirIdx];
        exploreLoc = extendToEdge(me, dir);
        exploreSetRound = round;
    }

    private static MapLocation extendToEdge(MapLocation from, Direction dir) {
        MapLocation loc = from;
        for (int i = 0; i < 60; i++) {
            MapLocation next = loc.add(dir);
            if (next.x < 0 || next.y < 0
                || next.x >= RobotPlayer.mapW || next.y >= RobotPlayer.mapH) break;
            loc = next;
        }
        return loc;
    }
}
