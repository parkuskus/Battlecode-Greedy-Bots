package botathilla5;

import battlecode.common.*;

/**
 * Tower — fokus: bangun tower sebanyak-banyaknya dulu, baru spawn unit.
 *
 * v5 changes from v3:
 * - Removed tower flickering entirely (too risky, self-sabotages economy)
 * - Broadcast on first turn using hasBroadcast flag (not round < 5)
 * - Cap tower relay to newly spawned unit at 3 messages
 */
public class TowerBot {

    private static int spawnCount = 0;
    private static MapLocation[] spawnLocs;
    private static boolean hasBroadcast = false;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();

        if (spawnLocs == null) {
            spawnLocs = rc.getAllLocationsWithinRadiusSquared(me, GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
        }

        Messaging.readMessages();
        Messaging.registerTower(me, rc.getType());

        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        // 1. Attack — target soldiers first, then lowest HP
        attackBest(rc, enemies);

        // 2. Upgrade diri sendiri — konservatif
        upgradeIfAffordable(rc, round);

        // 3. Broadcast tower info on first turn (once)
        if (!hasBroadcast && rc.canBroadcastMessage()) {
            rc.broadcastMessage(Messaging.encodeTowerBuilt(me, rc.getType()));
            hasBroadcast = true;
        }

        // 4. Relay tower list ke robot baru
        relayTowerList(rc, allies);

        // 5. Spawn units — greedy by phase
        spawnGreedy(rc, me, round, allies, enemies);
    }

    // ---- Attack ----

    private static void attackBest(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo best = null; int bestHP = Integer.MAX_VALUE; boolean foundSoldier = false;
        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.location)) continue;
            boolean isSoldier = e.type == UnitType.SOLDIER;
            if (isSoldier && !foundSoldier) { foundSoldier = true; best = e; bestHP = e.health; }
            else if (isSoldier && e.health < bestHP) { best = e; bestHP = e.health; }
            else if (!foundSoldier && e.health < bestHP) { best = e; bestHP = e.health; }
        }
        if (best != null) rc.attack(best.location);
    }

    // ---- Upgrade ----

    private static void upgradeIfAffordable(RobotController rc, int round) throws GameActionException {
        if (round < 100 || Messaging.towerCount < 3) return;
        UnitType type = rc.getType();
        int chips = rc.getChips();
        if (type.level == 1 && chips >= 3000 && rc.canUpgradeTower(rc.getLocation()))
            rc.upgradeTower(rc.getLocation());
        else if (type.level == 2 && round > 400 && chips >= 5500 && rc.canUpgradeTower(rc.getLocation()))
            rc.upgradeTower(rc.getLocation());
    }

    // ---- Relay ----

    /** Relay tower list to nearby non-tower allies — capped at 3 messages per ally. */
    private static void relayTowerList(RobotController rc, RobotInfo[] allies) throws GameActionException {
        for (RobotInfo ally : allies) {
            if (!ally.type.isTowerType() && rc.canSendMessage(ally.location)) {
                int sent = 0;
                for (int i = 0; i < Messaging.towerCount && sent < 3; i++) {
                    if (rc.canSendMessage(ally.location)) {
                        rc.sendMessage(ally.location,
                            Messaging.encodeTowerBuilt(Messaging.knownTowers[i], Messaging.knownTypes[i]));
                        sent++;
                    }
                }
            }
        }
    }

    // ---- Spawn ----

    private static void spawnGreedy(RobotController rc, MapLocation me, int round,
            RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {

        // Emergency: ada soldier musuh dekat tower → spawn mopper darurat
        for (RobotInfo e : enemies) {
            if (e.type == UnitType.SOLDIER) {
                Direction dir = me.directionTo(e.location);
                MapLocation loc = me.add(dir);
                if (rc.canBuildRobot(UnitType.MOPPER, loc)) {
                    rc.buildRobot(UnitType.MOPPER, loc);
                    sendTowerInfo(rc, loc); spawnCount++; return;
                }
                break;
            }
        }

        // Notify mopper kalau ada enemy paint nearby
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo ti : tiles) {
            if (ti.getPaint().isEnemy()) {
                for (RobotInfo ally : allies) {
                    if (ally.type == UnitType.MOPPER && rc.canSendMessage(ally.location)) {
                        rc.sendMessage(ally.location, Messaging.encodeNeedMopper(ti.getMapLocation()));
                        break;
                    }
                }
                break;
            }
        }

        UnitType toSpawn = chooseUnitType(round);
        if (trySpawnBest(rc, toSpawn)) return;

        // Fallback
        for (Direction dir : RobotPlayer.DIRS) {
            MapLocation loc = me.add(dir);
            if (rc.canBuildRobot(toSpawn, loc)) {
                rc.buildRobot(toSpawn, loc);
                sendTowerInfo(rc, loc); spawnCount++; return;
            }
        }
    }

    private static UnitType chooseUnitType(int round) {
        if (round < 150) {
            return (spawnCount % 5 < 4) ? UnitType.SOLDIER : UnitType.MOPPER;
        } else if (round < 400) {
            int mod = spawnCount % 5;
            return switch (mod) {
                case 0, 1 -> UnitType.SOLDIER;
                case 2, 3 -> UnitType.SPLASHER;
                default   -> UnitType.MOPPER;
            };
        } else {
            int mod = spawnCount % 4;
            return switch (mod) {
                case 0, 1 -> UnitType.SPLASHER;
                case 2    -> UnitType.SOLDIER;
                default   -> UnitType.MOPPER;
            };
        }
    }

    private static boolean trySpawnBest(RobotController rc, UnitType type) throws GameActionException {
        MapLocation best = null; int bestScore = Integer.MAX_VALUE;
        for (MapLocation loc : spawnLocs) {
            if (!rc.canBuildRobot(type, loc)) continue;
            MapInfo info = rc.senseMapInfo(loc);
            int adj = 0;
            for (RobotInfo r : rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam))
                if (!r.type.isTowerType()) adj++;
            int score = adj * 10;
            if (!info.getPaint().isAlly()) score += 5;
            if (score < bestScore) { bestScore = score; best = loc; }
        }
        if (best != null) {
            rc.buildRobot(type, best);
            sendTowerInfo(rc, best); spawnCount++; return true;
        }
        return false;
    }

    /** Send tower info to newly spawned unit — capped at 3 messages. */
    private static void sendTowerInfo(RobotController rc, MapLocation target) throws GameActionException {
        int sent = 0;
        for (int i = 0; i < Messaging.towerCount && sent < 3; i++) {
            if (rc.canSendMessage(target)) {
                rc.sendMessage(target, Messaging.encodeTowerBuilt(Messaging.knownTowers[i], Messaging.knownTypes[i]));
                sent++;
            } else break;
        }
    }
}
