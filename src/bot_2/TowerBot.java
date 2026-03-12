package bot_2;

import battlecode.common.*;

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
        // attack lawan
        attackBestTarget(rc, enemies);
        // upgrade
        upgradeIfAffordable(rc, round);
        // broadcast lokasi tower
        if (round < 5 && rc.canBroadcastMessage()) rc.broadcastMessage(Messaging.encodeTowerBuilt(me, rc.getType()));
        // spawn units
        spawnGreedy(rc, me, round, enemies);
    }

    // algoritma greedy, mencari musuh dengan HP terendah
    // akan diprioritaskan soldier terlebih dahulu untuk diserang (mencegah penyerangan ke teritori kita)
    private static void attackBestTarget(RobotController rc, RobotInfo[] enemies)
            throws GameActionException {
        RobotInfo best = null;
        int bestHP = Integer.MAX_VALUE;
        boolean foundSoldier = false;
        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.location)) continue; // tower akan mengabaikan musuh yang berada di luar jangkauan tembak
            boolean isSoldier = e.type == UnitType.SOLDIER;
            if (isSoldier && !foundSoldier) {
                foundSoldier = true;
                best = e; bestHP = e.health;
            } else if (isSoldier && e.health < bestHP) { // ganti sudah ditemtukan soldier, ganti ke soldier lain dengan hp yang lebih rendah
                best = e; bestHP = e.health;
            } else if (!foundSoldier && e.health < bestHP) { // target non-soldier
                best = e; bestHP = e.health;
            }
        }
        if (best != null) rc.attack(best.location);
    }

    // sistem ekonomi dan manajemen tower
    private static void upgradeIfAffordable(RobotController rc, int round)
            throws GameActionException {
        UnitType type = rc.getType();
        int chips = rc.getChips();
        if (round < 100 || Messaging.towerCount < 3) return;
        // level 1 ke 2 = 3000 chips
        if (type.level == 1 && chips >= 3000) {
            if (rc.canUpgradeTower(rc.getLocation()))
                rc.upgradeTower(rc.getLocation());
        }
        // level 2 ke 3 = ronde > 400 dan chips >= 5500
        else if (type.level == 2 && round > 400 && chips >= 5500) {
            if (rc.canUpgradeTower(rc.getLocation()))
                rc.upgradeTower(rc.getLocation());
        }
    }

    // sistem spawn unit pada tower
    private static void spawnGreedy(RobotController rc, MapLocation me,
            int round, RobotInfo[] enemies) throws GameActionException {
        // spawn mopper ke arah musuh jika ada soldier di dekat sana
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
        if (trySpawnBest(rc, toSpawn)) return;
        // tower akan melakukan pengecekan ke semua unit yang bisa ditempati
        // meskipun tile ideal sudah terhalang
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

    // strategi spawn unit
    private static UnitType chooseUnitType(int round) {
        // EARLY: spawn 4 soldier + 1 mopper (untuk mencari ruins)
        if (round < 150) {
            return (spawnCount % 5 < 4) ? UnitType.SOLDIER : UnitType.MOPPER;
        } 
        // MID: spawn 2 soldier + 2 splasher + 1 mopper
        else if (round < 600) {
            int mod = spawnCount % 5;
            return switch (mod) {
                case 0, 1 -> UnitType.SOLDIER;
                case 2, 3 -> UnitType.SPLASHER;
                default   -> UnitType.MOPPER;
            };
            
        } 
        // LATE: spawn 2 splasher + 1 soldier + 1 mopper
        else {
            int mod = spawnCount % 4;
            return switch (mod) {
                case 0, 1, 2 -> UnitType.SPLASHER;
                default   -> UnitType.MOPPER;
            };
        }
    }

    // algoritma untuk memastikan unit baru berada di tempat yang terbaik
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
            // jika ubin bukan milik kita atau kosong
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

    // mengirimkan informasi ke tower 
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
