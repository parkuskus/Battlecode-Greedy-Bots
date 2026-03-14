package alternative_bots_1;

import battlecode.common.*;

/**
 * Strategi Navigasi:
 * 1. GREEDY STEP: Selalu pilih langkah terbaik menuju target
 * 2. BUG NAV: Jika terhalang, ikuti dinding sampai bisa lanjut
 * 3. ANTI-OSCILLATION: Hindari bolak-balik di tempat yang sama
 * 
 * Algoritma Greedy dalam Navigasi:
 * - Setiap langkah, hitung skor semua arah yang mungkin
 * - Skor = jarak ke target + bonus tile friendly + penalty oscillation
 * - Pilih arah dengan skor tertinggi
 */
public class Nav {
    // simpan riwayat posisi untuk deteksi oscillation
    private static final int HISTORY_SIZE = 10;
    private static MapLocation[] history = new MapLocation[HISTORY_SIZE];
    private static int historyIndex = 0;
    private static Direction lastDirection = Direction.CENTER;    
    // untuk navigasi mengikuti dinding saat terhalang
    private static int lastBugDistance = Integer.MAX_VALUE;
    private static int turnsStuckBug = 0;
    private static Direction[] bugStack = new Direction[80];
    private static int bugIndex = 0;
    private static MapLocation bugTarget = null;
    private static boolean bugTurnRight = true;
    
    // catat posisi untuk deteksi oscillation
    static void recordPosition(MapLocation loc) {
        history[historyIndex % HISTORY_SIZE] = loc;
        historyIndex++;
    }
    
    // cek apakah pernah di lokasi baru-baru ini
    private static boolean wasRecentlyHere(MapLocation loc) {
        for (int i = 0; i < HISTORY_SIZE; i++) {
            if (history[i] != null && history[i].equals(loc)) return true;
        }
        return false;
    }
    
    // bergerak ke target dengan greedy sambil cegah osilasi
    static boolean fuzzyMove(MapLocation target) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return false;
        return greedyStep(target, null, false, -1);
    }
    
    // bergerak ke arah tertentu
    static boolean fuzzyMove(Direction dir) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady() || dir == Direction.CENTER) return false;
        MapLocation pseudoTarget = rc.getLocation().add(dir).add(dir);
        return greedyStep(pseudoTarget, null, false, -1);
    }
    
    // bergerak menghindari tower musuh
    static boolean safeFuzzyMove(MapLocation target, RobotInfo[] enemies) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return false;
        return greedyStep(target, enemies, true, -1);
    }
    
    // aggresive move
    static void aggressiveMove(MapLocation target) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady() || target == null) return;
        MapLocation position = rc.getLocation();
        Direction dir = position.directionTo(target);
        // coba arah langsung, lalu fuzzy
        Direction[] order = fuzzyOrder(dir);
        for (Direction d : order) {
            if (rc.canMove(d)) {
                rc.move(d);
                lastDirection = d;
                return;
            }
        }
        // Jika terhalang total, gunakan bug nav
        bugNav(target);
    }
    
    // ikuti dinding hingga bisa maju lagi
    static void bugNav(MapLocation target) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return;
        // reset bug state jika target berubah atau stuck terlalu lama
        if (bugTarget == null || bugTarget.distanceSquaredTo(target) > 8 || bugIndex >= 70) {
            bugStack = new Direction[80];
            bugIndex = 0;
            bugTarget = target;
            lastBugDistance = rc.getLocation().distanceSquaredTo(target);
            turnsStuckBug = 0;
        }
        bugTarget = target;
        // deteksi stuck
        int currentDistance = rc.getLocation().distanceSquaredTo(target);
        if (currentDistance >= lastBugDistance - 1) {
            turnsStuckBug++;
        } else {
            turnsStuckBug = 0;
        }
        lastBugDistance = currentDistance;
        // ganti arah putar jika stuck terlalu lama
        if (turnsStuckBug >= 7) {
            bugTurnRight = !bugTurnRight;
            bugStack = new Direction[80];
            bugIndex = 0;
            turnsStuckBug = 0;
        }
        // pop stack jika bisa bergerak ke arah yang di-stack
        while (bugIndex > 0 && rc.canMove(bugStack[bugIndex - 1])) {
            bugIndex--;
        }
        
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        // jika stack kosong, coba greedy step
        if (bugIndex == 0) {
            if (greedyStep(target, nearbyEnemies, true, -1)) {
                return;
            }
            Direction dir = rc.getLocation().directionTo(target);
            bugStack[bugIndex++] = bugTurnRight ? dir.rotateLeft() : dir.rotateRight();
        }
        // ikuti dinding
        Direction dir = bugTurnRight
            ? bugStack[bugIndex - 1].rotateRight()
            : bugStack[bugIndex - 1].rotateLeft();
            
        for (int i = 0; i < 8; i++) {
            MapLocation dest = rc.getLocation().add(dir);
            
            if (rc.canMove(dir) && !inEnemyTowerRange(dest, nearbyEnemies)) {
                rc.move(dir);
                lastDirection = dir;
                return;
            }
            
            // keluar dari map, balik arah
            if (!rc.onTheMap(dest)) {
                bugStack = new Direction[80];
                bugIndex = 0;
                bugTurnRight = !bugTurnRight;
                return;
            }
            
            bugStack[bugIndex++] = dir;
            dir = bugTurnRight ? dir.rotateRight() : dir.rotateLeft();
        }
    }
    
    static boolean moveIntoRange(MapLocation target, int rangeSq) throws GameActionException {
        if (greedyStep(target, null, false, rangeSq)) return true;
        return greedyStep(target, null, false, -1);
    }
    
    // helper functions
    private static boolean greedyStep(MapLocation target, RobotInfo[] enemies,
            boolean avoidEnemyTowers, int rangeSq) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady() || target == null) return false;
        
        MapLocation position = rc.getLocation();
        int currentDistance = position.distanceSquaredTo(target);
        Direction[] order = fuzzyOrder(position.directionTo(target));
        
        Direction best = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (Direction d : order) {
            if (!rc.canMove(d)) continue;
            MapLocation dest = position.add(d);
            
            // skip jika dalam range tower musuh (mode safe)
            if (avoidEnemyTowers && enemies != null && inEnemyTowerRange(dest, enemies)) {
                continue;
            }
            
            // skip jika di luar range yang diminta
            if (rangeSq >= 0 && dest.distanceSquaredTo(target) > rangeSq) {
                continue;
            }
            
            // hitung skor tile
            int score = scoreTile(dest);
            int newDistance = dest.distanceSquaredTo(target);
            
            score += (currentDistance - newDistance) * 8;
            
            // anti osilasi dengan penalti
            if (wasRecentlyHere(dest)) score -= 26;
            if (twoStepOscillation(dest)) score -= 42;
            
            // bonus konsistensi arah
            if (d == lastDirection) score += 4;
            if (d == lastDirection.opposite()) score -= 10;
            
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        
        if (best != null) {
            rc.move(best);
            lastDirection = best;
            return true;
        }
        return false;
    }
    
    // hitung skor tile berdasarkan warna cat
    static int scoreTile(MapLocation loc) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.onTheMap(loc)) return -9999;
        
        MapInfo info = rc.senseMapInfo(loc);
        if (info.isWall()) return -9999;
        
        int score = 0;
        PaintType paint = info.getPaint();
        // bonus berdasarkan warna cat
        if (paint.isAlly()) score += 3;
        else if (paint == PaintType.EMPTY) score += 2;
        else if (paint.isEnemy()) score -= 6;
        // bias ke tengah map (kontrol area)
        score += territorialBias(loc);
        // penalty clustering dengan sekutu
        if (Clock.getBytecodesLeft() > 3000) {
            int nearbyAllies = rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam).length;
            score -= nearbyAllies * 2;
        }
        
        return score;
    }
    
    // bias teritorial
    private static int territorialBias(MapLocation loc) {
        MapLocation center = new MapLocation(RobotPlayer.mapW / 2, RobotPlayer.mapH / 2);
        // bonus dekat tengah map
        int centerBias = 6 - loc.distanceSquaredTo(center) / 14;
        // bonus jika maju ke depan
        int forwardBias = 0;
        MapLocation position = RobotPlayer.rc.getLocation();
        if (position != null) {
            int currentDistToCenter = position.distanceSquaredTo(center);
            int newDistToCenter = loc.distanceSquaredTo(center);
            if (newDistToCenter < currentDistToCenter) forwardBias += 1;
        }
        
        return centerBias + forwardBias;
    }
    
   // deteksi osilasi
    private static boolean twoStepOscillation(MapLocation dest) {
        if (historyIndex < 2) return false;
        MapLocation twoStepsBack = history[(historyIndex - 2 + HISTORY_SIZE) % HISTORY_SIZE];
        return twoStepsBack != null && twoStepsBack.equals(dest);
    }
    
    // detelksi osilasi umum
    static boolean isOscillating() {
        if (historyIndex < 4) return false;
        MapLocation a = history[(historyIndex - 1 + HISTORY_SIZE) % HISTORY_SIZE];
        MapLocation b = history[(historyIndex - 2 + HISTORY_SIZE) % HISTORY_SIZE];
        MapLocation c = history[(historyIndex - 3 + HISTORY_SIZE) % HISTORY_SIZE];
        MapLocation d = history[(historyIndex - 4 + HISTORY_SIZE) % HISTORY_SIZE];
        return a != null && b != null && c != null && d != null
                && a.equals(c) && b.equals(d);
    }
    
    static boolean inEnemyTowerRange(MapLocation loc, RobotInfo[] enemies) {
        for (RobotInfo m : enemies) {
            if (m.type.isTowerType()
                && loc.distanceSquaredTo(m.location) <= m.type.actionRadiusSquared) {
                return true;
            }
        }
        return false;
    }
    
    static RobotInfo nearestEnemyTower(RobotInfo[] enemies) {
        RobotController rc = RobotPlayer.rc;
        RobotInfo best = null;
        int bestDistance = Integer.MAX_VALUE;
        
        for (RobotInfo m : enemies) {
            if (m.type.isTowerType()) {
                int distance = rc.getLocation().distanceSquaredTo(m.location);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = m;
                }
            }
        }
        return best;
    }
    
    static Direction[] fuzzyOrder(Direction dir) {
        return new Direction[]{
            dir,
            dir.rotateLeft(), dir.rotateRight(),
            dir.rotateLeft().rotateLeft(), dir.rotateRight().rotateRight(),
            dir.rotateLeft().rotateLeft().rotateLeft(),
            dir.rotateRight().rotateRight().rotateRight()
        };
    }
}