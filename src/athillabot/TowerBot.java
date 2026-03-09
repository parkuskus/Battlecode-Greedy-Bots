package athillabot;

import battlecode.common.*;

/**
 * Logic untuk semua tipe Tower (Paint, Money, Defense — level 1-3).
 * 
 * Tower punya 2 tugas utama:
 * 1. Spawn robot — keputusan GREEDY berdasarkan fase game
 * 2. Serang musuh — setiap giliran cek musuh di radius → serang terdekat
 * 
 * Spawn Priority (Greedy berdasarkan ronde):
 * - Early   (< 200):  Soldier dominan (perlu bangun tower baru ASAP)
 * - Mid   (200-800):  Splasher dominan + Soldier (ekspansi teritori)
 * - Late    (> 800):  Splasher + Mopper (maintain + aggro)
 */
public class TowerBot {

    public static void run(RobotController rc) throws GameActionException {
        // ===== 1. ATTACK: Serang musuh terdekat =====
        attackNearestEnemy(rc);

        // ===== 2. SPAWN: Buat robot baru =====
        spawnRobot(rc);

        // ===== 3. MESSAGES: Baca pesan masuk =====
        readMessages(rc);
    }

    /**
     * Serang robot/tower musuh terdekat yang ada di radius serangan.
     */
    private static void attackNearestEnemy(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return;

        // Greedy: serang musuh terdekat
        MapLocation myLoc = rc.getLocation();
        RobotInfo nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            int dist = myLoc.distanceSquaredTo(enemy.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = enemy;
            }
        }

        if (nearest != null && rc.canAttack(nearest.getLocation())) {
            rc.attack(nearest.getLocation());
        }

        // Juga coba AoE attack kalau ada banyak musuh
        if (enemies.length >= 2 && rc.canAttack(null)) {
            rc.attack(null); // AoE attack (null = semua musuh dalam range)
        }
    }

    /**
     * Spawn robot berdasarkan fase game (keputusan greedy).
     * 
     * Greedy rationale:
     * - Early game: Soldier → bangun tower baru → economy snowball
     * - Mid game: Splasher → 1 serangan cat 13 petak → paling efisien untuk 70% target
     * - Late game: Mopper tambahan → hapus cat musuh + refill teman di front line
     */
    private static void spawnRobot(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();

        UnitType toSpawn;
        UnitType backup;

        if (round < 200) {
            // Early: Soldier dominan, sesekali Mopper
            if (round % 4 == 0) {
                toSpawn = UnitType.MOPPER;
                backup = UnitType.SOLDIER;
            } else {
                toSpawn = UnitType.SOLDIER;
                backup = UnitType.MOPPER;
            }
        } else if (round < 800) {
            // Mid: Splasher dominan, Soldier untuk tower building
            int cycle = round % 5;
            if (cycle < 3) {
                toSpawn = UnitType.SPLASHER;
                backup = UnitType.SOLDIER;
            } else if (cycle == 3) {
                toSpawn = UnitType.SOLDIER;
                backup = UnitType.SPLASHER;
            } else {
                toSpawn = UnitType.MOPPER;
                backup = UnitType.SPLASHER;
            }
        } else {
            // Late: Splasher + Mopper push
            int cycle = round % 4;
            if (cycle < 2) {
                toSpawn = UnitType.SPLASHER;
                backup = UnitType.MOPPER;
            } else {
                toSpawn = UnitType.MOPPER;
                backup = UnitType.SPLASHER;
            }
        }

        // Coba spawn di arah yang ada ally paint (robot baru gak kena penalti)
        if (!trySpawn(rc, toSpawn)) {
            trySpawn(rc, backup);
        }
    }

    /**
     * Coba spawn robot di arah terbaik.
     * Prioritas: petak dengan ally paint > petak kosong > petak manapun.
     */
    private static boolean trySpawn(RobotController rc, UnitType type) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Direction[] dirs = RobotPlayer.directions;

        // Pass 1: prefer petak dengan ally paint
        for (Direction dir : dirs) {
            MapLocation spawnLoc = myLoc.add(dir);
            if (rc.canBuildRobot(type, spawnLoc)) {
                if (rc.senseMapInfo(spawnLoc).getPaint().isAlly()) {
                    rc.buildRobot(type, spawnLoc);
                    return true;
                }
            }
        }

        // Pass 2: petak manapun yang bisa
        for (Direction dir : dirs) {
            MapLocation spawnLoc = myLoc.add(dir);
            if (rc.canBuildRobot(type, spawnLoc)) {
                rc.buildRobot(type, spawnLoc);
                return true;
            }
        }

        return false;
    }

    /**
     * Baca dan proses pesan masuk dari robot sekutu.
     */
    private static void readMessages(RobotController rc) throws GameActionException {
        Communication.processMessages(rc, (type, loc, extra, senderID, round) -> {
            // Untuk sekarang, log saja. Nanti bisa dipakai untuk koordinasi spawn.
            if (type == Communication.MSG_RUIN_FOUND) {
                rc.setIndicatorString("Ruin reported at " + loc);
            }
        });
    }
}
