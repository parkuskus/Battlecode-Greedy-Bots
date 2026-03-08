package aufar_bot_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Greedy Tower Controller — coverage-optimized spawning.
 *
 * Strategy: spawn as many painters (soldiers + splashers) as possible.
 * Phase-based spawning ratio tuned for maximum map coverage:
 *  - Early (round < 200, towers ≤ 5): 3:1 soldier:splasher (fast territory claim)
 *  - Mid (round 200-800): 2:1:1 soldier:splasher:mopper (balanced expansion)
 *  - Late (round > 800): 1:2:1 soldier:splasher:mopper (heavy area denial)
 *
 * Other duties:
 *  1. Upgrade when economically safe
 *  2. Attack enemies (prioritize lowest HP soldiers)
 *  3. Broadcast/relay tower positions
 *  4. Request moppers when enemy paint detected
 *  5. Self-destruct money towers when over-capitalized
 */
public class TowerBot {

    private static int spawnCounter = 0;
    private static MapLocation[] spawnLocs;
    private static MapLocation diagEnemy;
    private static boolean isFirstTower = false;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();

        if (spawnLocs == null) {
            spawnLocs = rc.getAllLocationsWithinRadiusSquared(me,
                    GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
            diagEnemy = new MapLocation(
                    RobotPlayer.mapW - me.x - 1,
                    RobotPlayer.mapH - me.y - 1);
            isFirstTower = rc.getRoundNum() < 8;
        }

        Comms.readMessages();
        Comms.registerTower(me, rc.getType());

        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        // 1. Upgrade
        tryUpgrade(rc, allies);

        // 2. Attack enemies
        attackBestTarget(rc, enemies);

        // 3. Broadcast tower location early
        if (rc.getRoundNum() < 5 && rc.canBroadcastMessage()) {
            rc.broadcastMessage(Comms.encodeTowerBuilt(me, rc.getType()));
        }

        // 4. Relay tower info to nearby units
        Comms.towerRelayToUnits(allies);

        // 5. Spawn units
        boolean isPaintTower = rc.getType().getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER;
        boolean shouldSpawn = isPaintTower || isFirstTower || rc.getChips() > 1200;

        if (shouldSpawn) {
            // Emergency mopper if enemy soldiers nearby
            for (RobotInfo e : enemies) {
                if (e.type == UnitType.SOLDIER) {
                    trySpawnDir(rc, UnitType.MOPPER, me.directionTo(e.location));
                    break;
                }
            }

            // Request mopper if enemy paint detected
            requestMopperIfNeeded(rc, me, allies);

            // Phase-based spawning
            spawnByPhase(rc, enemies);
        }

        // 6. Self-destruct money tower if rich & safe
        if (rc.getType().getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER
            && rc.getChips() > 10000
            && enemies.length == 0
            && allies.length > 0
            && rc.getRoundNum() > 200) {
            rc.disintegrate();
        }
    }

    // ====== Upgrade ======

    private static void tryUpgrade(RobotController rc, RobotInfo[] allies)
            throws GameActionException {
        if (!rc.canUpgradeTower(rc.getLocation())) return;
        boolean isPaint = rc.getType().getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER;
        int threshold = isPaint ? 2500 : 3600;
        int allyThreshold = isPaint ? 3 : 4;
        if (rc.getChips() > threshold
            && (allies.length >= allyThreshold || rc.getChips() > threshold + 1000)) {
            rc.upgradeTower(rc.getLocation());
        }
    }

    // ====== Attack ======

    private static void attackBestTarget(RobotController rc, RobotInfo[] enemies)
            throws GameActionException {
        RobotInfo best = null;
        int bestHP = Integer.MAX_VALUE;
        boolean foundSoldier = false;
        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.location)) continue;
            boolean isSoldier = e.type == UnitType.SOLDIER;
            if (isSoldier && !foundSoldier) {
                foundSoldier = true;
                best = e;
                bestHP = e.health;
            } else if (isSoldier && e.health < bestHP) {
                best = e;
                bestHP = e.health;
            } else if (!foundSoldier && e.health < bestHP) {
                best = e;
                bestHP = e.health;
            }
        }
        if (best != null) rc.attack(best.location);
    }

    // ====== Mopper Request ======

    private static void requestMopperIfNeeded(RobotController rc, MapLocation me,
            RobotInfo[] allies) throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo ti : tiles) {
            if (ti.getPaint().isEnemy()) {
                for (RobotInfo ally : allies) {
                    if (ally.type == UnitType.MOPPER && rc.canSendMessage(ally.location)) {
                        rc.sendMessage(ally.location,
                            Comms.encodeNeedMopper(ti.getMapLocation()));
                        break;
                    }
                }
                break;
            }
        }
    }

    // ====== Phase-Based Spawning ======
    // Key difference from kevin_bot_1: heavier splasher ratio mid/late for area coverage

    private static void spawnByPhase(RobotController rc, RobotInfo[] enemies)
            throws GameActionException {
        int round = rc.getRoundNum();
        int towers = rc.getNumberTowers();

        if (round < 200 && towers <= 5) {
            // Early: 3 soldiers then 1 splasher (fast territory claim)
            if (spawnCounter % 4 < 3) {
                if (!trySpawnBest(rc, UnitType.SOLDIER))
                    trySpawnBest(rc, UnitType.SPLASHER);
            } else {
                if (!trySpawnBest(rc, UnitType.SPLASHER))
                    trySpawnBest(rc, UnitType.SOLDIER);
            }
            spawnCounter++;
        } else if (round < 800) {
            // Mid: soldier(2) → splasher(1) → mopper(1) — coverage heavy
            int phase = spawnCounter % 4;
            if (phase < 2) {
                trySpawnBest(rc, UnitType.SOLDIER);
            } else if (phase == 2) {
                trySpawnBest(rc, UnitType.SPLASHER);
            } else {
                trySpawnBest(rc, UnitType.MOPPER);
            }
            spawnCounter++;
        } else if (rc.getChips() > 1000) {
            // Late: soldier(1) → splasher(2) → mopper(1) — heavy area denial
            int phase = spawnCounter % 4;
            if (phase == 0) {
                trySpawnBest(rc, UnitType.SOLDIER);
            } else if (phase < 3) {
                trySpawnBest(rc, UnitType.SPLASHER);
            } else {
                trySpawnBest(rc, UnitType.MOPPER);
            }
            spawnCounter++;
        }
    }

    /** Spawn at least-crowded location, prefer ally-painted tiles, avoid edges. */
    private static boolean trySpawnBest(RobotController rc, UnitType type)
            throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MAX_VALUE;
        for (MapLocation loc : spawnLocs) {
            if (!rc.canBuildRobot(type, loc)) continue;
            // Avoid edges
            if (loc.x < 3 || loc.y < 3
                || loc.x >= RobotPlayer.mapW - 3
                || loc.y >= RobotPlayer.mapH - 3) continue;
            int adj = countAdjacentAllies(rc, loc);
            int score = adj * 10;
            MapInfo info = rc.senseMapInfo(loc);
            if (!info.getPaint().isAlly()) score += 5; // prefer spawning on unpainted
            if (score < bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        if (best != null) {
            rc.buildRobot(type, best);
            return true;
        }
        return trySpawnDir(rc, type, rc.getLocation().directionTo(diagEnemy));
    }

    private static boolean trySpawnDir(RobotController rc, UnitType type, Direction dir)
            throws GameActionException {
        MapLocation loc2 = rc.getLocation().add(dir).add(dir);
        MapLocation loc1 = rc.getLocation().add(dir);
        if (rc.canBuildRobot(type, loc2)) { rc.buildRobot(type, loc2); return true; }
        if (rc.canBuildRobot(type, loc1)) { rc.buildRobot(type, loc1); return true; }
        return false;
    }

    private static int countAdjacentAllies(RobotController rc, MapLocation loc)
            throws GameActionException {
        int count = 0;
        for (RobotInfo r : rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam))
            if (!r.type.isTowerType()) count++;
        return count;
    }
}
