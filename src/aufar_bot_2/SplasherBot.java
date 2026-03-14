package aufar_bot_2;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Greedy Splasher — aggressive area-paint specialist.
 *
 * Strategy: flip enemy paint as fast as possible, paint empty areas too.
 * Lower splash threshold (2) compared to standard bots for more aggressive coverage.
 *
 * Priority:
 *  1. REFILL — aggressive low threshold (20%) to maximize uptime
 *  2. SPLASH — score all attackable tiles by enemy-paint density, attack if score ≥ 2
 *  3. FRONTIER — move toward enemy paint center to find targets
 *  4. EXPLORE — paint empty tiles when no enemy paint visible
 *  5. Confirm tower patterns when passing ruins
 */
public class SplasherBot {

    private static MapLocation spawnTower = null;
    private static MapLocation exploreLoc = null;

    private static final double REFILL_LOW  = 0.20;
    private static final double REFILL_HIGH = 0.75;
    private static final int    SPLASH_THRESHOLD = 2; // aggressive: splash with just 2 enemy tiles

    private enum State { EXPLORE, REFILL }
    private static State state = State.EXPLORE;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();

        if (spawnTower == null) {
            spawnTower = me;
            for (RobotInfo r : rc.senseNearbyRobots(4, RobotPlayer.myTeam))
                if (r.type.isTowerType()) { spawnTower = r.location; break; }
            Direction d = spawnTower.directionTo(me);
            if (d == Direction.CENTER) d = RobotPlayer.DIRS[RobotPlayer.myID % 8];
            exploreLoc = Nav.extendToEdge(me, d);
        }

        Comms.readMessages();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        Comms.updateFromVisible(allies);
        Comms.relayToNearbyTower(allies);

        // State decision
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;
        if ((state != State.REFILL && paintRatio < REFILL_LOW)
         || (state == State.REFILL && paintRatio < REFILL_HIGH)) {
            state = State.REFILL;
        } else {
            state = State.EXPLORE;
        }

        if (state == State.REFILL) {
            refill(rc, me, allies);
            return;
        }

        // Greedy splash targeting
        boolean splashed = false;
        if (rc.isActionReady()) {
            splashed = trySplash(rc, me, nearby, enemies);
        }

        // Evade enemy towers
        RobotInfo tower = Nav.nearestEnemyTower(enemies);
        if (tower != null
            && me.distanceSquaredTo(tower.location) <= tower.type.actionRadiusSquared) {
            Direction away = me.directionTo(tower.location).opposite();
            Nav.fuzzyMove(away);
        }

        // Move toward enemy paint frontier or empty areas 
        MapLocation enemyCenter = findEnemyPaintCenter(nearby, me);
        if (enemyCenter != null) {
            Nav.safeFuzzyMove(enemyCenter, enemies);
        } else {
            // No enemy paint — explore toward unpainted territory
            MapLocation emptyTarget = findEmptyCluster(nearby, me);
            if (emptyTarget != null) {
                Nav.safeFuzzyMove(emptyTarget, enemies);
            } else {
                if (me.distanceSquaredTo(exploreLoc) <= 8) {
                    exploreLoc = Nav.extendToEdge(me,
                        RobotPlayer.DIRS[(RobotPlayer.myID + rc.getRoundNum()) % 8]);
                }
                Nav.bugNav(exploreLoc);
            }
        }

        // Confirm tower patterns when near ruins 
        confirmPatterns(rc);
    }

    /** Score all attackable tiles and splash the best one. */
    private static boolean trySplash(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        int[][] scores = new int[11][11];
        int totalEnemy = 0;

        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 2000) break;
            if (info.getPaint().isEnemy()) {
                totalEnemy++;
                int cx = info.getMapLocation().x - me.x + 5;
                int cy = info.getMapLocation().y - me.y + 5;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = cx + dx, ny = cy + dy;
                        if (nx >= 0 && nx < 11 && ny >= 0 && ny < 11)
                            scores[nx][ny]++;
                    }
                }
            }
        }

        // Bonus for enemy towers
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) {
                int cx = e.location.x - me.x + 5;
                int cy = e.location.y - me.y + 5;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = cx + dx, ny = cy + dy;
                        if (nx >= 0 && nx < 11 && ny >= 0 && ny < 11)
                            scores[nx][ny] += 5;
                    }
            }
        }

        // Also give bonus for empty tiles (dual-purpose: paint AND flip)
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1500) break;
            if (info.getPaint() == PaintType.EMPTY && !info.isWall()) {
                int cx = info.getMapLocation().x - me.x + 5;
                int cy = info.getMapLocation().y - me.y + 5;
                if (cx >= 0 && cx < 11 && cy >= 0 && cy < 11)
                    scores[cx][cy]++; // small bonus for painting empty
            }
        }

        // Find best target in action range
        MapLocation bestTarget = null;
        int bestScore = 0;
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(me,
                rc.getType().actionRadiusSquared)) {
            int sx = loc.x - me.x + 5;
            int sy = loc.y - me.y + 5;
            if (sx >= 0 && sx < 11 && sy >= 0 && sy < 11
                && scores[sx][sy] > bestScore) {
                bestScore = scores[sx][sy];
                bestTarget = loc;
            }
        }

        // Attack if worthwhile (lower threshold = more aggressive)
        if (bestTarget != null
            && (bestScore >= SPLASH_THRESHOLD || bestScore >= totalEnemy)) {
            if (me.distanceSquaredTo(bestTarget) > 4) {
                Nav.moveIntoRange(bestTarget, 4);
            }
            if (rc.canAttack(bestTarget)) {
                rc.attack(bestTarget);
                return true;
            }
        }
        return false;
    }

    private static void refill(RobotController rc, MapLocation me,
            RobotInfo[] allies) throws GameActionException {
        RobotInfo nearestTower = null;
        int nearDist = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && a.paintAmount > 30) {
                int d = me.distanceSquaredTo(a.location);
                if (d < nearDist) { nearDist = d; nearestTower = a; }
            }
        }
        MapLocation target;
        if (nearestTower != null) {
            target = nearestTower.location;
        } else {
            MapLocation pt = Comms.nearestPaintTower(me);
            target = pt != null ? pt : (spawnTower != null ? spawnTower : me);
        }
        if (me.distanceSquaredTo(target) <= 2 && nearestTower != null) {
            int amt = Math.min(rc.getType().paintCapacity - rc.getPaint(),
                               nearestTower.paintAmount);
            if (amt > 0 && rc.canTransferPaint(target, -amt))
                rc.transferPaint(target, -amt);
            if ((double) rc.getPaint() / rc.getType().paintCapacity >= REFILL_HIGH)
                state = State.EXPLORE;
        } else {
            Nav.bugNav(target);
        }
    }

    /** Average position of all enemy paint tiles in vision. */
    private static MapLocation findEnemyPaintCenter(MapInfo[] nearby, MapLocation me) {
        int sx = 0, sy = 0, count = 0;
        for (MapInfo info : nearby) {
            if (info.getPaint().isEnemy()) {
                sx += info.getMapLocation().x;
                sy += info.getMapLocation().y;
                count++;
            }
        }
        if (count < 2) return null;
        return new MapLocation(sx / count, sy / count);
    }

    /** Find cluster of empty tiles to paint. */
    private static MapLocation findEmptyCluster(MapInfo[] nearby, MapLocation me) {
        int sx = 0, sy = 0, count = 0;
        for (MapInfo info : nearby) {
            if (info.getPaint() == PaintType.EMPTY && !info.isWall()) {
                sx += info.getMapLocation().x;
                sy += info.getMapLocation().y;
                count++;
            }
        }
        if (count < 3) return null;
        return new MapLocation(sx / count, sy / count);
    }

    private static void confirmPatterns(RobotController rc) throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        for (MapLocation ruin : ruins) {
            for (UnitType t : new UnitType[]{
                    UnitType.LEVEL_ONE_PAINT_TOWER,
                    UnitType.LEVEL_ONE_MONEY_TOWER,
                    UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
                if (rc.canCompleteTowerPattern(t, ruin)) {
                    rc.completeTowerPattern(t, ruin);
                    Comms.registerTower(ruin, t);
                    break;
                }
            }
        }
    }
}
