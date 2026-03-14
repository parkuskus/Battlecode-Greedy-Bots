package alternative_bots_1;

import battlecode.common.*;

// SPLASHERBOT - pembom cat agresif
// tugas: rush ke wilayah musuh, splash sebanyak mungkin, siege tower, expand ke area kosong
// prinsip: selalu splash ke tile dengan nilai tertinggi, jangan mundur kecuali cat habis
public class SplasherBot {
    
    private static final double LOW_PAINT_THRESHOLD = 0.10; // 10% - perlu isi ulang
    private static final double ENOUGH_PAINT_THRESHOLD = 0.50; // 50% - cukup untuk bertempur
    private static final int MAX_REFILL_DIST = 100; // jarak max ke tower
    private static final int ROUND_EXPAND = 400; // mulai ekspansi lebih awal
    private static final int MIN_SPLASH_VALUE = 1; // splash walaupun sedikit nilai
    private static final int TARGET_MONEY_TOWERS = 3;
    private static final int ECONOMY_PHASE_ROUND_LIMIT = 260;
    
    private static MapLocation spawnTower;
    private static final MapLocation[] enemyTargets = new MapLocation[3];
    private static int targetIndex;
    private static int roundLastSwitch = -9999;
    
    private enum Status { RUSH, REFILL, SIEGE, EXPAND }
    private static Status status = Status.RUSH;
    
    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation position = rc.getLocation();
        
        if (spawnTower == null) {
            initialize(rc, position);
        }
        
        Messaging.readMessages();
        
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        
        SymmetryTracker.observe(nearby);
        refreshEnemyTargets();
        
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType()) {
                Messaging.registerTower(ally.location, ally.type);
            }
        }
        
        // catat dan broadcast tower musuh yang terlihat
        RobotInfo enemyTower = findEnemyTower(position, enemies);
        if (enemyTower != null) {
            Messaging.registerEnemyTower(enemyTower.location, rc.getRoundNum());
            for (RobotInfo ally : allies) {
                if (ally.type.isTowerType() && rc.canSendMessage(ally.location)) {
                    rc.sendMessage(ally.location, Messaging.encodeEnemyTower(enemyTower.location));
                    break;
                }
            }
        }
        Messaging.relayToNearbyTower(allies);
        
        status = decideStatus(rc, position, allies, enemies);
        
        switch (status) {
            case REFILL -> executeRefill(rc, position, allies);
            case SIEGE -> executeSiege(rc, position, enemies);
            case EXPAND -> executeExpand(rc, position, nearby, enemies);
            default -> executeRush(rc, position, enemies);
        }
        
        // selalu coba splash setelah gerak
        if (rc.isActionReady()) {
            MapLocation focus = Messaging.enemyTowerHint();
            MapLocation splashTarget = findBestSplashTarget(rc, rc.getLocation(), enemies, focus);
            if (splashTarget != null && rc.canAttack(splashTarget)) {
                rc.attack(splashTarget);
            }
        }
        
        // cat tile sendiri kalau masih kosong atau milik musuh
        if (rc.isActionReady()) {
            MapInfo info = rc.senseMapInfo(rc.getLocation());
            PaintType paint = info.getPaint();
            if ((paint == PaintType.EMPTY || paint.isEnemy()) && rc.canAttack(position)) {
                rc.attack(position);
            }
        }
        
        completeTowerPatterns(rc);
        
        Nav.recordPosition(rc.getLocation());
    }
    
    // pilih status terbaik sekarang
    private static Status decideStatus(RobotController rc, MapLocation position,
            RobotInfo[] allies, RobotInfo[] enemies) {
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;
        
        // kalau cat rendah, isi ulang dulu
        if ((status != Status.REFILL && paintRatio < LOW_PAINT_THRESHOLD)
         || (status == Status.REFILL && paintRatio < ENOUGH_PAINT_THRESHOLD)) {
            MapLocation tower = findRefillTower(position, allies);
            if (tower != null && position.distanceSquaredTo(tower) <= MAX_REFILL_DIST) {
                return Status.REFILL;
            }
        }

        if (!isEconomyPhaseDone(rc)) {
            return Status.RUSH;
        }
        
        // kalau ada tower musuh, siege
        if (findEnemyTower(position, enemies) != null || Messaging.enemyTowerHint() != null) {
            return Status.SIEGE;
        }
        
        // late game - mulai expand
        if (rc.getRoundNum() >= ROUND_EXPAND && Messaging.towerCount >= 3) {
            return Status.EXPAND;
        }
        
        return Status.RUSH;
    }

    private static boolean isEconomyPhaseDone(RobotController rc) {
        if (Messaging.countMoneyTowers() < TARGET_MONEY_TOWERS) return false;
        return rc.getRoundNum() >= ECONOMY_PHASE_ROUND_LIMIT;
    }
    
    // isi ulang cat di tower terdekat
    private static void executeRefill(RobotController rc, MapLocation position,
            RobotInfo[] allies) throws GameActionException {
        MapLocation target = findRefillTower(position, allies);
        if (target == null) target = spawnTower != null ? spawnTower : position;
        
        if (position.distanceSquaredTo(target) <= 2) {
            int needed = rc.getType().paintCapacity - rc.getPaint();
            if (needed > 0 && rc.canTransferPaint(target, -needed)) {
                rc.transferPaint(target, -needed);
            }
        } else {
            Nav.bugNav(target);
        }
    }
    
    // maju agresif ke tower musuh buat displash
    private static void executeSiege(RobotController rc, MapLocation position,
            RobotInfo[] enemies) throws GameActionException {
        RobotInfo tower = findEnemyTower(position, enemies);
        MapLocation target = tower != null ? tower.location : Messaging.enemyTowerHint();
        
        if (target == null) {
            status = Status.RUSH;
            return;
        }
        
        if (position.distanceSquaredTo(target) > rc.getType().actionRadiusSquared && rc.isMovementReady()) {
            Nav.aggressiveMove(target);
        }
    }
    
    // rush ke base musuh, tapi kalau ada kluster tile kosong/musuh di dekat, mampir dulu buat di-splash
    private static void executeRush(RobotController rc, MapLocation position,
            RobotInfo[] enemies) throws GameActionException {
        
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation cluster = findEnemyPaintCluster(rc, position, nearby);
        
        // kalau ada kluster dalam jangkauan, kesana dulu
        if (cluster != null && position.distanceSquaredTo(cluster) <= 36) {
            if (rc.isMovementReady()) {
                Nav.aggressiveMove(cluster);
            }
            return;
        }
        
        MapLocation target = enemyTargets[targetIndex];
        if (target == null) return;
        
        boolean arrived = position.distanceSquaredTo(target) <= 16;
        boolean stuckTooLong = rc.getRoundNum() - roundLastSwitch > 80;
        if (arrived || stuckTooLong) {
            targetIndex = (targetIndex + 1) % 3;
            roundLastSwitch = rc.getRoundNum();
            target = enemyTargets[targetIndex];
        }
        
        if (rc.isMovementReady() && target != null) {
            Nav.aggressiveMove(target);
        }
    }
    
    // perluas wilayah ke area kosong
    private static void executeExpand(RobotController rc, MapLocation position,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        MapLocation cluster = findEnemyPaintCluster(rc, position, nearby);
        if (cluster != null) {
            Nav.safeFuzzyMove(cluster, enemies);
            return;
        }
        
        // fallback ke rush
        executeRush(rc, position, enemies);
    }
    
    // cari target splash terbaik berdasarkan nilai tertinggi
    // nilai = damage ke musuh + coverage gain
    private static MapLocation findBestSplashTarget(RobotController rc,
            MapLocation position, RobotInfo[] enemies, MapLocation focus)
            throws GameActionException {
        
        MapLocation best = null;
        int bestValue = MIN_SPLASH_VALUE - 1;
        
        MapLocation[] candidates = rc.getAllLocationsWithinRadiusSquared(position, 
                rc.getType().actionRadiusSquared);
        
        for (MapLocation loc : candidates) {
            if (Clock.getBytecodesLeft() < 2000) break;
            if (!rc.canAttack(loc)) continue;
            
            int value = 0;
            
            // bonus kalau dekat tower atau unit musuh
            for (RobotInfo enemy : enemies) {
                if (enemy.type.isTowerType() && loc.distanceSquaredTo(enemy.location) <= 4) {
                    value += 24;
                } else if (!enemy.type.isTowerType() && loc.distanceSquaredTo(enemy.location) <= 2) {
                    value += 14;  // kena unit musuh juga lumayan
                }
            }
            
            // bonus kalau dekat tower yang diketahui
            if (focus != null) {
                value += Math.max(0, 20 - loc.distanceSquaredTo(focus));
            }
            
            // hitung tile yang kena splash (radius 1)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    MapLocation tile = loc.translate(dx, dy);
                    if (!rc.canSenseLocation(tile)) continue;
                    MapInfo info = rc.senseMapInfo(tile);
                    if (info.isWall() || info.hasRuin()) continue;
                    
                    if (info.getPaint().isEnemy()) value += 10;
                    else if (info.getPaint() == PaintType.EMPTY) value += 4;
                }
            }
            
            // hitung outer ring (radius 2)
            int[][] outer = { { -2, 0 }, { 2, 0 }, { 0, -2 }, { 0, 2 } };
            for (int[] d : outer) {
                MapLocation tile = loc.translate(d[0], d[1]);
                if (!rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (!info.isWall() && !info.hasRuin()) {
                    if (info.getPaint().isEnemy()) value += 5;
                    else if (info.getPaint() == PaintType.EMPTY) value += 1;
                }
            }
            
            if (value > bestValue) {
                bestValue = value;
                best = loc;
            }
        }
        return best;
    }
    
    // cari kluster cat musuh atau tile kosong yang worth buat displash
    private static MapLocation findEnemyPaintCluster(RobotController rc,
            MapLocation position, MapInfo[] nearby) throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (MapInfo info : nearby) {
            if (info.isWall() || info.hasRuin()) continue;
            if (!info.getPaint().isEnemy() && info.getPaint() != PaintType.EMPTY) continue;
            
            MapLocation loc = info.getMapLocation();
            int score = 18 - position.distanceSquaredTo(loc);
            
            if (info.getPaint().isEnemy()) score += 16;  // cat musuh lebih prioritas
            
            // bonus kalau jauh dari spawn (bagus buat ekspansi)
            if (spawnTower != null) {
                score += loc.distanceSquaredTo(spawnTower) / 10;
            }
            
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }
    
    // selesaikan pola tower yang kebetulan lewat
    private static void completeTowerPatterns(RobotController rc) throws GameActionException {
        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            for (UnitType type : new UnitType[] {
                    UnitType.LEVEL_ONE_PAINT_TOWER,
                    UnitType.LEVEL_ONE_MONEY_TOWER,
                    UnitType.LEVEL_ONE_DEFENSE_TOWER }) {
                if (rc.canCompleteTowerPattern(type, ruin)) {
                    rc.completeTowerPattern(type, ruin);
                    Messaging.registerTower(ruin, type);
                    return;
                }
            }
        }
    }
    
    private static RobotInfo findEnemyTower(MapLocation position, RobotInfo[] enemies) {
        for (RobotInfo enemy : enemies) {
            if (enemy.type.isTowerType()) return enemy;
        }
        return null;
    }
    
    private static MapLocation findRefillTower(MapLocation position, RobotInfo[] allies) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        
        for (RobotInfo ally : allies) {
            if (!ally.type.isTowerType() || ally.paintAmount <= 10) continue;
            int dist = position.distanceSquaredTo(ally.location);
            if (dist < bestDist) {
                bestDist = dist;
                best = ally.location;
            }
        }
        return best != null ? best : Messaging.nearestAnyTower(position);
    }
    
    private static void initialize(RobotController rc, MapLocation position)
            throws GameActionException {
        spawnTower = position;
        for (RobotInfo r : rc.senseNearbyRobots(4, RobotPlayer.myTeam)) {
            if (r.type.isTowerType()) {
                spawnTower = r.location;
                break;
            }
        }
        SymmetryTracker.init(spawnTower);
        refreshEnemyTargets();
        targetIndex = (RobotPlayer.myID * 3 + 1) % 3;
    }
    
    private static void refreshEnemyTargets() {
        if (spawnTower == null) return;
        for (int i = 0; i < 3; i++) {
            enemyTargets[i] = SymmetryTracker.targetForSlot(spawnTower, i, RobotPlayer.myID);
        }
    }
}