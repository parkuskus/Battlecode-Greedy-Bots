package alternative_bots_1;

import battlecode.common.*;

// TOWERBOT - menara pertahanan & produksi
// tugas: serang musuh yang mendekat, spawn unit baru, upgrade kalau mampu, broadcast info musuh
// strategi spawn: early banyak soldier, mid mix soldier+splasher, late splasher dominan
public class TowerBot {
    
    private static final int EARLY_ROUND = 150;
    private static final int MID_ROUND = 400;
    private static final int LATE_ROUND = 700;
    private static final int TARGET_MONEY_TOWERS = 3;
    private static final int ECONOMY_PHASE_ROUND_LIMIT = 260;
    
    private static int spawnCount = 0; // berapa unit udah di-spawn
    private static MapLocation[] spawnLocations; // lokasi yang bisa spawn
    private static MapLocation enemyTarget; // perkiraan lokasi base musuh
    private static boolean hasBroadcast = false; // udah kirim info ke jaringan?
    
    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation position = rc.getLocation();
        int round = rc.getRoundNum();
        
        if (spawnLocations == null) {
            initialize(rc, position);
        }
        
        Messaging.readMessages();
        Messaging.registerTower(position, rc.getType());
        
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[] nearby = rc.senseNearbyMapInfos(-1);
        
        SymmetryTracker.observe(nearby);
        enemyTarget = SymmetryTracker.targetForSlot(position, 0, RobotPlayer.myID + 31);
        
        // broadcast lokasi tower musuh kalau terlihat
        RobotInfo enemyTower = findEnemyTower(position, enemies);
        if (enemyTower != null) {
            Messaging.registerEnemyTower(enemyTower.location, round);
            if (rc.canBroadcastMessage()) {
                rc.broadcastMessage(Messaging.encodeEnemyTower(enemyTower.location));
            }
        }
        
        attackEnemies(rc, enemies);
        
        upgradeIfPossible(rc, round);
        
        // broadcast info tower sekali di awal
        if (!hasBroadcast && rc.canBroadcastMessage()) {
            rc.broadcastMessage(Messaging.encodeTowerBuilt(position, rc.getType()));
            hasBroadcast = true;
        }
        
        spawnUnits(rc, position, round, allies, enemies, nearby);
    }
    
    // pilih target terbaik dan serang
    // prioritas: tower > soldier > unit lain
    private static void attackEnemies(RobotController rc, RobotInfo[] enemies)
            throws GameActionException {
        RobotInfo bestTarget = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (RobotInfo enemy : enemies) {
            if (!rc.canAttack(enemy.location)) continue;
            
            int score = 0;
            
            if (enemy.type.isTowerType()) {
                score += 80;   // tower = target utama
            } else if (enemy.type == UnitType.SOLDIER) {
                score += 24;   // soldier berbahaya
            } else {
                score += 12;
            }
            
            // makin dekat makin bagus
            score += 18 - enemy.location.distanceSquaredTo(rc.getLocation()) / 2;
            
            // bonus kalau musuh lagi di tile kita
            if (rc.canSenseLocation(enemy.location)) {
                PaintType paint = rc.senseMapInfo(enemy.location).getPaint();
                if (paint.isAlly()) score += 10;
                else if (paint == PaintType.EMPTY) score += 4;
            }
            
            // bonus target HP rendah, lebih gampang dibunuh
            score -= enemy.health / 20;
            
            if (score > bestScore) {
                bestTarget = enemy;
                bestScore = score;
            }
        }
        
        if (bestTarget != null) {
            rc.attack(bestTarget.location);
        }
    }
    
    // upgrade ke level berikutnya kalau punya cukup chips
    private static void upgradeIfPossible(RobotController rc, int round)
            throws GameActionException {
        UnitType type = rc.getType();
        int chips = rc.getChips();
        
        // jangan upgrade terlalu awal, butuh chips buat spawn unit
        if (round < 80) return;
        
        // level 1 -> 2: butuh 2500 chips dan minimal 2 tower
        if (type.level == 1 && chips >= 2500 && Messaging.towerCount >= 2) {
            if (rc.canUpgradeTower(rc.getLocation())) {
                rc.upgradeTower(rc.getLocation());
            }
        }
        // level 2 -> 3: butuh 4500 chips dan round > 300
        else if (type.level == 2 && round > 300 && chips >= 4500) {
            if (rc.canUpgradeTower(rc.getLocation())) {
                rc.upgradeTower(rc.getLocation());
            }
        }
    }
    
    // produksi unit baru dengan strategi greedy
    private static void spawnUnits(RobotController rc, MapLocation position,
            int round, RobotInfo[] allies, RobotInfo[] enemies, MapInfo[] nearby)
            throws GameActionException {
        
        // darurat: kalau ada soldier musuh terlalu dekat, langsung spawn mopper
        RobotInfo threat = findNearestEnemySoldier(position, enemies);
        if (threat != null && position.distanceSquaredTo(threat.location) <= 9) {
            if (trySpawnInDirection(rc, UnitType.MOPPER, threat.location, enemies)) {
                return;
            }
        }
        
        sendMopperRequest(rc, allies, nearby);
        
        UnitType spawnType = chooseUnitType(rc, round, enemies, nearby);
        
        if (tryBestSpawn(rc, spawnType)) return;
        
        // fallback: spawn ke arah manapun yang bisa
        for (Direction dir : RobotPlayer.DIRS) {
            MapLocation loc = position.add(dir);
            if (rc.canBuildRobot(spawnType, loc)) {
                rc.buildRobot(spawnType, loc);
                sendTowerInfo(rc, loc);
                spawnCount++;
                return;
            }
        }
    }
    
    // pilih tipe unit berdasarkan fase permainan
    private static UnitType chooseUnitType(RobotController rc, int round,
            RobotInfo[] enemies, MapInfo[] nearby) throws GameActionException {

        if (!isEconomyPhaseDone(rc)) {
            int mod = spawnCount % 5;
            if (mod == 4) return UnitType.MOPPER;
            return UnitType.SOLDIER;
        }
        
        int combatEnemies = 0;
        for (RobotInfo enemy : enemies) {
            if (!enemy.type.isTowerType()) combatEnemies++;
        }
        
        // kalau banyak musuh, spawn mopper buat defense
        if (combatEnemies >= 4) return UnitType.MOPPER;
        
        // early game: 2 soldier : 2 splasher : 1 mopper
        if (round < EARLY_ROUND) {
            int mod = spawnCount % 5;
            if (mod == 4) return UnitType.MOPPER;
            if (mod >= 2) return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }
        
        // mid game: 2 soldier : 3 splasher : 1 mopper
        if (round < MID_ROUND) {
            int mod = spawnCount % 6;
            if (mod == 5) return UnitType.MOPPER;
            if (mod >= 2) return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }
        
        // late game: 2 soldier : 2 splasher : 1 mopper
        if (round < LATE_ROUND) {
            int mod = spawnCount % 5;
            if (mod == 4) return UnitType.MOPPER;
            if (mod >= 2) return UnitType.SPLASHER;
            return UnitType.SOLDIER;
        }
        
        // very late: fokus splasher buat menang coverage
        // 1 soldier : 2 splasher : 1 mopper
        if (spawnCount % 4 == 3) return UnitType.MOPPER;
        if (spawnCount % 4 >= 1) return UnitType.SPLASHER;
        return UnitType.SOLDIER;
    }

    private static boolean isEconomyPhaseDone(RobotController rc) {
        if (Messaging.countMoneyTowers() < TARGET_MONEY_TOWERS) return false;
        return rc.getRoundNum() >= ECONOMY_PHASE_ROUND_LIMIT;
    }
    
    // cari lokasi spawn terbaik, arahkan mendekati musuh
    private static boolean tryBestSpawn(RobotController rc, UnitType type)
            throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        
        MapLocation towerHint = Messaging.enemyTowerHint();
        MapLocation target = towerHint != null ? towerHint : enemyTarget;
        MapLocation mapCenter = new MapLocation(RobotPlayer.mapW / 2, RobotPlayer.mapH / 2);
        
        for (MapLocation loc : spawnLocations) {
            if (!rc.canBuildRobot(type, loc)) continue;
            
            MapInfo info = rc.senseMapInfo(loc);
            
            // hindari clustering dengan sekutu
            int nearbyAllies = 0;
            for (RobotInfo r : rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam)) {
                if (!r.type.isTowerType()) nearbyAllies++;
            }
            
            int score = 30 - loc.distanceSquaredTo(target) / 4;
            
            // bonus kalau spawn ke arah tengah map
            score += 16 - loc.distanceSquaredTo(mapCenter) / 6;
            
            // penalty clustering
            score -= nearbyAllies * 8;
            
            if (info.getPaint().isAlly()) score += 4;
            else if (info.getPaint() == PaintType.EMPTY) score += 2;
            else score -= 6;  // tile musuh, berbahaya
            
            if (type == UnitType.SOLDIER) score += 6;
            else if (type == UnitType.SPLASHER) score += 4;
            
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        
        if (best != null) {
            rc.buildRobot(type, best);
            sendTowerInfo(rc, best);
            spawnCount++;
            return true;
        }
        return false;
    }
    
    // spawn ke arah target tertentu, buat kasus darurat
    private static boolean trySpawnInDirection(RobotController rc, UnitType type,
            MapLocation target, RobotInfo[] enemies) throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        
        for (MapLocation loc : spawnLocations) {
            if (!rc.canBuildRobot(type, loc)) continue;
            
            int score = 40 - loc.distanceSquaredTo(target);
            if (Nav.inEnemyTowerRange(loc, enemies)) score -= 20;
            
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        
        if (best != null) {
            rc.buildRobot(type, best);
            sendTowerInfo(rc, best);
            spawnCount++;
            return true;
        }
        return false;
    }
    
    private static RobotInfo findEnemyTower(MapLocation position, RobotInfo[] enemies) {
        for (RobotInfo enemy : enemies) {
            if (enemy.type.isTowerType()) return enemy;
        }
        return null;
    }
    
    private static RobotInfo findNearestEnemySoldier(MapLocation position, RobotInfo[] enemies) {
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        
        for (RobotInfo enemy : enemies) {
            if (enemy.type == UnitType.SOLDIER) {
                int dist = position.distanceSquaredTo(enemy.location);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = enemy;
                }
            }
        }
        return best;
    }
    
    // minta mopper kalau ada banyak cat musuh di sekitar
    private static void sendMopperRequest(RobotController rc, RobotInfo[] allies,
            MapInfo[] nearby) throws GameActionException {
        int enemyPaintCount = 0;
        MapLocation enemyPaintLoc = null;
        
        for (MapInfo info : nearby) {
            if (info.getPaint().isEnemy()) {
                enemyPaintCount++;
                if (enemyPaintLoc == null) enemyPaintLoc = info.getMapLocation();
            }
        }
        
        if (enemyPaintCount >= 8 && enemyPaintLoc != null) {
            Messaging.needMopperAt = enemyPaintLoc;
        }
    }
    
    // kasih tau unit baru lokasi tower musuh yang kita tau
    private static void sendTowerInfo(RobotController rc, MapLocation loc)
            throws GameActionException {
        if (rc.canSendMessage(loc)) {
            MapLocation enemyHint = Messaging.enemyTowerHint();
            if (enemyHint != null) {
                rc.sendMessage(loc, Messaging.encodeEnemyTower(enemyHint));
            }
        }
    }
    
    // inisialisasi waktu tower pertama aktif
    private static void initialize(RobotController rc, MapLocation position)
            throws GameActionException {
        spawnLocations = rc.getAllLocationsWithinRadiusSquared(position,
                GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
        
        SymmetryTracker.init(position);
        enemyTarget = SymmetryTracker.targetForSlot(position, 0, RobotPlayer.myID + 31);
    }
}