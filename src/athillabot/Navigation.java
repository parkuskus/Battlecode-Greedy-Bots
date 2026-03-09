package athillabot;

import battlecode.common.*;

import java.util.Random;

/**
 * Utilitas navigasi greedy untuk semua robot.
 * 
 * Strategi:
 * 1. moveToward() — greedy: pilih arah yang paling dekat ke target + bisa dilewati
 *    Kalau arah terbaik blocked, coba arah sebelahnya (bug-nav ringan)
 * 2. moveRandom() — fallback kalau gak ada tujuan
 * 3. Helpers untuk cari tower/tile terdekat
 */
public class Navigation {

    static final Direction[] directions = RobotPlayer.directions;
    static final Random rng = RobotPlayer.rng;

    /**
     * Greedy move menuju target.
     * Pilih arah yang minimize jarak ke target. Kalau blocked, coba rotasi kiri/kanan.
     * Return true kalau berhasil bergerak.
     */
    public static boolean moveToward(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null) return false;
        if (!rc.isMovementReady()) return false;

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = myLoc.directionTo(target);

        if (bestDir == Direction.CENTER) return false;

        // Coba arah utama dulu, lalu rotasi kiri/kanan secara bergantian
        // Ini = bug-nav ringan: kalau ada tembok di depan, coba geser sedikit
        Direction[] tryDirs = {
            bestDir,
            bestDir.rotateLeft(),
            bestDir.rotateRight(),
            bestDir.rotateLeft().rotateLeft(),
            bestDir.rotateRight().rotateRight(),
        };

        for (Direction dir : tryDirs) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        }
        return false;
    }

    /**
     * Gerak random. Fallback kalau gak ada objektif.
     * Return true kalau berhasil bergerak.
     */
    public static boolean moveRandom(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return false;

        // Shuffle: coba dari arah random, biar gak bias ke NORTH terus
        int startIdx = rng.nextInt(8);
        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(startIdx + i) % 8];
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        }
        return false;
    }

    /**
     * Gerak random tapi prefer petak yang belum di-cat sekutu (greedy explore).
     * Bagus untuk Soldier/Splasher yang mau ekspansi teritori.
     */
    public static boolean moveTowardUnpainted(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return false;

        Direction bestDir = null;
        int bestScore = -1;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;

            MapLocation newLoc = rc.getLocation().add(dir);
            MapInfo info = rc.senseMapInfo(newLoc);
            int score = 0;

            // Prefer petak yang belum di-cat (netral)
            if (info.getPaint() == PaintType.EMPTY) score += 3;
            // Petak musuh juga worth it (kita timpa)
            else if (info.getPaint().isEnemy()) score += 2;
            // Petak sendiri = kurang menarik
            else score += 0;

            // Tie-break: random kecil biar gak selalu pilih arah yang sama
            score = score * 10 + rng.nextInt(10);

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            return true;
        }
        return false;
    }

    /**
     * Cari lokasi Paint Tower sekutu terdekat dari posisi robot.
     * Return null kalau gak ada yang terlihat.
     */
    public static MapLocation findNearestAllyPaintTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            UnitType type = ally.getType();
            if (type == UnitType.LEVEL_ONE_PAINT_TOWER || type == UnitType.LEVEL_TWO_PAINT_TOWER || type == UnitType.LEVEL_THREE_PAINT_TOWER) {
                int dist = myLoc.distanceSquaredTo(ally.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = ally.getLocation();
                }
            }
        }
        return nearest;
    }

    /**
     * Cari lokasi tower sekutu terdekat (tipe apapun) untuk refill paint.
     * Robot bisa tarik paint dari tower manapun pakai transferPaint negatif.
     */
    public static MapLocation findNearestAllyTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                int dist = myLoc.distanceSquaredTo(ally.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = ally.getLocation();
                }
            }
        }
        return nearest;
    }

    /**
     * Cari reruntuhan (ruin) terdekat dari posisi robot.
     * Return null kalau gak ada ruin yang terlihat.
     */
    public static MapLocation findNearestRuin(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                // Pastikan belum ada tower di situ
                RobotInfo robotAtRuin = rc.senseRobotAtLocation(tile.getMapLocation());
                if (robotAtRuin == null) {
                    int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = tile.getMapLocation();
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Cek apakah robot sedang berdiri di atas cat sekutu.
     */
    public static boolean isOnAllyPaint(RobotController rc) throws GameActionException {
        return rc.senseMapInfo(rc.getLocation()).getPaint().isAlly();
    }
}
