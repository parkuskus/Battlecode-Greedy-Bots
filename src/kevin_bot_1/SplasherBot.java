package kevin_bot_1;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Greedy Splasher — area-paint specialist.
 *
 * Each turn greedily picks the action that maximizes paint conversion:
 *  1. If low paint → refill at nearest tower
 *  2. Score all attackable tiles by enemy-paint density in their 3×3 area
 *  3. Attack the tile with highest score (if score ≥ 4)
 *  4. Move toward enemy paint frontier to find more targets
 *  5. Confirm tower patterns when passing ruins
 */
public class SplasherBot {

    private static MapLocation spawnTower = null;
    private static MapLocation exploreLoc = null;

    private static final double REFILL_LOW  = 0.20;  // 75/300 ≈ aggressive threshold
    private static final double REFILL_HIGH = 0.75;
    private static final int    SPLASH_THRESHOLD = 3; // min enemy tiles to justify splash

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
            exploreLoc = extendToEdge(me, d);
        }

        Messaging.readMessages();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        updateKnownTowers(rc, allies);
        Messaging.relayToNearbyTower(allies);

        /* ---- decide state ---- */
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

        /* ---- greedy splash targeting ---- */
        MapLocation bestTarget = null;
        int bestScore = 0;

        if (rc.isActionReady()) {
            // Build an 11×11 scoring grid centered on me
            int[][] scores = new int[11][11];
            int totalEnemy = 0;

            for (MapInfo info : nearby) {
                if (Clock.getBytecodesLeft() < 2000) break;
                if (info.getPaint().isEnemy()) {
                    totalEnemy++;
                    int cx = info.getMapLocation().x - me.x + 5;
                    int cy = info.getMapLocation().y - me.y + 5;
                    // splash affects a 3×3 area, so each enemy tile boosts
                    // all action-range tiles within 1 step
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            int nx = cx + dx, ny = cy + dy;
                            if (nx >= 0 && nx < 11 && ny >= 0 && ny < 11)
                                scores[nx][ny]++;
                        }
                    }
                }
                // bonus for enemy towers
            }

            // also score enemy towers highly
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

            // find best target in action range
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

            // attack if worthwhile
            if (bestTarget != null
                && (bestScore >= SPLASH_THRESHOLD || bestScore >= totalEnemy)) {
                if (me.distanceSquaredTo(bestTarget) > 4) {
                    Nav.moveIntoRange(bestTarget, 4);
                }
                if (rc.canAttack(bestTarget)) {
                    rc.attack(bestTarget);
                }
            }
        }

        /* ---- evade enemy towers ---- */
        RobotInfo tower = Nav.nearestEnemyTower(enemies);
        if (tower != null
            && me.distanceSquaredTo(tower.location) <= tower.type.actionRadiusSquared) {
            Direction away = me.directionTo(tower.location).opposite();
            Nav.fuzzyMove(away);
        }

        /* ---- move toward enemy paint frontier ---- */
        MapLocation enemyCenter = findEnemyPaintCenter(nearby, me);
        if (enemyCenter != null) {
            Nav.safeFuzzyMove(enemyCenter, enemies);
        } else {
            /* explore to find more territory */
            if (me.distanceSquaredTo(exploreLoc) <= 8) {
                exploreLoc = extendToEdge(me,
                    RobotPlayer.DIRS[(RobotPlayer.myID + rc.getRoundNum()) % 8]);
            }
            Nav.bugNav(exploreLoc);
        }

        /* ---- confirm tower patterns if near ruins ---- */
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        for (MapLocation ruin : ruins) {
            for (UnitType t : new UnitType[]{
                    UnitType.LEVEL_ONE_PAINT_TOWER,
                    UnitType.LEVEL_ONE_MONEY_TOWER,
                    UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
                if (rc.canCompleteTowerPattern(t, ruin)) {
                    rc.completeTowerPattern(t, ruin);
                    Messaging.registerTower(ruin, t);
                    break;
                }
            }
        }
    }

    /* ---- refill at nearest tower ---- */
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
            MapLocation pt = Messaging.nearestPaintTower(me);
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

    /* ---- utilities ---- */
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

    private static void updateKnownTowers(RobotController rc, RobotInfo[] allies) {
        for (RobotInfo a : allies)
            if (a.type.isTowerType())
                Messaging.registerTower(a.location, a.type);
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
