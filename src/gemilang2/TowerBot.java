package gemilang2;

import battlecode.common.*;

public class TowerBot {

    private static int spawnCount = 0;
    private static MapLocation[] spawnLocs;
    private static boolean hasBroadcast = false;

    private static final GreedyCore.Metric METRIC = new GreedyCore.Metric();

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();
        GreedyCore.startTurn(rc, METRIC);

        if (spawnLocs == null) {
            spawnLocs = rc.getAllLocationsWithinRadiusSquared(me, GameConstants.BUILD_ROBOT_RADIUS_SQUARED);
        }

        Messaging.readMessages();
        Messaging.registerTower(me, rc.getType());

        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        MapInfo[] nearby = rc.senseNearbyMapInfos(-1);
        METRIC.enemyPaintSeen += countEnemyPaint(nearby);

        attackBest(rc, enemies);
        upgradeIfAffordable(rc, round);

        if (!hasBroadcast && rc.canBroadcastMessage()) {
            rc.broadcastMessage(Messaging.encodeTowerBuilt(me, rc.getType()));
            hasBroadcast = true;
        }

        relayTowerList(allies);
        spawnGreedy(rc, me, round, allies, enemies, nearby);

        GreedyCore.endTurn(rc, METRIC);
        GreedyCore.traceMetric(rc, "TOWER", METRIC);
    }

    private static void attackBest(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo best = null;
        double bestScore = -9999;
        double second = -9999;
        int rejected = 0;
        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.location)) {
                rejected++;
                continue;
            }
            double score = GreedyCore.score(
                    0.5,
                    e.paintAmount > 0 ? 1.0 : 0.2,
                    e.type == UnitType.SOLDIER ? 2.3 : e.type == UnitType.SPLASHER ? 1.8 : 1.4,
                    0.6,
                    0.3,
                    e.health * 0.015,
                    0.2);
            if (score > bestScore) {
                second = bestScore;
                bestScore = score;
                best = e;
            } else if (score > second) second = score;
        }
        if (best != null) {
            GreedyCore.traceDecision(rc, "TOWER", "ATTACK", bestScore, second, rejected, Integer.toString(best.ID));
            rc.attack(best.location);
        }
    }

    private static void upgradeIfAffordable(RobotController rc, int round) throws GameActionException {
        if (round < 100 || Messaging.towerCount < 3) return;
        UnitType type = rc.getType();
        int chips = rc.getChips();
        if (type.level == 1 && chips >= 3000 && rc.canUpgradeTower(rc.getLocation()))
            rc.upgradeTower(rc.getLocation());
        else if (type.level == 2 && round > 400 && chips >= 5500 && rc.canUpgradeTower(rc.getLocation()))
            rc.upgradeTower(rc.getLocation());
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

        AllyComp comp = countAllyComp(allies);

        UnitType type = chooseUnitType(rc, me, round, comp, enemySoldiers, enemyPaint, frontier);
        if (type == null) return;

        MapLocation best = chooseSpawnLoc(rc, me, type, enemies, nearby);
        if (best == null) return;

        rc.buildRobot(type, best);
        sendTowerInfo(best);
        spawnCount++;
    }

    private static UnitType chooseUnitType(RobotController rc, MapLocation me, int round, AllyComp comp,
                                           int enemySoldiers, int enemyPaint, int frontier) {
        double[] score = new double[3];
        boolean[] feasible = new boolean[3];
        int[] dist = new int[]{0, 0, 0};
        int[] tie = new int[]{1, 2, 3};

        feasible[0] = hasBuildSlot(rc, UnitType.SOLDIER);
        feasible[1] = hasBuildSlot(rc, UnitType.SPLASHER);

        // Phase A (early): target ratio 5:1:1
        // Phase B (after map-control growth): target ratio 2:2:1
        boolean earlyPhase = round < 220 && Messaging.towerCount < 5;
        int bucket = earlyPhase ? 7 : 5;
        int total = comp.soldiers + comp.splashers + comp.moppers;
        int k = Math.max(1, (total + bucket) / bucket);

        int desiredS = (earlyPhase ? 5 : 2) * k;
        int desiredSp = (earlyPhase ? 1 : 2) * k;
        int desiredM = k;

        double deficitS = desiredS - comp.soldiers;
        double deficitSp = desiredSp - comp.splashers;
        double deficitM = desiredM - comp.moppers;

        boolean emergencyCleanup = enemyPaint >= 16 || (enemyPaint >= 12 && enemySoldiers >= 4);
        boolean allowEmergencyMopper = round >= 90 && enemyPaint >= 14 && enemySoldiers >= 3;
        boolean allowRatioMopper = deficitM > 0.0 && comp.soldiers >= 3;
        boolean allowMopper = allowEmergencyMopper || allowRatioMopper || (!earlyPhase && comp.moppers * 2 < comp.soldiers);
        feasible[2] = hasBuildSlot(rc, UnitType.MOPPER) && allowMopper;

        boolean splasherDebt = deficitSp > 0.6;
        boolean saveForSplasher = splasherDebt && !feasible[1] && rc.getPaint() < 300 && rc.getChips() >= 350;
        if (saveForSplasher && !emergencyCleanup) {
            feasible[2] = false;
            if (round >= 70 && feasible[0]) {
                GreedyCore.traceDecision(rc, "TOWER", "SPAWN_TYPE", -998.0, -998.0, 0, "SAVE_SPLASHER");
                return null;
            }
        }

        double ratioS = Math.max(-1.5, deficitS);
        double ratioSp = Math.max(-1.5, deficitSp);
        double ratioM = Math.max(-1.5, deficitM);

        score[0] = GreedyCore.score(
                frontier * 1.3,
                enemyPaint * 0.4,
                ratioS * 10.0 + (earlyPhase ? 1.2 : 0.4),
                2.2,
                0.7,
                enemySoldiers * 0.25,
                0.3);

        score[1] = GreedyCore.score(
                frontier * 1.8,
                enemyPaint * 1.8,
                ratioSp * 10.5 + (earlyPhase ? 0.6 : 2.4),
                2.3,
                1.0,
                enemySoldiers * 0.4,
                0.8);

        score[2] = GreedyCore.score(
                enemyPaint * 1.2,
                enemyPaint + enemySoldiers * 0.9,
                ratioM * 9.8 + (emergencyCleanup ? 2.0 : 0.3),
                1.3,
                0.5,
                0.35,
                1.2);

        if (feasible[1] && deficitSp > 0.2 && (comp.soldiers >= 4 || !earlyPhase)) {
            GreedyCore.traceDecision(rc, "TOWER", "SPAWN_TYPE", score[1] + 20.0, score[0], 0, UnitType.SPLASHER.name());
            return UnitType.SPLASHER;
        }

        if (feasible[2] && deficitM > 0.2 && comp.soldiers >= 4 && (!saveForSplasher || emergencyCleanup)) {
            GreedyCore.traceDecision(rc, "TOWER", "SPAWN_TYPE", score[2] + 12.0, score[0], 0, UnitType.MOPPER.name());
            return UnitType.MOPPER;
        }

        if (!feasible[0] && !feasible[1] && !feasible[2]) {
            GreedyCore.traceDecision(rc, "TOWER", "SPAWN_TYPE", -999.0, -999.0, 0, "NONE");
            return null;
        }

        int[] rejected = new int[1];
        double[] second = new double[1];
        int best = GreedyCore.pickBest(score, feasible, dist, tie, rejected, second);
        if (best < 0) return null;
        UnitType chosen = best == 0 ? UnitType.SOLDIER : best == 1 ? UnitType.SPLASHER : UnitType.MOPPER;
        GreedyCore.traceDecision(rc, "TOWER", "SPAWN_TYPE", score[best], second[0], rejected[0], chosen.name());
        return chosen;
    }

    private static MapLocation chooseSpawnLoc(RobotController rc, MapLocation me, UnitType type,
                                              RobotInfo[] enemies, MapInfo[] nearby) throws GameActionException {
        MapLocation pressure = nearestPressurePoint(me, enemies, nearby);
        MapLocation best = null;
        double bestScore = -9999;
        double second = -9999;
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

            double objective = 2.2 - distPress * 0.06;
            if (type == UnitType.SOLDIER) objective = 3.0 - distPress * 0.08;
            else if (type == UnitType.SPLASHER) objective = 2.6 - distPress * 0.07;
            double crowdPenalty = crowd * (type == UnitType.SOLDIER ? 0.18 : 0.25);

            double score = GreedyCore.score(
                    info.getPaint().isAlly() ? 0.9 : 0.5,
                    type == UnitType.SPLASHER ? 0.6 : 0.2,
                    objective,
                    type == UnitType.SOLDIER ? 1.3 : 1.0,
                    Math.max(0, 5 - crowd) * 0.2,
                    towerRisk ? 2.2 : 0.25,
                    crowdPenalty);
            if (score > bestScore) {
                second = bestScore;
                bestScore = score;
                best = loc;
            } else if (score > second) second = score;
        }

        if (best != null) {
            GreedyCore.traceDecision(rc, "TOWER", "SPAWN_LOC", bestScore, second, rejected, GreedyCore.locLabel(best));
        }
        return best;
    }

    private static void sendTowerInfo(MapLocation target) throws GameActionException {
        int sent = 0;
        for (int i = 0; i < Messaging.towerCount && sent < 3; i++) {
            if (Messaging.trySendMessage(target, Messaging.encodeTowerBuilt(Messaging.knownTowers[i], Messaging.knownTypes[i]))) sent++;
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







