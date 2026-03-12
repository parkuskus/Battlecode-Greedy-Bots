package bot_kevin_1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/*
    Decision priority tiap round
    1. Upgrade diri sendiri jika chips cukup
    2. Menyerang musuh dengan HP paling rendah
    3. Broadcast lokasi tower ke robots baru
    4. Spawn units using dengan pembagian fase:
        - Early (round <200, <5 towers): spam soldiers + splashers
        - Mid   (round 200-600): spam 1:1:1 soldier/splasher/mopper
        - Late  (round >600): spam splashers untuk menutupi area lebih luas
    5. Self-destruct money towers jika chips banyak dan tidak ada musuh di sekitar.
        Alasannya agar tower baru dapat dibangun dan memberikan paint kepada robots di sekitar.
 */
public class TowerBot {

    private static int spawnCounter = 0;
    private static MapLocation[] spawnLocs;
    private static MapLocation diagEnemy;
    private static boolean isFirstTower = false;
    private static int lastChips = 0;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        if(spawnLocs == null) {
            spawnLocs = rc.getAllLocationsWithinRadiusSquared(me, GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
            diagEnemy = new MapLocation(
                    RobotPlayer.mapW - me.x - 1,
                    RobotPlayer.mapH - me.y - 1);
            isFirstTower = rc.getRoundNum() < 8;
        }

        Messaging.readMessages();
        Messaging.registerTower(me, rc.getType());

        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        // upgrade
        tryUpgrade(rc, allies);
        // serang musuh
        attackBestTarget(rc, enemies);
        // informasikan lokasi tower di awal game
        if(rc.getRoundNum() < 5 && rc.canBroadcastMessage()) {
            rc.broadcastMessage(Messaging.encodeTowerBuilt(me, rc.getType()));
        }
        // informasikan tower yang diketahui kepada robots di sekitar
        relayTowerList(rc, allies);

        // spawan units besar-besaran sambil cek chips income
        boolean canAfford = lastChips != rc.getChips();
        lastChips = rc.getChips();

        boolean isPaintTower = rc.getType().getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER;
        boolean shouldSpawn = isPaintTower || isFirstTower || rc.getChips() > 1200;

        if(shouldSpawn && canAfford) {
            // mopper muncul jika keadaan darurat (musuh dekat tower)
            int enemySoldiers = 0;
            for(RobotInfo e : enemies)
                if(e.type == UnitType.SOLDIER) enemySoldiers++;
            if(enemySoldiers > 0) {
                Direction dir = me.directionTo(enemies[0].location);
                trySpawnDir(rc, UnitType.MOPPER, dir);
            }

            // jika ada paint musuh di sekitar minta tower kirimkan mopper
            MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
            for(MapInfo ti : tiles) {
                if(ti.getPaint().isEnemy()) {
                    // kirim mopper 
                    for(RobotInfo ally : allies) {
                        if(ally.type == UnitType.MOPPER && rc.canSendMessage(ally.location)) {
                            rc.sendMessage(ally.location,
                                Messaging.encodeNeedMopper(ti.getMapLocation()));
                            break;
                        }
                    }
                    break;
                }
            }

            spawnByPhase(rc, enemies);
        }

        // hancurkan tower jika dah kaya hehe
        if(rc.getType().getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER
            && rc.getChips() > 10000
            && enemies.length == 0
            && allies.length > 0
            && rc.getRoundNum() > 200) {
            rc.disintegrate();
        }
    }

    // upgrade kapan?
    private static void tryUpgrade(RobotController rc, RobotInfo[] allies)
            throws GameActionException {
        if(!rc.canUpgradeTower(rc.getLocation())) return;
        boolean isPaint = rc.getType().getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER;
        int threshold = isPaint ? 2500 : 3600;
        int allyThreshold = isPaint ? 3 : 4;
        if(rc.getChips() > threshold
            && (allies.length >= allyThreshold || rc.getChips() > threshold + 1000)) {
            rc.upgradeTower(rc.getLocation());
        }
    }

    // serang pasukan, lihat yang low hp
    private static void attackBestTarget(RobotController rc, RobotInfo[] enemies)
            throws GameActionException {
        RobotInfo best = null;
        int bestHP = Integer.MAX_VALUE;
        boolean foundSoldier = false;
        for(RobotInfo e : enemies) {
            if(!rc.canAttack(e.location)) continue;
            boolean isSoldier = e.type == UnitType.SOLDIER;
            if(isSoldier && !foundSoldier) {
                foundSoldier = true;
                best = e;
                bestHP = e.health;
            } else if(isSoldier && e.health < bestHP) {
                best = e;
                bestHP = e.health;
            } else if(!foundSoldier && e.health < bestHP) {
                best = e;
                bestHP = e.health;
            }
        }
        if(best != null) rc.attack(best.location);
    }

    // informasikan info yang dimiliki tower
    private static void relayTowerList(RobotController rc, RobotInfo[] allies)
            throws GameActionException {
        for(RobotInfo ally : allies) {
            if(!ally.type.isTowerType() && rc.canSendMessage(ally.location)) {
                for(int i = 0; i < Messaging.towerCount; i++) {
                    if(rc.canSendMessage(ally.location)) {
                        rc.sendMessage(ally.location,
                            Messaging.encodeTowerBuilt(
                                Messaging.knownTowers[i],
                                Messaging.knownTypes[i]));
                    } else break;
                }
            }
        }
    }

    // spawn berdasarkan fase secara greedy
    private static void spawnByPhase(RobotController rc, RobotInfo[] enemies)
            throws GameActionException {
        int round = rc.getRoundNum();
        int towers = rc.getNumberTowers();

        if(round < 200 && towers <= 5) {
            // Early: 2 soldiers lalu 1 splasher
            if(spawnCounter % 3 < 2) {
                if(!trySpawnBest(rc, UnitType.SOLDIER))
                    trySpawnBest(rc, UnitType.SPLASHER);
            } else {
                if(!trySpawnBest(rc, UnitType.SPLASHER))
                    trySpawnBest(rc, UnitType.SOLDIER);
            }
            spawnCounter++;
        } else if(rc.getChips() > 1100) {
            // Mid: 1 soldier + 1 splasher + 1 mopper
            int phase = spawnCounter % 3;
            if(phase == 0) {
                trySpawnBest(rc, UnitType.SOLDIER);
            } else if(phase == 1) {
                trySpawnBest(rc, UnitType.MOPPER);
            } else {
                trySpawnBest(rc, UnitType.SPLASHER);
            }
            spawnCounter++;
        }

        // yang Late akan diimplementasikan :)
    }

    // spawn robots ke tile yang warnanya adalah warna tim kita
    private static boolean trySpawnBest(RobotController rc, UnitType type)
            throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MAX_VALUE;
        for(MapLocation loc : spawnLocs) {
            if(!rc.canBuildRobot(type, loc)) continue;
            // avoid spawning on edges
            if(loc.x < 3 || loc.y < 3
                || loc.x >= RobotPlayer.mapW - 3
                || loc.y >= RobotPlayer.mapH - 3) continue;
            MapInfo info = rc.senseMapInfo(loc);
            int adj = countAdjacentAllies(rc, loc);
            int score = adj * 10;
            if(!info.getPaint().isAlly()) score += 5;
            if(score < bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        if(best != null) {
            rc.buildRobot(type, best);
            return true;
        }
        return trySpawnDir(rc, type, rc.getLocation().directionTo(diagEnemy));
    }

    private static boolean trySpawnDir(RobotController rc, UnitType type, Direction dir)
            throws GameActionException {
        MapLocation loc2 = rc.getLocation().add(dir).add(dir);
        MapLocation loc1 = rc.getLocation().add(dir);
        if(rc.canBuildRobot(type, loc2)) { rc.buildRobot(type, loc2); return true; }
        if(rc.canBuildRobot(type, loc1)) { rc.buildRobot(type, loc1); return true; }
        return false;
    }

    private static int countAdjacentAllies(RobotController rc, MapLocation loc)
            throws GameActionException {
        int count = 0;
        for(RobotInfo r : rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam))
            if(!r.type.isTowerType()) count++;
        return count;
    }
}
