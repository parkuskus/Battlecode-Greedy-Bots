package athillabot;

import battlecode.common.*;

/**
 * Logic untuk Mopper — support unit.
 * 
 * Mopper punya 3 kemampuan unik:
 * 1. Mop petak (hapus cat musuh di tanah, curi 5 cat dari robot musuh di petak itu)
 * 2. Mop swing (hapus 5 cat dari robot musuh di garis lurus, 4 arah)
 * 3. Transfer paint ke/dari robot sekutu
 * 
 * PERHATIAN: Mopper kena penalti DOUBLE saat di wilayah musuh!
 * Jadi Mopper harus hati-hati, jangan terlalu deep ke teritori musuh.
 * 
 * Greedy Priority:
 * 1. Refill kalau paint rendah
 * 2. Mop swing kalau ada robot musuh nearby
 * 3. Transfer paint ke sekutu yang butuh
 * 4. Mop cat musuh di tanah
 * 5. Patrol: gerak ke frontier tapi stay di ally territory
 */
public class MopperBot {

    // Threshold refill (20% dari kapasitas 100)
    private static final int PAINT_REFILL_THRESHOLD = 20;
    private static MapLocation refillTarget = null;

    public static void run(RobotController rc) throws GameActionException {
        int myPaint = rc.getPaint();

        // ===== 1. REFILL: Balik ke tower kalau paint rendah =====
        if (myPaint < PAINT_REFILL_THRESHOLD) {
            if (handleRefill(rc)) return;
        } else {
            refillTarget = null;
        }

        // ===== 2. MOP SWING: Kalau ada robot musuh dekat → swing! =====
        if (tryMopSwing(rc)) {
            // Berhasil swing, coba gerak mundur dari musuh setelahnya
            retreatFromEnemy(rc);
            return;
        }

        // ===== 3. TRANSFER PAINT: Bantu teman yang butuh cat =====
        if (myPaint > 30) {
            tryTransferPaintToAlly(rc);
        }

        // ===== 4. MOP ENEMY PAINT: Hapus cat musuh di tanah =====
        tryMopEnemyPaint(rc);

        // ===== 5. MOVE: Patrol — gerak menuju frontier tapi prefer ally territory =====
        if (!moveSmartPatrol(rc)) {
            Navigation.moveRandom(rc);
        }

        // Coba mop lagi setelah gerak
        if (tryMopSwing(rc)) return;
        tryMopEnemyPaint(rc);
    }

    /**
     * Handle refill paint dari tower.
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
     * Mop Swing: serang robot musuh di garis lurus (4 arah utama).
     * 
     * Greedy: pilih arah yang mengenai robot musuh paling banyak.
     * Swing mengenai 3 petak di langkah 1 + 3 petak di langkah 2 = maks 6 target.
     */
    private static boolean tryMopSwing(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return false;

        Direction bestDir = null;
        int bestEnemies = 0;

        // Cek 4 arah utama (swing hanya bisa ke N/S/E/W)
        Direction[] swingDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : swingDirs) {
            if (!rc.canMopSwing(dir)) continue;

            // Hitung berapa musuh yang akan kena
            int enemies = countSwingTargets(rc, dir);
            if (enemies > bestEnemies) {
                bestEnemies = enemies;
                bestDir = dir;
            }
        }

        if (bestDir != null && bestEnemies > 0) {
            rc.mopSwing(bestDir);
            return true;
        }
        return false;
    }

    /**
     * Hitung jumlah robot musuh yang akan kena swing di arah tertentu.
     */
    private static int countSwingTargets(RobotController rc, Direction dir) throws GameActionException {
        int count = 0;
        MapLocation myLoc = rc.getLocation();
        Team opponent = rc.getTeam().opponent();

        // Swing step 1: 1 langkah ke arah dir, lalu 3 petak (center + kiri + kanan)
        MapLocation step1Center = myLoc.add(dir);
        Direction left = dir.rotateLeft().rotateLeft(); // 90 derajat kiri
        Direction right = dir.rotateRight().rotateRight(); // 90 derajat kanan
        MapLocation[] step1 = {step1Center, step1Center.add(left), step1Center.add(right)};

        // Swing step 2: 2 langkah ke arah dir
        MapLocation step2Center = step1Center.add(dir);
        MapLocation[] step2 = {step2Center, step2Center.add(left), step2Center.add(right)};

        for (MapLocation loc : step1) {
            if (rc.canSenseLocation(loc)) {
                RobotInfo robot = rc.senseRobotAtLocation(loc);
                if (robot != null && robot.getTeam() == opponent) count++;
            }
        }
        for (MapLocation loc : step2) {
            if (rc.canSenseLocation(loc)) {
                RobotInfo robot = rc.senseRobotAtLocation(loc);
                if (robot != null && robot.getTeam() == opponent) count++;
            }
        }

        return count;
    }

    /**
     * Transfer paint ke robot sekutu terdekat yang butuh (paint < 50% kapasitas).
     * Greedy: transfer ke yang paling butuh (% paint terendah).
     */
    private static void tryTransferPaintToAlly(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;

        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam()); // radius √2
        RobotInfo neediest = null;
        double lowestPaintRatio = 0.5; // Hanya bantu yang < 50%

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) continue; // Jangan transfer ke tower
            double ratio = (double) ally.getPaintAmount() / ally.getType().paintCapacity;
            if (ratio < lowestPaintRatio) {
                lowestPaintRatio = ratio;
                neediest = ally;
            }
        }

        if (neediest != null) {
            // Transfer: berikan setengah paint kita atau secukupnya
            int toGive = Math.min(rc.getPaint() / 2, neediest.getType().paintCapacity - neediest.getPaintAmount());
            if (toGive > 0 && rc.canTransferPaint(neediest.getLocation(), toGive)) {
                rc.transferPaint(neediest.getLocation(), toGive);
            }
        }
    }

    /**
     * Mop cat musuh di tanah (dalam radius √2).
     * Greedy: pilih petak musuh terdekat.
     */
    private static void tryMopEnemyPaint(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;

        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(2); // radius √2

        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy()) {
                if (rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation());
                    return;
                }
            }
        }
    }

    /**
     * Mundur dari musuh setelah mop swing (safety).
     */
    private static void retreatFromEnemy(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return;

        // Mundur: arah kebalikan dari musuh terdekat
        MapLocation nearestEnemy = enemies[0].getLocation();
        Direction retreatDir = rc.getLocation().directionTo(nearestEnemy).opposite();
        if (rc.canMove(retreatDir)) {
            rc.move(retreatDir);
        }
    }

    /**
     * Patrol movement: gerak explore tapi prefer stay di ally territory.
     * Mopper kena double penalty di enemy territory, jadi harus hati-hati.
     */
    private static boolean moveSmartPatrol(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return false;

        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (Direction dir : RobotPlayer.directions) {
            if (!rc.canMove(dir)) continue;

            MapLocation newLoc = myLoc.add(dir);
            MapInfo info = rc.senseMapInfo(newLoc);
            int score = 0;

            PaintType paint = info.getPaint();
            // Bonus besar kalau ada cat musuh di sana (bisa kita mop)
            if (paint.isEnemy()) score += 5;
            // Prefer petak ally (aman, gak kena penalti)
            else if (paint.isAlly()) score += 2;
            // Petak kosong = ok
            else score += 1;

            // Bonus kalau ada robot musuh nearby posisi baru (bisa swing)
            // Cek jumlah musuh yang bisa di-sense dari posisi baru
            // (Ini estimasi kasar karena kita belum di sana)

            // Random tie-break
            score = score * 10 + RobotPlayer.rng.nextInt(10);

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
}
