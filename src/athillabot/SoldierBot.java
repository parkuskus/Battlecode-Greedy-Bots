package athillabot;

import battlecode.common.*;

/**
 * Logic untuk Soldier — robot serba bisa.
 * 
 * Soldier adalah builder + painter utama di early game.
 * HP tinggi (250), bisa serang petak untuk warnai + damage tower musuh.
 * 
 * Greedy Priority (urutan keputusan per giliran):
 * 1. Refill paint kalau hampir habis → pergi ke tower terdekat
 * 2. Upgrade tower sekutu yang dekat
 * 3. Bangun tower baru di reruntuhan → mark pattern → paint → complete
 * 4. Complete SRP (Special Resource Pattern) kalau bisa
 * 5. Eksplorasi + warnai petak baru
 */
public class SoldierBot {

    // Threshold paint untuk mulai refill (20% dari max 200)
    private static final int PAINT_REFILL_THRESHOLD = 40;
    // Lokasi target refill yang sedang dituju (persist antar giliran)
    private static MapLocation refillTarget = null;
    // Lokasi ruin yang sedang dikerjakan
    private static MapLocation currentRuin = null;

    public static void run(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int myPaint = rc.getPaint();

        // ===== 1. REFILL: Kalau paint hampir habis, balik ke tower =====
        if (myPaint < PAINT_REFILL_THRESHOLD) {
            if (handleRefill(rc)) return;
        } else {
            refillTarget = null; // Sudah cukup paint, reset target refill
        }
        
        // ===== 2. UPGRADE: Coba upgrade tower sekutu terdekat =====
        tryUpgradeNearbyTower(rc);

        // ===== 3. BUILD TOWER: Cari reruntuhan → bangun tower =====
        if (handleTowerBuilding(rc)) return;

        // ===== 4. COMPLETE SRP: Selesaikan resource pattern kalau bisa =====
        tryCompleteResourcePattern(rc);

        // ===== 5. PAINT + EXPLORE: Warnai petak di bawah kaki, lalu eksplorasi =====
        paintCurrentTile(rc);
        
        // Gerak ke area yang belum diwarnai (greedy explore)
        if (!Navigation.moveTowardUnpainted(rc)) {
            Navigation.moveRandom(rc);
        }
        
        // Coba warnai petak baru setelah gerak
        paintCurrentTile(rc);

        // ===== 6. COMMUNICATE: Laporkan temuan =====
        broadcastFindings(rc);
    }

    /**
     * Handle refill paint dari tower terdekat.
     * Return true kalau robot sedang dalam mode refill (skip aksi lain).
     */
    private static boolean handleRefill(RobotController rc) throws GameActionException {
        // Cari tower terdekat untuk refill
        if (refillTarget == null) {
            refillTarget = Navigation.findNearestAllyTower(rc);
        }

        if (refillTarget == null) {
            // Gak ada tower terlihat, gerak random sambil berharap ketemu
            Navigation.moveRandom(rc);
            return true;
        }

        // Kalau sudah di sebelah tower (radius √2 = jarak squared ≤ 2), tarik paint
        if (rc.getLocation().distanceSquaredTo(refillTarget) <= 2) {
            if (rc.canTransferPaint(refillTarget, -rc.getType().paintCapacity)) {
                // Tarik paint sebanyak-banyaknya (nilai negatif = tarik dari tower)
                int amountNeeded = rc.getType().paintCapacity - rc.getPaint();
                rc.transferPaint(refillTarget, -amountNeeded);
                refillTarget = null;
                return false; // Sudah refill, lanjut aksi normal
            }
        }

        // Belum sampai, gerak menuju tower
        Navigation.moveToward(rc, refillTarget);
        // Sambil jalan, cat petak di bawah kaki
        paintCurrentTile(rc);
        return true;
    }

    /**
     * Cari reruntuhan dan bangun tower di sana.
     * Flow: detect ruin → move to ruin → mark pattern → paint pattern tiles → complete
     * 
     * Tower type greedy: Money Tower > Paint Tower (sesuai preferensi user).
     * Return true kalau sedang mengerjakan tower (skip explore).
     */
    private static boolean handleTowerBuilding(RobotController rc) throws GameActionException {
        // Cari ruin terdekat
        MapLocation ruinLoc = Navigation.findNearestRuin(rc);

        if (ruinLoc == null) {
            currentRuin = null;
            return false;
        }
        currentRuin = ruinLoc;

        // Tentukan tipe tower: Money > Paint > Defense
        UnitType towerType = chooseTowerType(rc);

        // Gerak menuju ruin
        Direction dirToRuin = rc.getLocation().directionTo(ruinLoc);
        if (rc.canMove(dirToRuin)) {
            rc.move(dirToRuin);
        }

        // Mark tower pattern kalau belum di-mark (biaya 25 cat)
        MapLocation checkLoc = ruinLoc.subtract(rc.getLocation().directionTo(ruinLoc));
        if (rc.canSenseLocation(checkLoc)) {
            if (rc.senseMapInfo(checkLoc).getMark() == PaintType.EMPTY 
                    && rc.canMarkTowerPattern(towerType, ruinLoc)) {
                rc.markTowerPattern(towerType, ruinLoc);
            }
        }

        // Paint semua petak yang sudah di-mark tapi belum di-cat benar
        for (MapInfo patternTile : rc.senseNearbyMapInfos(ruinLoc, 8)) {
            if (patternTile.getMark() != PaintType.EMPTY 
                    && patternTile.getMark() != patternTile.getPaint()) {
                boolean useSecondary = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                if (rc.canAttack(patternTile.getMapLocation())) {
                    rc.attack(patternTile.getMapLocation(), useSecondary);
                }
            }
        }

        // Coba selesaikan tower
        if (rc.canCompleteTowerPattern(towerType, ruinLoc)) {
            rc.completeTowerPattern(towerType, ruinLoc);
        }

        return true;
    }

    /**
     * Pilih tipe tower yang mau dibangun (greedy).
     * Priority: Money Tower > Paint Tower > Defense Tower.
     * 
     * Rationale: Money Tower = lebih banyak chips = lebih banyak robot = snowball.
     * Paint Tower penting juga untuk sustain paint supply.
     */
    private static UnitType chooseTowerType(RobotController rc) throws GameActionException {
        // Hitung jumlah tower masing-masing tipe yang terlihat
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int moneyTowers = 0;
        int paintTowers = 0;

        for (RobotInfo ally : allies) {
            UnitType t = ally.getType();
            if (t == UnitType.LEVEL_ONE_MONEY_TOWER || t == UnitType.LEVEL_TWO_MONEY_TOWER || t == UnitType.LEVEL_THREE_MONEY_TOWER) {
                moneyTowers++;
            } else if (t == UnitType.LEVEL_ONE_PAINT_TOWER || t == UnitType.LEVEL_TWO_PAINT_TOWER || t == UnitType.LEVEL_THREE_PAINT_TOWER) {
                paintTowers++;
            }
        }

        // Greedy: bangun tipe yang paling sedikit, prefer Money
        if (moneyTowers <= paintTowers) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        } else {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
    }

    /**
     * Coba upgrade tower sekutu yang ada di sebelah (radius √2).
     */
    private static void tryUpgradeNearbyTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType() && rc.canUpgradeTower(ally.getLocation())) {
                rc.upgradeTower(ally.getLocation());
                return;
            }
        }
    }

    /**
     * Coba selesaikan Special Resource Pattern kalau ada yang bisa di-complete.
     */
    private static void tryCompleteResourcePattern(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) continue;
            MapLocation loc = tile.getMapLocation();
            if (rc.canCompleteResourcePattern(loc)) {
                rc.completeResourcePattern(loc);
                return;
            }
        }
    }

    /**
     * Cat petak di bawah kaki kalau belum di-cat sendiri.
     * Ini mencegah penalti paint saat akhir giliran di petak netral/musuh.
     */
    private static void paintCurrentTile(RobotController rc) throws GameActionException {
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }

    /**
     * Broadcast temuan penting ke sekutu terdekat.
     */
    private static void broadcastFindings(RobotController rc) throws GameActionException {
        // Laporkan ruin yang ditemukan
        if (currentRuin != null) {
            int msg = Communication.encode(Communication.MSG_RUIN_FOUND, currentRuin);
            Communication.broadcastToNearbyAllies(rc, msg);
        }
    }
}
