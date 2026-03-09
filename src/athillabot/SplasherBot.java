package athillabot;

import battlecode.common.*;

/**
 * Logic untuk Splasher — mass area painter.
 * 
 * Splasher adalah unit PALING EFISIEN untuk mewarnai petak:
 * - 1 serangan = ~13 petak terwarnai (radius 2 dari center)
 * - Bisa overwrite cat musuh di radius √2 dari center
 * - Biaya: 50 cat per serangan (vs Soldier 5 cat per 1 petak)
 * 
 * Greedy Priority:
 * 1. Refill kalau paint rendah
 * 2. Cari posisi serangan yang memaksimalkan petak baru ter-cat
 * 3. Gerak menuju frontier (area belum di-cat)
 */
public class SplasherBot {

    // Threshold: refill kalau paint < 20% dari kapasitas (300)
    private static final int PAINT_REFILL_THRESHOLD = 60;
    // Minimum paint untuk bisa menyerang (biaya attack = 50)
    private static final int PAINT_ATTACK_MIN = 50;
    private static MapLocation refillTarget = null;

    public static void run(RobotController rc) throws GameActionException {
        int myPaint = rc.getPaint();

        // ===== 1. REFILL: Balik ke tower kalau paint rendah =====
        if (myPaint < PAINT_REFILL_THRESHOLD) {
            if (handleRefill(rc)) return;
        } else {
            refillTarget = null;
        }

        // ===== 2. ATTACK: Cari posisi serangan terbaik (greedy) =====
        if (myPaint >= PAINT_ATTACK_MIN) {
            tryBestAttack(rc);
        }

        // ===== 3. MOVE: Gerak menuju area yang belum diwarnai =====
        if (!Navigation.moveTowardUnpainted(rc)) {
            Navigation.moveRandom(rc);
        }

        // Coba serang lagi setelah gerak (kalau cooldown sudah reset)
        if (myPaint >= PAINT_ATTACK_MIN) {
            tryBestAttack(rc);
        }
    }

    /**
     * Handle refill paint — sama seperti Soldier, jalan ke tower terdekat.
     */
    private static boolean handleRefill(RobotController rc) throws GameActionException {
        if (refillTarget == null) {
            refillTarget = Navigation.findNearestAllyTower(rc);
        }

        if (refillTarget == null) {
            Navigation.moveRandom(rc);
            return true;
        }

        if (rc.getLocation().distanceSquaredTo(refillTarget) <= 2) {
            if (rc.canTransferPaint(refillTarget, -rc.getType().paintCapacity)) {
                int amountNeeded = rc.getType().paintCapacity - rc.getPaint();
                rc.transferPaint(refillTarget, -amountNeeded);
                refillTarget = null;
                return false;
            }
        }

        Navigation.moveToward(rc, refillTarget);
        return true;
    }

    /**
     * Greedy attack: evaluasi setiap posisi yang bisa diserang,
     * pilih center yang MEMAKSIMALKAN jumlah petak baru yang ter-cat.
     * 
     * Splasher attack radius: center harus ≤ 2 petak dari posisi Splasher.
     * Effect radius: semua petak dalam radius 2 dari center diwarnai.
     * Overwrite musuh: hanya dalam radius √2 dari center.
     * 
     * Scoring: +3 per petak kosong yang bakal di-cat
     *          +5 per petak musuh yang bakal di-overwrite (dalam √2)
     *          +0 per petak yang sudah sekutu (tidak berguna)
     *          +1 per damage ke tower musuh di area
     */
    private static void tryBestAttack(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;

        MapLocation myLoc = rc.getLocation();
        MapLocation bestCenter = null;
        int bestScore = 0;

        // Evaluasi posisi kita sendiri + 8 arah + posisi 2 langkah
        // Titik pusat serangan bisa di jarak ≤ 2 dari posisi splasher
        MapLocation[] candidates = rc.getAllLocationsWithinRadiusSquared(myLoc, 4);

        for (MapLocation center : candidates) {
            if (!rc.canAttack(center)) continue;

            int score = evaluateAttackCenter(rc, center);
            if (score > bestScore) {
                bestScore = score;
                bestCenter = center;
            }
        }

        // Hanya serang kalau ada petak yang worth it (minimal 2 petak baru)
        if (bestCenter != null && bestScore >= 6) {
            rc.attack(bestCenter);
        }
    }

    /**
     * Evaluasi skor serangan di center tertentu.
     * Semakin tinggi skor = semakin banyak petak baru yang ter-cat.
     */
    private static int evaluateAttackCenter(RobotController rc, MapLocation center) throws GameActionException {
        int score = 0;

        // Semua petak dalam radius 2 dari center (radius squared = 4)
        MapLocation[] affectedTiles = rc.getAllLocationsWithinRadiusSquared(center, 4);

        for (MapLocation tile : affectedTiles) {
            if (!rc.canSenseLocation(tile)) continue;

            MapInfo info = rc.senseMapInfo(tile);
            // Skip tembok dan reruntuhan
            if (info.isWall() || info.hasRuin()) continue;

            PaintType paint = info.getPaint();
            int distSqToCenter = center.distanceSquaredTo(tile);

            if (paint == PaintType.EMPTY) {
                // Petak kosong → akan diwarnai
                score += 3;
            } else if (paint.isEnemy()) {
                // Petak musuh → bisa di-overwrite hanya kalau dalam radius √2 dari center
                if (distSqToCenter <= 2) {
                    score += 5; // Overwrite musuh sangat berharga
                }
            }
            // Petak ally → +0 (gak ada gunanya cat ulang)

            // Bonus: tower musuh di area kena damage
            RobotInfo robotAtTile = rc.senseRobotAtLocation(tile);
            if (robotAtTile != null && robotAtTile.getTeam() != rc.getTeam() && robotAtTile.getType().isTowerType()) {
                score += 2;
            }
        }

        return score;
    }
}
