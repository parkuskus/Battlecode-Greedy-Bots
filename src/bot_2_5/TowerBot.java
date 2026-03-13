package bot_2_5;

import battlecode.common.*;

/**
 * Tower v3 — smart spawn + conservative upgrades.
 * Key changes from bot_2_4:
 *  - Register tower in messaging system
 *  - Better attack targeting (prioritize soldiers, then lowest HP)
 *  - Smart spawn positioning (least crowded, ally-painted)
 *  - Emergency mopper if enemy soldiers nearby
 *  - Earlier splasher transition (round 400)
 *  - Conservative upgrades (5000/8000) to preserve chip pool for spawning
 */
public class TowerBot {

    private static int spawnCount = 0;
    private static MapLocation[] spawnLocs;
    private static MapLocation diagEnemy;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();

        if (spawnLocs == null) {
            spawnLocs = rc.getAllLocationsWithinRadiusSquared(me,
                    GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
            diagEnemy = new MapLocation(
                    RobotPlayer.mapW - me.x - 1,
                    RobotPlayer.mapH - me.y - 1);
        }

        Messaging.readMessages();
        Messaging.registerTower(me, rc.getType());

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        /* 1. Attack enemies — prioritize soldiers then lowest HP */
        attackBestTarget(rc, enemies);

        /* 2. Upgrade — middle ground thresholds */
        upgradeIfAffordable(rc, round);

        /* 3. Broadcast tower location early game */
        if (round < 5 && rc.canBroadcastMessage()) {
            rc.broadcastMessage(Messaging.encodeTowerBuilt(me, rc.getType()));
        }

        /* 4. Spawn units */
        spawnGreedy(rc, me, round, enemies);
    }

    /* ============================================================ */
    /*                    ATTACK ENEMIES                            */
    /* ============================================================ */

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
                best = e; bestHP = e.health;
            } else if (isSoldier && e.health < bestHP) {
                best = e; bestHP = e.health;
            } else if (!foundSoldier && e.health < bestHP) {
                best = e; bestHP = e.health;
            }
        }
        if (best != null) rc.attack(best.location);
    }

    /* ============================================================ */
    /*                    UPGRADE LOGIC                             */
    /* ============================================================ */

    private static void upgradeIfAffordable(RobotController rc, int round)
            throws GameActionException {
        UnitType type = rc.getType();
        int chips = rc.getChips();

        if (round < 100 || Messaging.towerCount < 3) return;

        if (type.level == 1 && chips >= 3000) {
            if (rc.canUpgradeTower(rc.getLocation()))
                rc.upgradeTower(rc.getLocation());
        } else if (type.level == 2 && round > 400 && chips >= 5500) {
            if (rc.canUpgradeTower(rc.getLocation()))
                rc.upgradeTower(rc.getLocation());
        }
    }

    /* ============================================================ */
    /*                    SPAWN LOGIC                               */
    /* ============================================================ */

    private static void spawnGreedy(RobotController rc, MapLocation me,
            int round, RobotInfo[] enemies) throws GameActionException {

        // Emergency: spawn mopper if enemy soldiers within range
        if (enemies.length > 0) {
            for (RobotInfo e : enemies) {
                if (e.type == UnitType.SOLDIER) {
                    Direction dir = me.directionTo(e.location);
                    MapLocation loc = me.add(dir);
                    if (rc.canBuildRobot(UnitType.MOPPER, loc)) {
                        rc.buildRobot(UnitType.MOPPER, loc);
                        sendTowerInfo(rc, loc);
                        spawnCount++;
                        return;
                    }
                    break;
                }
            }
        }

        UnitType toSpawn = chooseUnitType(round);

        // Try smart positioning first
        if (trySpawnBest(rc, toSpawn)) return;

        // Fallback: first available direction
        for (Direction dir : RobotPlayer.DIRS) {
            MapLocation loc = me.add(dir);
            if (rc.canBuildRobot(toSpawn, loc)) {
                rc.buildRobot(toSpawn, loc);
                sendTowerInfo(rc, loc);
                spawnCount++;
                return;
            }
        }
    }

    private static UnitType chooseUnitType(int round) {
        if (round < 100) {
            // Early: soldiers to claim territory and build towers
            return (spawnCount % 5 < 4) ? UnitType.SOLDIER : UnitType.MOPPER;
        } else if (round < 300) {
            // Mid: balanced — soldiers still needed for ruins + towers
            int mod = spawnCount % 5;
            return switch (mod) {
                case 0, 1 -> UnitType.SOLDIER;
                case 2, 3 -> UnitType.SPLASHER;
                default   -> UnitType.MOPPER;
            };
        } else {
            // Late: splasher-heavy for coverage push
            int mod = spawnCount % 4;
            return switch (mod) {
                case 0, 1 -> UnitType.SPLASHER;
                case 2    -> UnitType.SOLDIER;
                default   -> UnitType.MOPPER;
            };
        }
    }

    /**
     * Smart spawn: prefer least-crowded ally-painted tile.
     */
    private static boolean trySpawnBest(RobotController rc, UnitType type)
            throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MAX_VALUE;
        for (MapLocation loc : spawnLocs) {
            if (!rc.canBuildRobot(type, loc)) continue;
            MapInfo info = rc.senseMapInfo(loc);
            int adj = 0;
            for (RobotInfo r : rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam)) {
                if (!r.type.isTowerType()) adj++;
            }
            int score = adj * 10;
            if (!info.getPaint().isAlly()) score += 5;
            if (score < bestScore) { bestScore = score; best = loc; }
        }
        if (best != null) {
            rc.buildRobot(type, best);
            sendTowerInfo(rc, best);
            spawnCount++;
            return true;
        }
        return false;
    }

    private static void sendTowerInfo(RobotController rc, MapLocation target)
            throws GameActionException {
        for (int i = 0; i < Messaging.towerCount; i++) {
            if (rc.canSendMessage(target)) {
                rc.sendMessage(target,
                    Messaging.encodeTowerBuilt(
                        Messaging.knownTowers[i],
                        Messaging.knownTypes[i]));
            } else break;
        }
    }
}
