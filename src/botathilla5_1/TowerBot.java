package botathilla5_1;

import battlecode.common.*;

/**
 * botathilla5_1 Tower:
 * - Early greedy ratio 4:3 (Soldier:Splasher), mopper emergency-only
 * - Splasher reserve hold when deficit exists but resources are not ready
 * - Emergency cleanup keeps mopper available under pressure
 */
public class TowerBot {

    private static int spawnCount = 0;
    private static int soldierSpawns = 0;
    private static int splasherSpawns = 0;
    private static int mopperSpawns = 0;
    private static int firstSplasherRound = -1;
    private static int holdSplasherRounds = 0;

    private static MapLocation[] spawnLocs;
    private static boolean hasBroadcast = false;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();

        if (spawnLocs == null) {
            spawnLocs = rc.getAllLocationsWithinRadiusSquared(me, GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
        }

        Messaging.readMessages();
        Messaging.registerTower(me, rc.getType());

        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[] nearby = rc.senseNearbyMapInfos(-1);

        attackBest(rc, enemies);
        upgradeIfAffordable(rc, round);

        if (!hasBroadcast && rc.canBroadcastMessage()) {
            rc.broadcastMessage(Messaging.encodeTowerBuilt(me, rc.getType()));
            hasBroadcast = true;
        }

        relayTowerList(allies);
        spawnGreedy(rc, me, round, allies, enemies, nearby);

        GreedyCore.traceMetric(rc, "TOWER", firstSplasherRound, soldierSpawns, splasherSpawns, mopperSpawns);
    }

    private static void attackBest(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo best = null;
        double bestScore = -1e9;
        double second = -1e9;
        int rejected = 0;
        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.location)) {
                rejected++;
                continue;
            }
            double score = GreedyCore.score(
                    0.4,
                    e.paintAmount > 0 ? 1.0 : 0.2,
                    e.type == UnitType.SOLDIER ? 2.2 : e.type == UnitType.SPLASHER ? 1.7 : 1.2,
                    0.5,
                    0.2,
                    e.health * 0.015,
                    0.2);
            if (score > bestScore) {
                second = bestScore;
                bestScore = score;
                best = e;
            } else if (score > second) {
                second = score;
            }
        }
        if (best != null) {
            GreedyCore.traceDecision(rc, "TOWER", "ATTACK", Integer.toString(best.ID), bestScore, second, rejected);
            rc.attack(best.location);
        }
    }

    private static void upgradeIfAffordable(RobotController rc, int round) throws GameActionException {
        if (round < 120 || Messaging.towerCount < 3) return;
        UnitType type = rc.getType();
        int chips = rc.getChips();
        if (type.level == 1 && chips >= 3200 && rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
        } else if (type.level == 2 && round > 420 && chips >= 5800 && rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
        }
    }

    private static void relayTowerList(RobotInfo[] allies) throws GameActionException {
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType()) continue;
            int sent = 0;
            for (int i = 0; i < Messaging.towerCount && sent < 3; i++) {
                if (Messaging.trySendMessage(ally.location,
                        Messaging.encodeTowerBuilt(Messaging.knownTowers[i], Messaging.knownTypes[i]))) sent++;
                else break;
            }
        }
    }

    private static void spawnGreedy(RobotController rc, MapLocation me, int round,
                                    RobotInfo[] allies, RobotInfo[] enemies, MapInfo[] nearby) throws GameActionException {
        if (!rc.isActionReady()) return;

        int enemySoldiers = 0;
        for (RobotInfo e : enemies) if (e.type == UnitType.SOLDIER) enemySoldiers++;
        int enemyPaint = countEnemyPaint(nearby);
        int frontier = estimateFrontierDemand(rc, nearby);

        UnitType chosen = chooseUnitType(rc, round, enemySoldiers, enemyPaint, frontier);
        if (chosen == null) chosen = fallbackUnitType(rc, enemySoldiers, enemyPaint);
        if (chosen == null) return;

        MapLocation loc = chooseSpawnLoc(rc, me, chosen, enemies, nearby);
        if (loc == null) {
            UnitType alt = fallbackUnitType(rc, enemySoldiers, enemyPaint);
            if (alt != null && alt != chosen) {
                MapLocation altLoc = chooseSpawnLoc(rc, me, alt, enemies, nearby);
                if (altLoc != null) {
                    chosen = alt;
                    loc = altLoc;
                }
            }
        }
        if (loc == null) return;

        rc.buildRobot(chosen, loc);
        spawnCount++;
        if (chosen == UnitType.SOLDIER) soldierSpawns++;
        else if (chosen == UnitType.SPLASHER) {
            splasherSpawns++;
            if (firstSplasherRound < 0) firstSplasherRound = round;
        } else mopperSpawns++;

        sendTowerInfo(loc);
    }

    private static UnitType chooseUnitType(RobotController rc, int round,
                                           int enemySoldiers, int enemyPaint, int frontier) {
        boolean[] feasible = new boolean[3];
        double[] score = new double[3];
        int[] dist = new int[]{0, 0, 0};
        int[] tie = new int[]{1, 2, 3};

        feasible[0] = hasBuildSlot(rc, UnitType.SOLDIER);
        feasible[1] = hasBuildSlot(rc, UnitType.SPLASHER);
        feasible[2] = hasBuildSlot(rc, UnitType.MOPPER);

        int produced = Math.max(1, soldierSpawns + splasherSpawns + mopperSpawns + 1);
        int mapArea = RobotPlayer.mapW * RobotPlayer.mapH;
        boolean smallMap = mapArea <= 1200;
        boolean largeMap = mapArea >= 2600;
        boolean earlyGame = round < 180;

        double soldierRatio;
        double splasherRatio;
        double mopperRatio;
        if (earlyGame) {
            soldierRatio = 4.0 / 7.0;
            splasherRatio = 3.0 / 7.0;
            mopperRatio = smallMap ? 1.0 / 10.0 : 0.0;
        } else if (largeMap) {
            soldierRatio = 5.0 / 9.0;
            splasherRatio = 3.0 / 9.0;
            mopperRatio = 1.0 / 9.0;
        } else {
            soldierRatio = 1.0 / 2.0;
            splasherRatio = 3.0 / 10.0;
            mopperRatio = 1.0 / 5.0;
        }

        double targetSoldier = produced * soldierRatio;
        double targetSplasher = produced * splasherRatio;
        double targetMopper = produced * mopperRatio;

        double defSoldier = targetSoldier - soldierSpawns;
        double defSplasher = targetSplasher - splasherSpawns;
        double defMopper = targetMopper - mopperSpawns;

        boolean emergencyCleanup = enemySoldiers >= 3 || enemyPaint >= 10;
        boolean earlyNoMopper = earlyGame && !emergencyCleanup;
        boolean splasherDeficit = defSplasher > 0.25;
        boolean splasherAffordable = rc.getPaint() >= UnitType.SPLASHER.paintCost
                && rc.getChips() >= UnitType.SPLASHER.moneyCost;
        boolean saveForSplasher = earlyGame && splasherDeficit && !splasherAffordable;

        if (earlyNoMopper) feasible[2] = false;

        if (saveForSplasher && !emergencyCleanup) {
            holdSplasherRounds++;
            feasible[2] = false;
        } else {
            holdSplasherRounds = 0;
        }

        score[0] = GreedyCore.score(
                frontier * 1.2,
                enemyPaint * 0.4,
                (earlyGame ? 4.2 : 4.8) + Math.max(0.0, defSoldier) * 1.6,
                2.2,
                0.8,
                enemySoldiers * 0.3,
                0.3);

        score[1] = GreedyCore.score(
                frontier * 1.8,
                enemyPaint * 1.7,
                (earlyGame ? 4.9 : 3.6) + Math.max(0.0, defSplasher) * 2.3,
                2.4,
                1.0,
                enemySoldiers * 0.45,
                0.8);

        score[2] = GreedyCore.score(
                enemyPaint * 1.1,
                enemyPaint + enemySoldiers,
                emergencyCleanup ? 3.2 : (earlyNoMopper ? -2.4 : (1.0 + Math.max(0.0, defMopper) * 1.1)),
                1.2,
                0.6,
                0.35,
                earlyNoMopper ? 2.5 : 1.0);

        if (saveForSplasher && !splasherAffordable) {
            score[0] -= (holdSplasherRounds >= 2 ? 1.8 : 0.8);
        }
        if (smallMap && round < 140 && enemyPaint >= 4) score[2] += 1.5;

        int[] rejected = new int[1];
        double[] second = new double[1];
        int idx = GreedyCore.pickBest(score, feasible, dist, tie, rejected, second);
        if (idx < 0) {
            GreedyCore.traceDecision(rc, "TOWER", "SPAWN", "NONE", -999.0, -999.0, rejected[0]);
            return null;
        }
        UnitType chosen = idx == 0 ? UnitType.SOLDIER : idx == 1 ? UnitType.SPLASHER : UnitType.MOPPER;
        GreedyCore.traceDecision(rc, "TOWER", "SPAWN", chosen.name(), score[idx], second[0], rejected[0]);
        return chosen;
    }

    private static UnitType fallbackUnitType(RobotController rc, int enemySoldiers, int enemyPaint) {
        if (enemySoldiers >= 2 || enemyPaint >= 10) {
            if (hasBuildSlot(rc, UnitType.MOPPER)) return UnitType.MOPPER;
            if (hasBuildSlot(rc, UnitType.SPLASHER)) return UnitType.SPLASHER;
            if (hasBuildSlot(rc, UnitType.SOLDIER)) return UnitType.SOLDIER;
        } else {
            if (hasBuildSlot(rc, UnitType.SOLDIER)) return UnitType.SOLDIER;
            if (hasBuildSlot(rc, UnitType.SPLASHER)) return UnitType.SPLASHER;
            if (hasBuildSlot(rc, UnitType.MOPPER)) return UnitType.MOPPER;
        }
        return null;
    }

    private static MapLocation chooseSpawnLoc(RobotController rc, MapLocation me, UnitType type,
                                              RobotInfo[] enemies, MapInfo[] nearby) throws GameActionException {
        MapLocation pressure = nearestPressurePoint(me, enemies, nearby);
        MapLocation best = null;
        double bestScore = -1e9;
        double second = -1e9;
        int rejected = 0;

        for (MapLocation loc : spawnLocs) {
            if (!rc.canBuildRobot(type, loc)) {
                rejected++;
                continue;
            }
            MapInfo info = rc.senseMapInfo(loc);
            int crowd = rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam).length;
            int distPress = pressure == null ? 16 : loc.distanceSquaredTo(pressure);
            boolean towerRisk = Nav.inEnemyTowerRange(loc, enemies);

            double objective = 2.4 - distPress * (type == UnitType.SPLASHER ? 0.08 : 0.06);
            double sc = GreedyCore.score(
                    info.getPaint().isAlly() ? 0.8 : 0.5,
                    type == UnitType.SPLASHER ? 0.6 : 0.2,
                    objective,
                    type == UnitType.SOLDIER ? 1.2 : 1.0,
                    Math.max(0, 5 - crowd) * 0.2,
                    towerRisk ? 2.1 : 0.2,
                    crowd * 0.22);
            if (sc > bestScore) {
                second = bestScore;
                bestScore = sc;
                best = loc;
            } else if (sc > second) second = sc;
        }

        if (best != null) {
            GreedyCore.traceDecision(rc, "TOWER", "SPAWN_LOC", best.x + "," + best.y, bestScore, second, rejected);
        }
        return best;
    }

    private static void sendTowerInfo(MapLocation target) throws GameActionException {
        int sent = 0;
        for (int i = 0; i < Messaging.towerCount && sent < 3; i++) {
            if (Messaging.trySendMessage(target,
                    Messaging.encodeTowerBuilt(Messaging.knownTowers[i], Messaging.knownTypes[i]))) sent++;
            else break;
        }
    }

    private static boolean hasBuildSlot(RobotController rc, UnitType type) {
        for (MapLocation loc : spawnLocs) {
            if (rc.canBuildRobot(type, loc)) return true;
        }
        return false;
    }

    private static int estimateFrontierDemand(RobotController rc, MapInfo[] nearby) throws GameActionException {
        int demand = 0;
        for (MapInfo info : nearby) {
            if (info.isWall() || info.hasRuin()) continue;
            PaintType p = info.getPaint();
            if (!(p == PaintType.EMPTY || p.isEnemy())) continue;
            MapLocation loc = info.getMapLocation();
            for (Direction d : RobotPlayer.DIRS) {
                MapLocation adj = loc.add(d);
                if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) {
                    demand++;
                    break;
                }
            }
        }
        return demand;
    }

    private static int countEnemyPaint(MapInfo[] nearby) {
        int c = 0;
        for (MapInfo info : nearby) if (info.getPaint().isEnemy()) c++;
        return c;
    }

    private static MapLocation nearestPressurePoint(MapLocation me, RobotInfo[] enemies, MapInfo[] nearby) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            int d = me.distanceSquaredTo(e.location);
            if (d < bd) {
                bd = d;
                best = e.location;
            }
        }
        for (MapInfo info : nearby) {
            if (!info.getPaint().isEnemy()) continue;
            int d = me.distanceSquaredTo(info.getMapLocation());
            if (d < bd) {
                bd = d;
                best = info.getMapLocation();
            }
        }
        return best;
    }
}


