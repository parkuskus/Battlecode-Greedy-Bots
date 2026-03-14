package alternative_bots_1;

import battlecode.common.*;

// SOLDIERBOT
// serang tower musuh, cat wilayah, bangun tower, isi ulang kalau kepepet
// selalu pilih aksi dengan nilai tertinggi saat ini, jangan mundur
public class SoldierBot {
    
    // batas cat untuk isi ulang
    private static final double CRITICAL_PAINT = 0.10; // 10% - wajib isi
    private static final double ENOUGH_PAINT = 0.50; // 50% - cukup untuk explore
    private static final int MAX_REFILL_DIST = 100; // jarak max ke tower untuk isi ulang
    private static final int TARGET_MONEY_TOWERS = 3;
    private static final int ECONOMY_PHASE_ROUND_LIMIT = 260;
    private static final int MIN_CHIPS_FOR_SRP = 120;
    
    private static MapLocation spawnTower; // tower tempat kita lahir
    private static final MapLocation[] enemyTargets = new MapLocation[3]; // 3 kemungkinan lokasi musuh
    private static int targetIndex; // target aktif saat ini
    private static int roundLastSwitch = -9999; // kapan terakhir ganti target
    
    private static MapLocation knownEnemyTower; // tower musuh yang kita ingat
    private static boolean srpCompleted = false;
    
    private enum Status { ATTACK, BUILD, BUILD_SRP, RUSH, REFILL }
    private static Status status = Status.RUSH;
    
    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation position = rc.getLocation();
        
        if (spawnTower == null) {
            initialize(rc, position);
        }
        
        Messaging.readMessages();
        
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        
        SymmetryTracker.observe(nearby);
        refreshEnemyTargets();
        
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType()) {
                Messaging.registerTower(ally.location, ally.type);
            }
        }
        Messaging.relayToNearbyTower(allies);
        
        recordEnemyTower(rc, position, enemies);
        
        status = decideStatus(rc, position, allies, enemies);
        
        switch (status) {
            case ATTACK -> executeAttack(rc, position, enemies);
            case BUILD -> executeBuild(rc, position);
            case BUILD_SRP -> executeBuildSrp(rc, position);
            case REFILL -> executeRefill(rc, position, allies);
            default -> executeRush(rc, position, enemies);
        }
        
        // refresh posisi setelah gerak
        position = rc.getLocation();
        nearby = rc.senseNearbyMapInfos();
        
        // cat di bawah kaki dulu, wajib
        paintUnderFoot(rc, position);
        
        if (status != Status.REFILL) {
            paintNearestTile(rc, position, nearby);
        }
        
        completeTowerPatterns(rc);
        
        Nav.recordPosition(position);
    }
    
    // tentukan aksi terbaik sekarang
    // urutan prioritas:
    // - economy phase: refill > build money tower > build SRP > rush
    // - offensive phase: refill > attack > rush > build
    private static Status decideStatus(RobotController rc, MapLocation position,
            RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;
        
        // kalau cat udah kritis, balik ke tower dulu
        if (paintRatio < CRITICAL_PAINT && status != Status.REFILL) {
            MapLocation tower = findRefillTower(position, allies);
            if (tower != null && position.distanceSquaredTo(tower) <= MAX_REFILL_DIST) {
                return Status.REFILL;
            }
        }
        // stay di refill sampai cukup
        if (status == Status.REFILL && paintRatio < ENOUGH_PAINT) {
            return Status.REFILL;
        }

        boolean economyDone = isEconomyPhaseDone(rc);

        if (!economyDone) {
            MapLocation ruin = findNearestRuin(rc, position);
            if (ruin != null && rc.getChips() >= 200 && paintRatio >= 0.20) {
                return Status.BUILD;
            }

            MapLocation srpCenter = findBestSrpCenter(rc, position);
            if (srpCenter != null && rc.getChips() >= MIN_CHIPS_FOR_SRP && paintRatio >= 0.22) {
                return Status.BUILD_SRP;
            }

            return Status.RUSH;
        }
        
        // kalau liat tower musuh langsung serang
        RobotInfo visibleTower = findEnemyTower(enemies);
        if (visibleTower != null && paintRatio >= 0.20 && rc.getHealth() > 25) {
            knownEnemyTower = visibleTower.location;
            return Status.ATTACK;
        }
        
        // tau lokasi tower musuh tapi belum kelihatan, tetap kesana
        if (knownEnemyTower != null && paintRatio >= 0.18) {
            return Status.ATTACK;
        }
        MapLocation towerHint = Messaging.enemyTowerHint();
        if (towerHint != null && paintRatio >= 0.18) {
            knownEnemyTower = towerHint;
            return Status.ATTACK;
        }

        // rush ke prediksi base musuh
        MapLocation rushTarget = enemyTargets[targetIndex];
        if (rushTarget != null && paintRatio >= 0.15) {
            return Status.RUSH;
        }

        // bangun tower kalau lagi nganggur dan ada ruin
        MapLocation ruin = findNearestRuin(rc, position);
        if (ruin != null && rc.getChips() >= 200 && paintRatio >= 0.20) {
            return Status.BUILD;
        }
        
        return Status.RUSH;
    }
    
    // hancurkan tower musuh, maju terus
    private static void executeAttack(RobotController rc, MapLocation position, 
            RobotInfo[] enemies) throws GameActionException {
        
        RobotInfo tower = findEnemyTower(enemies);
        MapLocation target = tower != null ? tower.location : knownEnemyTower;
        
        if (target == null) {
            status = Status.RUSH;
            executeRush(rc, position, enemies);
            return;
        }
        
        // serang dulu kalau sudah dalam jangkauan
        if (tower != null && rc.canAttack(tower.location)) {
            rc.attack(tower.location);
        }
        
        // maju kalau belum dalam jangkauan
        int dist = position.distanceSquaredTo(target);
        if (dist > rc.getType().actionRadiusSquared) {
            Direction dir = position.directionTo(target);
            Direction[] order = Nav.fuzzyOrder(dir);
            for (Direction d : order) {
                if (rc.canMove(d)) {
                    rc.move(d);
                    break;
                }
            }
        }
        
        // coba serang lagi setelah gerak
        position = rc.getLocation();
        tower = findEnemyTower(rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam));
        if (tower != null && rc.canAttack(tower.location)) {
            rc.attack(tower.location);
        }
        
        // tower udah hancur, hapus dari memori
        if (knownEnemyTower != null && rc.canSenseLocation(knownEnemyTower)) {
            RobotInfo robot = rc.senseRobotAtLocation(knownEnemyTower);
            if (robot == null || robot.team != RobotPlayer.enemyTeam || !robot.type.isTowerType()) {
                knownEnemyTower = null;
            }
        }
    }
    
    // dorong ke base musuh secepat mungkin
    // kalau nemu tower musuh di jalan langsung eskalasi ke mode attack
    private static void executeRush(RobotController rc, MapLocation position, 
            RobotInfo[] enemies) throws GameActionException {
        
        if (!rc.isMovementReady()) return;

        RobotInfo visibleTower = findEnemyTower(enemies);
        if (visibleTower != null) {
            knownEnemyTower = visibleTower.location;
            status = Status.ATTACK;
            executeAttack(rc, position, enemies);
            return;
        }

        MapLocation target = enemyTargets[targetIndex];
        if (target == null) {
            targetIndex = (targetIndex + 1) % 3;
            target = enemyTargets[targetIndex];
        }
        if (target != null) {
            boolean arrived = position.distanceSquaredTo(target) <= 9;
            boolean stuckTooLong = rc.getRoundNum() - roundLastSwitch > 55;
            if (arrived || stuckTooLong) {
                targetIndex = (targetIndex + 1) % 3;
                roundLastSwitch = rc.getRoundNum();
                target = enemyTargets[targetIndex];
            }
            if (target != null) {
                Nav.aggressiveMove(target);
                return;
            }
        }
        
        // fallback: cari frontier tile biar tetap produktif sambil nunggu info musuh
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation frontier = null;
        MapLocation emptyTile = null;
        int frontierDist = Integer.MAX_VALUE;
        int emptyDist = Integer.MAX_VALUE;
        
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 2000) break;
            if (info.isWall() || info.hasRuin()) continue;
            if (info.getPaint() != PaintType.EMPTY) continue;
            
            MapLocation loc = info.getMapLocation();
            int dist = position.distanceSquaredTo(loc);
            
            if (dist < emptyDist) {
                emptyDist = dist;
                emptyTile = loc;
            }
            
            if (dist > 0 && dist < frontierDist) {
                for (Direction d : RobotPlayer.DIRS) {
                    MapLocation adj = loc.add(d);
                    if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) {
                        frontierDist = dist;
                        frontier = loc;
                        break;
                    }
                }
            }
        }
        
        if (frontier != null) {
            Nav.fuzzyMove(frontier);
            return;
        }
        if (emptyTile != null && emptyDist <= 25) {
            Nav.fuzzyMove(emptyTile);
            return;
        }
    }
    
    // balik ke tower untuk isi ulang, cepet-cepet balik lagi
    private static void executeRefill(RobotController rc, MapLocation position, 
            RobotInfo[] allies) throws GameActionException {
        
        MapLocation target = findRefillTower(position, allies);
        if (target == null) target = Messaging.nearestAnyTower(position);
        if (target == null) target = spawnTower;
        if (target == null) {
            status = Status.RUSH;
            return;
        }
        
        if (position.distanceSquaredTo(target) <= 2) {
            int needed = rc.getType().paintCapacity - rc.getPaint();
            if (needed > 0 && rc.canTransferPaint(target, -needed)) {
                rc.transferPaint(target, -needed);
            }
        } else {
            Nav.aggressiveMove(target);
        }
    }
    
    // bangun tower di ruin terdekat
    // paint tower dulu for economic safety
    private static void executeBuild(RobotController rc, MapLocation position)
            throws GameActionException {
        
        MapLocation ruin = findNearestRuin(rc, position);
        if (ruin == null) {
            status = Status.RUSH;
            return;
        }
        
        UnitType type;
        if (Messaging.countPaintTowers() == 0) {
            type = UnitType.LEVEL_ONE_PAINT_TOWER;
        } else if (Messaging.countMoneyTowers() < TARGET_MONEY_TOWERS) {
            type = UnitType.LEVEL_ONE_MONEY_TOWER;
        } else {
            type = UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        
        if (rc.canMarkTowerPattern(type, ruin)) {
            rc.markTowerPattern(type, ruin);
        }
        
        paintMarkedTiles(rc, ruin);
        
        if (position.distanceSquaredTo(ruin) > 8) {
            Nav.aggressiveMove(ruin);
        } else if (position.distanceSquaredTo(ruin) > 2) {
            Direction d = position.directionTo(ruin);
            if (rc.canMove(d)) rc.move(d);
        }
        
        if (rc.canCompleteTowerPattern(type, ruin)) {
            rc.completeTowerPattern(type, ruin);
            Messaging.registerTower(ruin, type);
        }
    }

    private static void executeBuildSrp(RobotController rc, MapLocation position)
            throws GameActionException {
        MapLocation center = findBestSrpCenter(rc, position);
        if (center == null) {
            status = Status.RUSH;
            return;
        }

        if (rc.canMarkResourcePattern(center)) {
            rc.markResourcePattern(center);
        }

        paintMarkedTiles(rc, center);

        if (rc.isMovementReady() && position.distanceSquaredTo(center) > 2) {
            Nav.bugNav(center);
        }

        if (rc.canCompleteResourcePattern(center)) {
            rc.completeResourcePattern(center);
            srpCompleted = true;
        }
    }
    
    // cat tile yang udah dimark buat pola tower
    private static void paintMarkedTiles(RobotController rc, MapLocation center)
            throws GameActionException {
        if (!rc.isActionReady()) return;
        
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (Clock.getBytecodesLeft() < 1200) return;
                
                MapLocation tile = center.translate(dx, dy);
                if (!rc.canSenseLocation(tile)) continue;
                
                MapInfo info = rc.senseMapInfo(tile);
                if (info.hasRuin() || info.isWall()) continue;
                
                PaintType mark = info.getMark();
                if (mark != PaintType.EMPTY && mark != info.getPaint() && !mark.isEnemy()) {
                    if (rc.canAttack(tile)) {
                        rc.attack(tile, mark == PaintType.ALLY_SECONDARY);
                        return;
                    }
                }
            }
        }
    }
    
    // cat tile sendiri tiap giliran
    private static void paintUnderFoot(RobotController rc, MapLocation position)
            throws GameActionException {
        if (!rc.isActionReady()) return;
        
        MapInfo info = rc.senseMapInfo(position);
        PaintType paint = info.getPaint();
        
        if (paint == PaintType.EMPTY || paint.isEnemy()) {
            PaintType mark = info.getMark();
            boolean secondary = (mark == PaintType.ALLY_SECONDARY);
            if (rc.canAttack(position)) {
                rc.attack(position, secondary);
            }
        }
    }
    
    // cat tile kosong atau musuh terdekat buat coverage
    private static void paintNearestTile(RobotController rc, MapLocation position, MapInfo[] nearby)
            throws GameActionException {
        if (!rc.isActionReady()) return;
        
        // prioritasin tile kosong dulu
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1500) break;
            
            MapLocation loc = info.getMapLocation();
            PaintType paint = info.getPaint();
            
            if (paint == PaintType.EMPTY && !info.isWall() && !info.hasRuin() 
                    && rc.canAttack(loc)) {
                PaintType mark = info.getMark();
                boolean secondary = (mark == PaintType.ALLY_SECONDARY);
                rc.attack(loc, secondary);
                return;
            }
        }
        
        // kalau ga ada yang kosong, cat tile musuh
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1000) break;
            
            MapLocation loc = info.getMapLocation();
            PaintType paint = info.getPaint();
            
            if (paint.isEnemy() && !info.isWall() && !info.hasRuin() 
                    && rc.canAttack(loc)) {
                rc.attack(loc);
                return;
            }
        }
    }

    // cat agresif sambil jalan - prioritas tile sendiri > musuh > kosong
    private static void paintAggressively(RobotController rc, MapLocation position, MapInfo[] nearby)
            throws GameActionException {
        if (!rc.isActionReady()) return;
        
        // tile sendiri dulu
        MapInfo selfInfo = rc.senseMapInfo(position);
        PaintType selfPaint = selfInfo.getPaint();
        if (selfPaint == PaintType.EMPTY || selfPaint.isEnemy()) {
            PaintType mark = selfInfo.getMark();
            boolean secondary = (mark == PaintType.ALLY_SECONDARY);
            if (rc.canAttack(position)) {
                rc.attack(position, secondary);
                return;
            }
        }
        
        // pilih target terbaik di sekitar
        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1000) break;
            if (info.isWall() || info.hasRuin()) continue;
            
            MapLocation loc = info.getMapLocation();
            if (!rc.canAttack(loc)) continue;
            
            PaintType paint = info.getPaint();
            if (paint.isAlly()) continue;
            
            int dist = position.distanceSquaredTo(loc);
            int score = 100 - dist;
            
            if (paint.isEnemy()) score += 50;
            else if (paint == PaintType.EMPTY) score += 30;
            
            if (score > bestScore) {
                bestScore = score;
                bestTarget = loc;
            }
        }
        
        if (bestTarget != null) {
            PaintType mark = rc.senseMapInfo(bestTarget).getMark();
            boolean secondary = (mark == PaintType.ALLY_SECONDARY);
            rc.attack(bestTarget, secondary);
        }
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

        MapLocation me = rc.getLocation();
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dy = -2; dy <= 2; dy += 4) {
                MapLocation center = new MapLocation(
                        ((me.x + dx + 2) / 4) * 4 + 2,
                        ((me.y + dy + 2) / 4) * 4 + 2);
                if (me.distanceSquaredTo(center) <= 2 && rc.canCompleteResourcePattern(center)) {
                    rc.completeResourcePattern(center);
                    srpCompleted = true;
                    return;
                }
            }
        }
    }

    private static MapLocation findBestSrpCenter(RobotController rc, MapLocation position)
            throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int dx = -8; dx <= 8; dx += 4) {
            for (int dy = -8; dy <= 8; dy += 4) {
                if (Clock.getBytecodesLeft() < 2500) return best;

                MapLocation center = new MapLocation(
                        ((position.x + dx + 2) / 4) * 4 + 2,
                        ((position.y + dy + 2) / 4) * 4 + 2);
                if (!rc.onTheMap(center)) continue;
                if (position.distanceSquaredTo(center) > 64) continue;

                if (!rc.canMarkResourcePattern(center) && !rc.canCompleteResourcePattern(center)) continue;

                int score = 42 - position.distanceSquaredTo(center);
                for (MapInfo info : rc.senseNearbyMapInfos(center, 8)) {
                    if (info.getPaint().isEnemy()) score -= 3;
                    else if (info.getPaint().isAlly()) score += 1;
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = center;
                }
            }
        }
        return best;
    }

    private static boolean isEconomyPhaseDone(RobotController rc) {
        if (Messaging.countMoneyTowers() < TARGET_MONEY_TOWERS) return false;
        return srpCompleted || rc.getRoundNum() >= ECONOMY_PHASE_ROUND_LIMIT;
    }
    
    // cari tower musuh - pilih yang HP-nya paling rendah
    private static RobotInfo findEnemyTower(RobotInfo[] enemies) {
        RobotInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (RobotInfo m : enemies) {
            if (!m.type.isTowerType()) continue;
            
            int score = 1000 + (300 - m.health) * 4;
            if (m.type.getBaseType() == UnitType.LEVEL_ONE_DEFENSE_TOWER) {
                score -= 50;  // agak hindari defense tower
            }
            
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }
    
    // cari ruin terdekat yang belum ada tower-nya
    private static MapLocation findNearestRuin(RobotController rc, MapLocation position)
            throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        
        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            if (rc.senseRobotAtLocation(ruin) != null) continue;  // udah ada tower
            int dist = position.distanceSquaredTo(ruin);
            if (dist < bestDist) {
                bestDist = dist;
                best = ruin;
            }
        }
        return best;
    }
    
    // cari tower sekutu buat isi ulang cat
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
    
    // catat tower musuh yang terlihat dan kasih tau sekutu
    private static void recordEnemyTower(RobotController rc, MapLocation position,
            RobotInfo[] enemies) throws GameActionException {
        RobotInfo tower = findEnemyTower(enemies);
        
        if (tower != null) {
            knownEnemyTower = tower.location;
            Messaging.registerEnemyTower(knownEnemyTower, rc.getRoundNum());
            
            for (RobotInfo ally : rc.senseNearbyRobots(-1, RobotPlayer.myTeam)) {
                if (ally.type.isTowerType() && rc.canSendMessage(ally.location)) {
                    rc.sendMessage(ally.location, Messaging.encodeEnemyTower(knownEnemyTower));
                    break;
                }
            }
        }
    }
    
    // inisialisasi waktu pertama spawn
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
        targetIndex = RobotPlayer.myID % 3;
    }
    
    // refresh lokasi target berdasarkan simetri peta
    private static void refreshEnemyTargets() {
        if (spawnTower == null) return;
        for (int i = 0; i < 3; i++) {
            enemyTargets[i] = SymmetryTracker.targetForSlot(spawnTower, i, RobotPlayer.myID);
        }
    }
}