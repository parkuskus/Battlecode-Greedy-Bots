package alternative_bots_1;
import battlecode.common.*;

public class SplasherBot {
    private static MapLocation exploreLoc = null;
    private static int exploreSetRound = 0;
    private static MapLocation spawnTower = null;

    private static final double REFILL_LOW  = 0.15;
    private static final double REFILL_HIGH = 0.50;
    private static final int REFILL_MAX_DIST = 100;
    private static final int MIN_SPLASH_VALUE = 2;

    private enum State { EXPLORE, REFILL, COMBAT }
    private static State state = State.EXPLORE;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();

        if (spawnTower == null) {
            initSpawnTower(rc, me);
            int dirIdx = (RobotPlayer.myID + round / 30) % 8;
            exploreLoc = extendToEdge(me, RobotPlayer.DIRS[dirIdx]);
            exploreSetRound = round;
        }

        Messaging.readMessages();
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[]   nearby  = rc.senseNearbyMapInfos();

        for (RobotInfo a : allies) {
            if (a.type.isTowerType()) Messaging.registerTower(a.location, a.type);
        }
        Messaging.relayToNearbyTower(allies);

        state = decide(rc, me, enemies);

        switch (state) {
            case REFILL  -> refillState(rc, me, allies);
            case COMBAT  -> combatState(rc, me, enemies);
            case EXPLORE -> exploreState(rc, me, nearby, enemies);
        }

        // serangan splash
        if (rc.isActionReady()) {
            MapLocation target = bestSplashTarget(rc, me, nearby, enemies);
            if (target != null && rc.canAttack(target)) rc.attack(target);
        }

        // warnai tile saat ini
        if (rc.isActionReady()) {
            MapInfo mi = rc.senseMapInfo(me);
            PaintType p = mi.getPaint();
            if ((p == PaintType.EMPTY || p.isEnemy()) && rc.canAttack(me))
                rc.attack(me);
        }

        Nav.recordPosition(rc.getLocation());
    }   

    // GREEDY PRIORITY
    // fokus untuk refill jika mungkin. Jika syarat terpenuhi, serang musuh
    private static State decide(RobotController rc, MapLocation me,
            RobotInfo[] enemies) {
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;

        if ((state != State.REFILL && paintRatio < REFILL_LOW)
         || (state == State.REFILL && paintRatio < REFILL_HIGH)) {
            MapLocation pt = Messaging.nearestPaintTower(me);
            if (pt != null && me.distanceSquaredTo(pt) <= REFILL_MAX_DIST)
                return State.REFILL;
        }

        RobotInfo et = Nav.nearestEnemyTower(enemies);
        if (et != null && rc.getHealth() > 60 && paintRatio > 0.30
            && et.type.getBaseType() != UnitType.LEVEL_ONE_DEFENSE_TOWER)
            return State.COMBAT;

        return State.EXPLORE;
    }


    // REFILL STATE
    private static void refillState(RobotController rc, MapLocation me,
            RobotInfo[] allies) throws GameActionException {
        RobotInfo vis = null;
        int bestD = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType()
                && a.type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER
                && a.paintAmount > 30) {
                int d = me.distanceSquaredTo(a.location);
                if (d < bestD) { bestD = d; vis = a; }
            }
        }

        MapLocation target;
        if (vis != null) {
            target = vis.location;
        } else {
            MapLocation known = Messaging.nearestPaintTower(me);
            target = known != null ? known : (spawnTower != null ? spawnTower : me);
        }

        if (me.distanceSquaredTo(target) <= 2) {
            if (vis != null) {
                int space = rc.getType().paintCapacity - rc.getPaint();
                int amt = Math.min(space, vis.paintAmount);
                if (amt > 0 && rc.canTransferPaint(target, -amt))
                    rc.transferPaint(target, -amt);
            }
        } else {
            Nav.bugNav(target);
        }
    }

    // COMBAT STATE
    private static void combatState(RobotController rc, MapLocation me,
            RobotInfo[] enemies) throws GameActionException {
        RobotInfo tower = Nav.nearestEnemyTower(enemies);
        if (tower == null) return;

        if (me.distanceSquaredTo(tower.location) <= rc.getType().actionRadiusSquared) {
            if (rc.canAttack(tower.location)) rc.attack(tower.location);
            Direction away = me.directionTo(tower.location).opposite();
            Nav.fuzzyMove(away);
        } else {
            Nav.moveIntoRange(tower.location, rc.getType().actionRadiusSquared);
            if (rc.canAttack(tower.location)) rc.attack(tower.location);
        }
    }

    // EXPLORE STATE
    private static void exploreState(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {

        int round = rc.getRoundNum();

        if (me.distanceSquaredTo(exploreLoc) <= 8
            || (round - exploreSetRound) > 30) {
            int dirIdx = (RobotPlayer.myID + round / 30) % 8;
            exploreLoc = extendToEdge(me, RobotPlayer.DIRS[dirIdx]);
            exploreSetRound = round;
        }

        MapLocation enemyCluster = findEnemyPaintCluster(rc, me, nearby);
        if (enemyCluster != null && me.distanceSquaredTo(enemyCluster) > 4) {
            Nav.fuzzyMove(enemyCluster);
        } else if (rc.isMovementReady()) {
            Nav.bugNav(exploreLoc);
        }
    }

    // Sistem penentuan splash target terbaik
    // greedy scoring algorithm
    private static MapLocation bestSplashTarget(RobotController rc,
            MapLocation me, MapInfo[] nearby, RobotInfo[] enemies)
            throws GameActionException {

        if (!rc.isActionReady()) return null;

        MapLocation bestLoc = null;
        int bestVal = MIN_SPLASH_VALUE - 1;

        MapLocation[] locs = rc.getAllLocationsWithinRadiusSquared(
            me, rc.getType().actionRadiusSquared);

        for (MapLocation loc : locs) {
            if (Clock.getBytecodesLeft() < 2000) break;
            if (!rc.canAttack(loc)) continue;

            int value = 0;
            // Inner ring (dist^2 <= 2)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    MapLocation t = loc.translate(dx, dy);
                    if (!rc.canSenseLocation(t)) continue;
                    MapInfo info = rc.senseMapInfo(t);
                    if (info.isWall() || info.hasRuin()) continue;
                    if (info.getPaint() == PaintType.EMPTY) value += 2;
                    else if (info.getPaint().isEnemy()) value += 4; // overwrite = territory swing
                }
            }
            // Outer ring (dist^2 = 4)
            int[][] outer = {{-2,0},{2,0},{0,-2},{0,2}};
            for (int[] d : outer) {
                MapLocation t = loc.translate(d[0], d[1]);
                if (!rc.canSenseLocation(t)) continue;
                MapInfo info = rc.senseMapInfo(t);
                if (!info.isWall() && !info.hasRuin()
                    && info.getPaint() == PaintType.EMPTY) value += 1;
            }

            if (value > bestVal) {
                bestVal = value;
                bestLoc = loc;
            }
        }
        return bestLoc;
    }

    private static MapLocation findEnemyPaintCluster(RobotController rc,
            MapLocation me, MapInfo[] nearby) {
        int sumX = 0, sumY = 0, count = 0;
        for (MapInfo info : nearby) {
            if (info.getPaint().isEnemy()) {
                sumX += info.getMapLocation().x;
                sumY += info.getMapLocation().y;
                count++;
            }
        }
        if (count < 3) return null;
        return new MapLocation(sumX / count, sumY / count);
    }

    private static void initSpawnTower(RobotController rc, MapLocation me)
            throws GameActionException {
        spawnTower = me;
        int bestD = Integer.MAX_VALUE;
        for (RobotInfo r : rc.senseNearbyRobots(4, RobotPlayer.myTeam)) {
            if (r.type.isTowerType()) {
                int d = me.distanceSquaredTo(r.location);
                if (d < bestD) { bestD = d; spawnTower = r.location; }
            }
        }
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
