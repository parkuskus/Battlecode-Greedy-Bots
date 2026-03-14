package botathilla5_2;

import battlecode.common.*;

/**
 * botathilla5_2 Tower:
 * - Early ratio 5:1:1, mid/late transition to 2:2:1
 * - Splasher reserve hold when deficit exists but resources are not ready
 * - Spawn and location selection use greedy argmax over feasible candidates
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

        if (!hasBroadcast && Messaging.tryBroadcastMessage(Messaging.encodeTowerBuilt(me, rc.getType()))) {
            hasBroadcast = true;
        }

        relayTowerList(allies);
        spawnGreedy(rc, me, round, allies, enemies, nearby);
        upgradeIfAffordable(rc, round, enemies, nearby);

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

    private static void upgradeIfAffordable(RobotController rc, int round, RobotInfo[] enemies, MapInfo[] nearby)
            throws GameActionException {
        if (!rc.isActionReady()) return;
        if (round < 150 || Messaging.towerCount < 3) return;

        int pressure = countEnemyPaint(nearby) + countEnemySoldiers(enemies) * 2;
        if (pressure > 14) return;

        UnitType type = rc.getType();
        int chips = rc.getChips();
        if (type.level == 1 && chips >= 3600 && rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
        } else if (type.level == 2 && round > 460 && chips >= 6200 && rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
        }
    }

    private static void relayTowerList(RobotInfo[] allies) throws GameActionException {
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType()) continue;
            int sent = 0;
            for (int i = 0; i < Messaging.towerCount && sent < 3; i++) {
                if (Messaging.trySendMessage(ally.location,
                        Messaging.encodeTowerBuilt(Messaging.knownTowers[i], Messaging.knownTypes[i]))) {
                    sent++;
                } else {
                    break;
                }
            }
        }
    }

    private static void spawnGreedy(RobotController rc, MapLocation me, int round,
                                    RobotInfo[] allies, RobotInfo[] enemies, MapInfo[] nearby) throws GameActionException {
        if (!rc.isActionReady()) return;

        int enemySoldiers = countEnemySoldiers(enemies);
        int enemyPaint = countEnemyPaint(nearby);
        int frontier = estimateFrontierDemand(rc, nearby);

        AllyComp comp = countAllyComp(allies);
        UnitType chosen = chooseUnitType(rc, me, round, comp, enemySoldiers, enemyPaint, frontier);
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
        if (chosen == UnitType.SOLDIER) {
            soldierSpawns++;
        } else if (chosen == UnitType.SPLASHER) {
            splasherSpawns++;
            if (firstSplasherRound < 0) firstSplasherRound = round;
        } else {
            mopperSpawns++;
        }

        sendTowerInfo(loc);
    }

    private static UnitType chooseUnitType(RobotController rc, MapLocation me, int round, AllyComp comp,
                                           int enemySoldiers, int enemyPaint, int frontier) {
        boolean[] feasible = new boolean[3];
        double[] score = new double[3];
        int[] dist = new int[]{0, 0, 0};
        int[] tie = new int[]{1, 2, 3};

        feasible[0] = hasBuildSlot(rc, UnitType.SOLDIER);
        feasible[1] = hasBuildSlot(rc, UnitType.SPLASHER);
        feasible[2] = hasBuildSlot(rc, UnitType.MOPPER);

        int total = Math.max(1, comp.soldiers + comp.splashers + comp.moppers + 1);
        boolean midLate = round >= 180 || Messaging.towerCount >= 4 || spawnCount >= 18;

        double soldierRatio = midLate ? (2.0 / 5.0) : (5.0 / 7.0);
        double splasherRatio = midLate ? (2.0 / 5.0) : (1.0 / 7.0);
        double mopperRatio = 1.0 - soldierRatio - splasherRatio;

        double targetSoldier = total * soldierRatio;
        double targetSplasher = total * splasherRatio;
        double targetMopper = total * mopperRatio;

        double defSoldier = targetSoldier - comp.soldiers;
        double defSplasher = targetSplasher - comp.splashers;
        double defMopper = targetMopper - comp.moppers;

        double needSoldier = Math.max(0.0, defSoldier);
        double needSplasher = Math.max(0.0, defSplasher);
        double needMopper = Math.max(0.0, defMopper);
        double overSoldier = Math.max(0.0, -defSoldier);
        double overSplasher = Math.max(0.0, -defSplasher);
        double overMopper = Math.max(0.0, -defMopper);

        boolean emergencyCleanup = enemySoldiers >= 3 || enemyPaint >= 12
                || (Messaging.enemyBlobAt != null && Messaging.enemyBlobRound + 3 >= round);
        boolean splasherDeficit = needSplasher > 0.35;
        boolean splasherAffordable = rc.getPaint() >= UnitType.SPLASHER.paintCost
                && rc.getChips() >= UnitType.SPLASHER.moneyCost;
        boolean saveForSplasher = midLate && splasherDeficit && !splasherAffordable;

        if (saveForSplasher && !emergencyCleanup) {
            holdSplasherRounds++;
            feasible[2] = false;
        } else {
            holdSplasherRounds = 0;
        }

        score[0] = GreedyCore.score(
                frontier * 1.2,
                enemyPaint * 0.35,
                (midLate ? 4.2 : 5.2) + needSoldier * 2.0,
                2.0,
                0.8,
                enemySoldiers * 0.28,
                0.35 + overSoldier * 0.8);

        score[1] = GreedyCore.score(
                frontier * 1.7 + enemyPaint * 0.8,
                enemyPaint * 1.8,
                (midLate ? 5.4 : 3.1) + needSplasher * 2.8 + (round > 260 ? 0.6 : 0.0),
                2.6,
                1.2,
                enemySoldiers * 0.45,
                0.9 + overSplasher * 0.7);

        score[2] = GreedyCore.score(
                enemyPaint * 1.1,
                enemyPaint + enemySoldiers * 0.8,
                (emergencyCleanup ? 4.0 : 1.5) + needMopper * 1.5,
                1.3,
                0.8,
                0.35,
                1.0 + overMopper * 0.5);

        if (saveForSplasher && !splasherAffordable) score[0] -= (holdSplasherRounds >= 2 ? 1.8 : 0.8);

        if (!midLate && round >= 70 && splasherSpawns == 0 && splasherAffordable) {
            score[1] += 5.0;
        }

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

    private static MapLocation chooseSpawnLoc(RobotController rc, MapLocation me, UnitType type,
                                              RobotInfo[] enemies, MapInfo[] nearby) throws GameActionException {
        MapLocation pressure = nearestPressurePoint(me, enemies, nearby);
        MapLocation best = null;
        double bestScore = -1e9;
        double second = -1e9;
        int rejected = 0;

        for (MapLocation loc : spawnLocs) {
            if (Clock.getBytecodesLeft() < 2400) break;
            if (!rc.canBuildRobot(type, loc)) {
                rejected++;
                continue;
            }
            MapInfo info = rc.senseMapInfo(loc);
            int crowd = rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam).length;
            int distPress = pressure == null ? 16 : loc.distanceSquaredTo(pressure);
            boolean towerRisk = Nav.inEnemyTowerRange(loc, enemies);

            double objective = type == UnitType.SPLASHER ? 2.7 - distPress * 0.09 : 2.2 - distPress * 0.07;
            double sc = GreedyCore.score(
                    info.getPaint().isAlly() ? 0.8 : 0.5,
                    type == UnitType.SPLASHER ? 0.7 : type == UnitType.MOPPER ? 0.6 : 0.2,
                    objective,
                    type == UnitType.SOLDIER ? 1.3 : 1.0,
                    Math.max(0, 5 - crowd) * 0.2,
                    towerRisk ? 2.1 : 0.2,
                    crowd * 0.24);
            if (sc > bestScore) {
                second = bestScore;
                bestScore = sc;
                best = loc;
            } else if (sc > second) {
                second = sc;
            }
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
                    Messaging.encodeTowerBuilt(Messaging.knownTowers[i], Messaging.knownTypes[i]))) {
                sent++;
            } else {
                break;
            }
        }
    }

    private static UnitType fallbackUnitType(RobotController rc, int enemySoldiers, int enemyPaint) {
        if (enemySoldiers >= 2 || enemyPaint >= 10) {
            if (hasBuildSlot(rc, UnitType.MOPPER)) return UnitType.MOPPER;
            if (hasBuildSlot(rc, UnitType.SOLDIER)) return UnitType.SOLDIER;
            if (hasBuildSlot(rc, UnitType.SPLASHER)) return UnitType.SPLASHER;
        } else {
            if (hasBuildSlot(rc, UnitType.SOLDIER)) return UnitType.SOLDIER;
            if (hasBuildSlot(rc, UnitType.SPLASHER)) return UnitType.SPLASHER;
            if (hasBuildSlot(rc, UnitType.MOPPER)) return UnitType.MOPPER;
        }
        return null;
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
            if (Clock.getBytecodesLeft() < 2600) break;
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

    private static int countEnemySoldiers(RobotInfo[] enemies) {
        int c = 0;
        for (RobotInfo e : enemies) if (e.type == UnitType.SOLDIER) c++;
        return c;
    }

    private static MapLocation nearestPressurePoint(MapLocation me, RobotInfo[] enemies, MapInfo[] nearby) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;

        if (Messaging.enemyBlobAt != null && Messaging.enemyBlobRound + 4 >= RobotPlayer.rc.getRoundNum()) {
            best = Messaging.enemyBlobAt;
            bd = me.distanceSquaredTo(best);
        }

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

    private static AllyComp countAllyComp(RobotInfo[] allies) {
        int sol = 0, spl = 0, mop = 0;
        for (RobotInfo a : allies) {
            if (a.type == UnitType.SOLDIER) sol++;
            else if (a.type == UnitType.SPLASHER) spl++;
            else if (a.type == UnitType.MOPPER) mop++;
        }
        return new AllyComp(sol, spl, mop);
    }

    private static final class AllyComp {
        final int soldiers;
        final int splashers;
        final int moppers;

        AllyComp(int soldiers, int splashers, int moppers) {
            this.soldiers = soldiers;
            this.splashers = splashers;
            this.moppers = moppers;
        }
    }
}






