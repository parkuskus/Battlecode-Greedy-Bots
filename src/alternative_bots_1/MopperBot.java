package alternative_bots_1;

import battlecode.common.*;

// MOPPER BOT
// prioritas hapus cat musuh di dekat tower kita, kejar unit musuh untuk steal paint, transfer paint ke sekutu yang butuh
public class MopperBot {
    
    private static final double LOW_PAINT_THRESHOLD = 0.10;   // 10% - perlu isi ulang
    private static final double ENOUGH_PAINT_THRESHOLD = 0.50;    // 50% - cukup untuk bertempur
    private static final int MAX_REFILL_DISTANCE = 100;    // maksimal jarak ke tower
    private static final int TARGET_MONEY_TOWERS = 3;
    private static final int ECONOMY_PHASE_ROUND_LIMIT = 260;
    
    private static MapLocation spawnTower;
    private static final MapLocation[] enemyTargets = new MapLocation[3];
    private static int targetIndex;
    private static int roundLastTargetSwitch = -9999;
    
    private enum Status { REFILL, CHASE_TOWER, CLEAN, RUSH }
    private static Status status = Status.RUSH;
    
    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation position = rc.getLocation();
        
        if (spawnTower == null) initialize(rc, position);
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
        
        // GREEDY BY DECISION
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;
        status = decideStatus(rc, position, allies, enemies, paintRatio);
        
        if (status == Status.REFILL) {
            executeRefill(rc, position, allies);
        } else {
            // serang
            executeMop(rc, position, nearby, enemies);
            // transfer cat ke sekutu yang butuh
            transferPaintToAlly(rc, position, allies);
            // gerak
            movementLogic(rc, position, nearby, allies, enemies);
        }
        // bantu selesaikan pola tower
        completeTowerPatterns(rc);
        
        Nav.recordPosition(rc.getLocation());
    }
    

    private static Status decideStatus(RobotController rc, MapLocation position,
            RobotInfo[] allies, RobotInfo[] enemies, double paintRatio) {
        
        // PRIORITAS 1: isi ulang jika cat rendah
        if ((status != Status.REFILL && paintRatio < LOW_PAINT_THRESHOLD)
                || (status == Status.REFILL && paintRatio < ENOUGH_PAINT_THRESHOLD)) {
            MapLocation tower = findRefillTower(position, allies);
            if (tower != null && position.distanceSquaredTo(tower) <= MAX_REFILL_DISTANCE) return Status.REFILL;
        }

        if (!isEconomyPhaseDone(rc)) {
            if (Messaging.needMopperAt != null) return Status.CLEAN;
            return Status.RUSH;
        }

        // PRIORITAS 2: cari tower musuh jika ada
        if (findEnemyTower(position, enemies) != null || Messaging.enemyTowerHint() != null) return Status.CHASE_TOWER;
        // PRIORITAS 3: bersihkan area yang butuh mopper
        if (Messaging.needMopperAt != null) return Status.CLEAN;

        // DEFAULT: rush ke base musuh
        return Status.RUSH;
    }

    private static boolean isEconomyPhaseDone(RobotController rc) {
        if (Messaging.countMoneyTowers() < TARGET_MONEY_TOWERS) return false;
        return rc.getRoundNum() >= ECONOMY_PHASE_ROUND_LIMIT;
    }
    
    // serang musuh atau hapus cat semua musuh
    private static void executeMop(RobotController rc, MapLocation position,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        if (!rc.isActionReady()) return;
        
        // PRIORITAS 1: serang tower musuh dalam jangkauan
        RobotInfo tower = findEnemyTower(position, enemies);
        if (tower != null && position.distanceSquaredTo(tower.location) <= rc.getType().actionRadiusSquared
                && rc.canAttack(tower.location)) {
            rc.attack(tower.location);
            return;
        }
        
        // PRIORITAS 2: serang unit musuh dalam jangkauan (steal paint)
        RobotInfo bestTarget = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (RobotInfo enemy : enemies) {
            if (enemy.type.isTowerType() || position.distanceSquaredTo(enemy.location) > 2) continue;
            int score = 30 - position.distanceSquaredTo(enemy.location);
            // bonus jika di tile yang menguntungkan
            if (rc.canSenseLocation(enemy.location)) {
                PaintType paint = rc.senseMapInfo(enemy.location).getPaint();
                if (paint.isEnemy()) score += 6;
                else if (paint.isAlly()) score += 10;
            }
            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }
        
        if (bestTarget != null && rc.canAttack(bestTarget.location)) {
            rc.attack(bestTarget.location);
            return;
        }
        
        // PRIORITAS 3: hapus cat musuh terdekat
        MapLocation bestPaintTarget = null;
        int bestPaintScore = Integer.MIN_VALUE;
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1000) return;
            MapLocation loc = info.getMapLocation();
            if (!info.getPaint().isEnemy()) continue;
            if (position.distanceSquaredTo(loc) > rc.getType().actionRadiusSquared) continue;
            if (!rc.canAttack(loc)) continue;
            // hitung skor ancaman
            int score = calculateThreatScore(rc, loc, Messaging.enemyTowerHint());
            
            if (score > bestPaintScore) {
                bestPaintScore = score;
                bestPaintTarget = loc;
            }
        }
        
        if (bestPaintTarget != null) {
            rc.attack(bestPaintTarget);
        }
    }

    private static int calculateThreatScore(RobotController rc, MapLocation loc, MapLocation towerHint) {
        int score = 20;
        // bonus jika dekat tower musuh
        if (towerHint != null) score += Math.max(0, 30 - loc.distanceSquaredTo(towerHint));
        // bonus jika dekat posisi kita
        score += 20 - rc.getLocation().distanceSquaredTo(loc);
        
        return score;
    }
    
    // transfer cat ke ally
    private static void transferPaintToAlly(RobotController rc, MapLocation position,
            RobotInfo[] allies) throws GameActionException {
        if (!rc.isActionReady()) return;
        
        int myPaint = rc.getPaint();
        int maxPaint = rc.getType().paintCapacity;
        
        // Hanya transfer jika punya cukup cat
        if (myPaint < maxPaint * 0.3) return;
        
        RobotInfo transferTarget = null;
        double lowestRatio = 0.3;
        
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType()) continue;
            if (position.distanceSquaredTo(ally.location) > 2) continue;
            
            double ratio = (double) ally.paintAmount / ally.type.paintCapacity;
            if (ratio < lowestRatio) {
                lowestRatio = ratio;
                transferTarget = ally;
            }
        }
        
        if (transferTarget != null) {
            int amount = Math.min(myPaint / 3,
                transferTarget.type.paintCapacity - transferTarget.paintAmount);
            if (amount > 0 && rc.canTransferPaint(transferTarget.location, amount)) {
                rc.transferPaint(transferTarget.location, amount);
            }
        }
    }
    
    // greedy movement
    private static void movementLogic(RobotController rc, MapLocation position,
            MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        if (!rc.isMovementReady()) return;
        MapLocation towerHint = Messaging.enemyTowerHint();
        RobotInfo tower = findEnemyTower(position, enemies);
        if (tower != null) towerHint = tower.location;
        // rush ke tower musuh
        if (status == Status.CHASE_TOWER && towerHint != null) {
            // cari cat musuh di dekat tower untuk dibersihkan
            MapLocation cleanTarget = findNearestCleanTileFromTarget(rc, position, nearby, towerHint);
            if (cleanTarget != null && position.distanceSquaredTo(cleanTarget) <= 8) {
                Nav.aggressiveMove(cleanTarget);
                return;
            }
            Nav.aggressiveMove(towerHint);
            return;
        }
        // pergi ke lokasi yang butuh mopper
        if (status == Status.CLEAN && Messaging.needMopperAt != null) {
            Nav.aggressiveMove(Messaging.needMopperAt);
            return;
        }
        // kejar unit musuh terdekat
        RobotInfo closestEnemy = null;
        int bestEnemyScore = Integer.MIN_VALUE;
        
        for (RobotInfo enemy : enemies) {
            if (enemy.type.isTowerType()) continue;
            int score = 40 - position.distanceSquaredTo(enemy.location);
            if (score > bestEnemyScore) {
                bestEnemyScore = score;
                closestEnemy = enemy;
            }
        }
        if (closestEnemy != null && bestEnemyScore >= 4) {
            Nav.aggressiveMove(closestEnemy.location);
            return;
        }
        // cari cat musuh terdekat
        MapLocation bestEnemyPaint = findBestEnemyPaint(rc, nearby);
        if (bestEnemyPaint != null && position.distanceSquaredTo(bestEnemyPaint) <= 50) {
            Nav.aggressiveMove(bestEnemyPaint);
            return;
        }
        // kawal soldier ke arah musuh
        RobotInfo bestSoldier = findBestAllySoldier(position, allies, towerHint);
        if (bestSoldier != null) {
            Nav.aggressiveMove(bestSoldier.location);
            return;
        }
        // rush ke base musuh
        MapLocation target = enemyTargets[targetIndex];
        if (target == null) return;
        
        boolean arrived = position.distanceSquaredTo(target) <= 16;
        boolean longStuck = rc.getRoundNum() - roundLastTargetSwitch > 80;
        if (arrived || longStuck) {
            targetIndex = (targetIndex + 1) % 3;
            roundLastTargetSwitch = rc.getRoundNum();
            target = enemyTargets[targetIndex];
        }
        
        Nav.aggressiveMove(target);
    }
    
    // refill paint
    private static void executeRefill(RobotController rc, MapLocation position,
            RobotInfo[] allies) throws GameActionException {
        MapLocation target = findRefillTower(position, allies);
        if (target == null) {
            Nav.fuzzyMove(RobotPlayer.DIRS[RobotPlayer.myID % 8]);
            return;
        }
        
        if (position.distanceSquaredTo(target) <= 2) {
            int needed = rc.getType().paintCapacity - rc.getPaint();
            if (needed > 0 && rc.canTransferPaint(target, -needed)) {
                rc.transferPaint(target, -needed);
            }
        } else {
            Nav.bugNav(target);
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
    }
    
    // helper functions
    private static RobotInfo findEnemyTower(MapLocation position, RobotInfo[] enemies) {
        for (RobotInfo enemy : enemies) {
            if (enemy.type.isTowerType()) return enemy;
        }
        return null;
    }
    
    private static MapLocation findRefillTower(MapLocation position, RobotInfo[] allies) {
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        
        for (RobotInfo ally : allies) {
            if (!ally.type.isTowerType() || ally.paintAmount <= 10) continue;
            int distance = position.distanceSquaredTo(ally.location);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ally.location;
            }
        }
        return best != null ? best : Messaging.nearestAnyTower(position);
    }
    
    private static MapLocation findNearestCleanTileFromTarget(RobotController rc,
            MapLocation position, MapInfo[] nearby, MapLocation target) {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (MapInfo info : nearby) {
            if (!info.getPaint().isEnemy()) continue;
            MapLocation loc = info.getMapLocation();
            
            int score = 30 - loc.distanceSquaredTo(target);
            score -= position.distanceSquaredTo(loc) / 2;
            
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }
    
    private static MapLocation findBestEnemyPaint(RobotController rc, MapInfo[] nearby) {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (MapInfo info : nearby) {
            if (!info.getPaint().isEnemy()) continue;
            MapLocation loc = info.getMapLocation();
            int score = 30 - rc.getLocation().distanceSquaredTo(loc);
            
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }
    
    private static RobotInfo findBestAllySoldier(MapLocation position,
            RobotInfo[] allies, MapLocation target) {
        if (target == null) return null;
        
        RobotInfo best = null;
        int bestDistToTarget = Integer.MAX_VALUE;
        
        for (RobotInfo ally : allies) {
            if (ally.type != UnitType.SOLDIER) continue;
            int distToTarget = ally.location.distanceSquaredTo(target);            
            if (distToTarget < bestDistToTarget) {
                bestDistToTarget = distToTarget;
                best = ally;
            }
        }
        return best;
    }
    
    private static void initialize(RobotController rc, MapLocation position)
            throws GameActionException {
        spawnTower = position;
        for (RobotInfo robot : rc.senseNearbyRobots(4, RobotPlayer.myTeam)) {
            if (robot.type.isTowerType()) {
                spawnTower = robot.location;
                break;
            }
        }
        SymmetryTracker.init(spawnTower);
        refreshEnemyTargets();
        targetIndex = (RobotPlayer.myID * 5 + 2) % 3;
    }
    
    private static void refreshEnemyTargets() {
        if (spawnTower == null) return;
        for (int i = 0; i < 3; i++) {
            enemyTargets[i] = SymmetryTracker.targetForSlot(spawnTower, i, RobotPlayer.myID);
        }
    }
}