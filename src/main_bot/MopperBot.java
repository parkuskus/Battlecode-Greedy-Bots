package main_bot;

import battlecode.common.*;

/**
 * Mopper — support unit.
 *
 * v5 changes from v3:
 * - Prime scatter for explore direction (myID * 37)
 * - Refill from any tower type (not just paint)
 * - Fix stale `me` after movement
 */
public class MopperBot {

    private static MapLocation exploreLoc = null;
    private static int exploreSetRound = 0;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();

        if (exploreLoc == null) setNewExploreTarget(rc, me, round);

        Messaging.readMessages();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[] nearby = rc.senseNearbyMapInfos();

        for (RobotInfo a : allies) if (a.type.isTowerType()) Messaging.registerTower(a.location, a.type);
        Messaging.relayToNearbyTower(allies);

        // 1. Refill jika paint sangat rendah
        if ((double) rc.getPaint() / rc.getType().paintCapacity < 0.15) {
            refill(rc, me, allies);
            Nav.recordPosition(rc.getLocation());
            return;
        }

        // 2. Mop action: swing ke musuh / mop enemy paint di tanah
        mopAction(rc, me, nearby, enemies);

        // 3. Transfer paint ke teman yang butuh
        transferPaintToAlly(rc, me, allies);

        // 4. Movement
        moveLogic(rc, me, nearby, allies, enemies, round);

        Nav.recordPosition(rc.getLocation());
    }

    // ---- Refill ----

    private static void refill(RobotController rc, MapLocation me, RobotInfo[] allies) throws GameActionException {
        // v5: accept any tower type for refill
        RobotInfo vis = null; int bd = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && a.paintAmount > 20) {
                int d = me.distanceSquaredTo(a.location);
                if (d < bd) { bd = d; vis = a; }
            }
        }
        MapLocation target = vis != null ? vis.location : Messaging.nearestRefillTower(me);
        if (target == null) { Nav.fuzzyMove(RobotPlayer.DIRS[RobotPlayer.myID % 8]); return; }

        if (me.distanceSquaredTo(target) <= 2) {
            int space = rc.getType().paintCapacity - rc.getPaint();
            if (space > 0 && rc.canTransferPaint(target, -space))
                rc.transferPaint(target, -space);
        } else Nav.bugNav(target);
    }

    // ---- Mop Action ----

    private static void mopAction(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        if (!rc.isActionReady()) return;

        // Swing ke musuh jika ada
        Direction bestSwing = null; int bestCount = 0;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (!rc.canMopSwing(dir)) continue;
            int count = countSwingTargets(rc, me, dir, enemies);
            if (count > bestCount) { bestCount = count; bestSwing = dir; }
        }
        if (bestSwing != null) { rc.mopSwing(bestSwing); return; }

        // Mop robot musuh dalam radius √2
        for (RobotInfo e : enemies) {
            if (!e.type.isTowerType() && me.distanceSquaredTo(e.location) <= 2 && rc.canAttack(e.location)) {
                rc.attack(e.location); return;
            }
        }

        // Mop enemy paint di tanah (hanya yang DEKAT, jangan chase)
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1000) return;
            if (info.getPaint().isEnemy() && me.distanceSquaredTo(info.getMapLocation()) <= 2
                && rc.canAttack(info.getMapLocation())) {
                rc.attack(info.getMapLocation()); return;
            }
        }
    }

    private static int countSwingTargets(RobotController rc, MapLocation me, Direction dir, RobotInfo[] enemies) {
        int count = 0;
        Direction left = dir.rotateLeft().rotateLeft();
        Direction right = dir.rotateRight().rotateRight();
        MapLocation s1 = me.add(dir);
        MapLocation s2 = s1.add(dir);
        MapLocation[] targets = {s1, s1.add(left), s1.add(right), s2, s2.add(left), s2.add(right)};
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            for (MapLocation t : targets)
                if (e.location.equals(t)) { count++; break; }
        }
        return count;
    }

    // ---- Paint Transfer ----

    private static void transferPaintToAlly(RobotController rc, MapLocation me, RobotInfo[] allies) throws GameActionException {
        if (!rc.isActionReady()) return;
        int myPaint = rc.getPaint();
        if (myPaint < rc.getType().paintCapacity * 0.4) return;

        RobotInfo bestTarget = null; double lowestRatio = 0.4;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() || me.distanceSquaredTo(a.location) > 2) continue;
            double ratio = (double) a.paintAmount / a.type.paintCapacity;
            if (ratio < lowestRatio) { lowestRatio = ratio; bestTarget = a; }
        }
        if (bestTarget != null) {
            int give = Math.min(myPaint / 3, bestTarget.type.paintCapacity - bestTarget.paintAmount);
            if (give > 0 && rc.canTransferPaint(bestTarget.location, give))
                rc.transferPaint(bestTarget.location, give);
        }
    }

    // ---- Movement ----

    private static void moveLogic(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies, int round) throws GameActionException {
        if (!rc.isMovementReady()) return;

        // Chase musuh non-tower HANYA jika dalam radius mop DAN on ally paint
        RobotInfo nearEn = null; int nd = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            int d = me.distanceSquaredTo(e.location);
            if (d < nd) { nd = d; nearEn = e; }
        }
        if (nearEn != null && nd <= 8) {
            MapLocation targetTile = me.add(me.directionTo(nearEn.location));
            if (rc.canSenseLocation(targetTile) && rc.senseMapInfo(targetTile).getPaint().isAlly()) {
                Nav.fuzzyMove(nearEn.location); return;
            }
        }

        // Mop adjacent enemy paint only
        MapLocation epaint = findNearestEnemyPaint(nearby, me);
        if (epaint != null && me.distanceSquaredTo(epaint) <= 2) {
            Nav.fuzzyMove(epaint); return;
        }

        // Check needMopperAt dari tower broadcast
        if (Messaging.needMopperAt != null && me.distanceSquaredTo(Messaging.needMopperAt) <= 100) {
            Nav.bugNav(Messaging.needMopperAt); return;
        }

        // Ikuti soldier terdekat (support)
        RobotInfo nearSoldier = null; int sd = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type == UnitType.SOLDIER) {
                int d = me.distanceSquaredTo(a.location);
                if (d < sd) { sd = d; nearSoldier = a; }
            }
        }
        if (nearSoldier != null && sd > 8) { Nav.fuzzyMove(nearSoldier.location); return; }

        // Patrol
        if (me.distanceSquaredTo(exploreLoc) <= 8 || (round - exploreSetRound) > 40)
            setNewExploreTarget(rc, me, round);
        Nav.bugNav(exploreLoc);
    }

    private static MapLocation findNearestEnemyPaint(MapInfo[] nearby, MapLocation me) {
        MapLocation best = null; int bd = Integer.MAX_VALUE;
        for (MapInfo info : nearby) {
            if (info.getPaint().isEnemy()) {
                int d = me.distanceSquaredTo(info.getMapLocation());
                if (d < bd) { bd = d; best = info.getMapLocation(); }
            }
        }
        return best;
    }

    private static void setNewExploreTarget(RobotController rc, MapLocation me, int round) {
        // v5: prime scatter
        int dirIdx = (RobotPlayer.myID * 37 + round / 40) % 8;
        exploreLoc = Nav.extendToEdge(me, RobotPlayer.DIRS[dirIdx]);
        exploreSetRound = round;
    }
}
