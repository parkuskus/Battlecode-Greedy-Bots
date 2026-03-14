package main_bot;

import battlecode.common.*;

/**
 * Splasher — fokus painting area, BUKAN serang musuh.
 *
 * v5 changes from v3:
 * - Correct outer ring: only 4 cardinal tiles at dist²=4 ({-2,0},{2,0},{0,-2},{0,2})
 * - Refill from any tower type (prefer paint)
 * - Fix stale `me` after movement
 * - Prime scatter for explore direction
 */
public class SplasherBot {

    private static MapLocation exploreLoc = null;
    private static int exploreSetRound = 0;
    private static MapLocation spawnTower = null;

    private static final double REFILL_LOW = 0.15;
    private static final double REFILL_HIGH = 0.50;
    private static final int REFILL_MAX_DIST = 100;
    private static final int MIN_SPLASH_VALUE = 3;

    private enum State { EXPLORE, REFILL }
    private static State state = State.EXPLORE;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();

        if (spawnTower == null) {
            initSpawnTower(rc, me);
            // v5: prime scatter
            exploreLoc = Nav.extendToEdge(me, RobotPlayer.DIRS[(RobotPlayer.myID * 37 + round / 30) % 8]);
            exploreSetRound = round;
        }

        Messaging.readMessages();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[] nearby = rc.senseNearbyMapInfos();

        for (RobotInfo a : allies) if (a.type.isTowerType()) Messaging.registerTower(a.location, a.type);
        Messaging.relayToNearbyTower(allies);

        state = decide(rc, me);

        switch (state) {
            case REFILL  -> refillState(rc, me, allies);
            case EXPLORE -> exploreState(rc, me, nearby, enemies);
        }

        // Refresh me after potential movement
        me = rc.getLocation();

        // Splash attack — fokus painting, bukan damage tower
        if (rc.isActionReady() && rc.getPaint() >= 50) {
            MapLocation target = bestSplashTarget(rc, me);
            if (target != null && rc.canAttack(target)) rc.attack(target);
        }

        Nav.recordPosition(me);
    }

    private static State decide(RobotController rc, MapLocation me) {
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;
        if ((state != State.REFILL && paintRatio < REFILL_LOW)
         || (state == State.REFILL && paintRatio < REFILL_HIGH)) {
            // v5: prefer paint tower but accept any
            MapLocation pt = Messaging.nearestRefillTower(me);
            if (pt != null && me.distanceSquaredTo(pt) <= REFILL_MAX_DIST) return State.REFILL;
        }
        return State.EXPLORE;
    }

    private static void refillState(RobotController rc, MapLocation me, RobotInfo[] allies) throws GameActionException {
        // v5: accept any tower type for refill, prefer paint
        RobotInfo visPaint = null; int bpd = Integer.MAX_VALUE;
        RobotInfo visAny = null; int bad = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && a.paintAmount > 30) {
                int d = me.distanceSquaredTo(a.location);
                if (a.type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                    if (d < bpd) { bpd = d; visPaint = a; }
                }
                if (d < bad) { bad = d; visAny = a; }
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
        } else Nav.bugNav(target);
    }

    private static void exploreState(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        int round = rc.getRoundNum();
        if (me.distanceSquaredTo(exploreLoc) <= 8 || (round - exploreSetRound) > 30) {
            // v5: prime scatter
            exploreLoc = Nav.extendToEdge(me, RobotPlayer.DIRS[(RobotPlayer.myID * 37 + round / 30) % 8]);
            exploreSetRound = round;
        }

        MapLocation cluster = findPaintableCluster(nearby, me);
        if (cluster != null && me.distanceSquaredTo(cluster) > 4)
            Nav.safeFuzzyMove(cluster, enemies);
        else if (rc.isMovementReady())
            Nav.bugNav(exploreLoc);
    }

    // ======== SPLASH TARGETING ========

    /**
     * v5: Corrected outer ring — only 4 cardinal tiles at exactly dist²=4.
     * Inner ring (3×3, dist² ≤ 2): overwrite enemy paint.
     * Outer cardinal (dist²=4): only paint empty.
     */
    private static MapLocation bestSplashTarget(RobotController rc, MapLocation me) throws GameActionException {
        MapLocation bestLoc = null; int bestVal = MIN_SPLASH_VALUE - 1;
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapLocation[] locs = rc.getAllLocationsWithinRadiusSquared(me, rc.getType().actionRadiusSquared);
        for (MapLocation loc : locs) {
            if (Clock.getBytecodesLeft() < 2000) break;
            if (!rc.canAttack(loc)) continue;
            // Skip target yang akan hit enemy tower
            boolean hitsEnemyTower = false;
            for (RobotInfo e : enemies) {
                if (e.type.isTowerType() && loc.distanceSquaredTo(e.location) <= 4) {
                    hitsEnemyTower = true; break;
                }
            }
            if (hitsEnemyTower) continue;
            int value = 0;
            // Inner ring (3×3, dist² ≤ 2): can overwrite enemy
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    MapLocation t = loc.translate(dx, dy);
                    if (!rc.canSenseLocation(t)) continue;
                    MapInfo info = rc.senseMapInfo(t);
                    if (info.isWall() || info.hasRuin()) continue;
                    if (info.getPaint() == PaintType.EMPTY) value += 2;
                    else if (info.getPaint().isEnemy()) value += 4;
                }
            }
            // v5: Outer ring — only 4 cardinal tiles at dist²=4 (splash paints empty only here)
            int[][] outerCardinal = {{-2,0},{2,0},{0,-2},{0,2}};
            for (int[] d : outerCardinal) {
                MapLocation t = loc.translate(d[0], d[1]);
                if (!rc.canSenseLocation(t)) continue;
                MapInfo info = rc.senseMapInfo(t);
                if (!info.isWall() && !info.hasRuin() && info.getPaint() == PaintType.EMPTY) value += 1;
            }
            if (value > bestVal) { bestVal = value; bestLoc = loc; }
        }
        return bestLoc;
    }

    private static MapLocation findPaintableCluster(MapInfo[] nearby, MapLocation me) {
        int sumX = 0, sumY = 0, count = 0;
        for (MapInfo info : nearby) {
            PaintType p = info.getPaint();
            if (p == PaintType.EMPTY || p.isEnemy()) {
                if (info.isWall() || info.hasRuin()) continue;
                sumX += info.getMapLocation().x;
                sumY += info.getMapLocation().y;
                count++;
            }
        }
        if (count < 3) return null;
        return new MapLocation(sumX / count, sumY / count);
    }

    private static void initSpawnTower(RobotController rc, MapLocation me) throws GameActionException {
        spawnTower = me; int bd = Integer.MAX_VALUE;
        for (RobotInfo r : rc.senseNearbyRobots(4, RobotPlayer.myTeam)) {
            if (r.type.isTowerType()) {
                int d = me.distanceSquaredTo(r.location);
                if (d < bd) { bd = d; spawnTower = r.location; }
            }
        }
    }
}
