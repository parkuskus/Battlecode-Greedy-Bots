package SPAARK;

import battlecode.common.*;
import java.util.*;

public class Tower {
    // initial weights for bots
    public static final double TOW_SPAWN_SOLDIER_WEIGHT = 1.5;
    public static final double TOW_SPAWN_SPLASHER_WEIGHT = 0.2;
    public static final double TOW_SPAWN_MOPPER_WEIGHT = 1.2;
    // reduce the weight of soldiers if max towers reached
    public static final double TOW_MAXED_REDUCE_SOLDIER_WEIGHT = 1;

    public static int spawnedSoldiers = 0;
    public static int spawnedSplashers = 0;
    public static int spawnedMoppers = 0;
    public static int spawnedRobots = 0;
    public static int lastSpawn = -1;

    public static double doubleSpawnedSoldiers = 0;
    public static double doubleSpawnedSplashers = 0;
    public static double doubleSpawnedMoppers = 0;

    public static int cnt = 0;

    public static MapLocation[] spawnLocs;
    public static int level;

    public static void init() throws Exception {
        spawnLocs = new MapLocation[] {
                G.me.add(Direction.NORTH),
                G.me.add(Direction.NORTHEAST),
                G.me.add(Direction.EAST),
                G.me.add(Direction.SOUTHEAST),
                G.me.add(Direction.SOUTH),
                G.me.add(Direction.SOUTHWEST),
                G.me.add(Direction.WEST),
                G.me.add(Direction.NORTHWEST),
                G.me.add(Direction.NORTH).add(Direction.NORTH),
                G.me.add(Direction.EAST).add(Direction.EAST),
                G.me.add(Direction.SOUTH).add(Direction.SOUTH),
                G.me.add(Direction.WEST).add(Direction.WEST),
        };
        Arrays.sort(spawnLocs,
                (MapLocation a, MapLocation b) -> a.distanceSquaredTo(G.mapCenter) - b.distanceSquaredTo(G.mapCenter));
        POI.addTower(-1, G.me, G.team, G.rc.getType());
        switch (G.rc.getType().getBaseType()) {
            case LEVEL_ONE_DEFENSE_TOWER -> DefenseTower.init();
            case LEVEL_ONE_MONEY_TOWER -> MoneyTower.init();
            case LEVEL_ONE_PAINT_TOWER -> PaintTower.init();
        }
    }

    public static void spawnBot(UnitType t) throws Exception {
        switch (t) {
            case UnitType.MOPPER:
                for (MapLocation loc : spawnLocs) {
                    if (G.rc.canBuildRobot(UnitType.MOPPER, loc)) {
                        G.rc.buildRobot(UnitType.MOPPER, loc);
                        spawnedRobots++;
                        // spawnedMoppers++;
                        break;
                    }
                }
                break;
            case UnitType.SPLASHER:
                for (MapLocation loc : spawnLocs) {
                    if (G.rc.canBuildRobot(UnitType.SPLASHER, loc)) {
                        G.rc.buildRobot(UnitType.SPLASHER, loc);
                        spawnedRobots++;
                        // spawnedSplashers++;
                        break;
                    }
                }
                break;
            case UnitType.SOLDIER:
                for (MapLocation loc : spawnLocs) {
                    if (G.rc.canBuildRobot(UnitType.SOLDIER, loc)) {
                        G.rc.buildRobot(UnitType.SOLDIER, loc);
                        spawnedRobots++;
                        // spawnedSoldiers++;
                        break;
                    }
                }
                break;
            default:
                throw new Exception("what are you spawning?? a tower???");
        }
    }

    public static void run() throws Exception {
        // general common code for all towers
        // spawning
        UnitType trying = UnitType.SPLASHER;
        // int mod = 7;
        // int area = G.mapHeight * G.mapWidth;

        double soldierWeight = TOW_SPAWN_SOLDIER_WEIGHT;
        double splasherWeight = TOW_SPAWN_SPLASHER_WEIGHT;
        double mopperWeight = TOW_SPAWN_MOPPER_WEIGHT;

        // if (G.rc.getNumberTowers() < 25) {
        // for (int i = POI.numberOfTowers; --i >= 0;) {
        // if (POI.towerTeams[i] == Team.NEUTRAL) {
        // soldierWeight += 1;
        // break;
        // }
        // }
        // }
        // if (G.rc.getNumberTowers() == 25) {
        // soldierWeight -= TOW_MAXED_REDUCE_SOLDIER_WEIGHT;
        // }
        soldierWeight -= ((double) G.rc.getNumberTowers()) * 0.05;
        splasherWeight += ((double) POI.paintTowers) * 0.3;
        double sum = soldierWeight + splasherWeight + mopperWeight;
        soldierWeight /= sum;
        splasherWeight /= sum;
        mopperWeight /= sum;

        // G.indicatorString = new StringBuilder();
        // G.indicatorString.append(doubleSpawnedSoldiers + " " + spawnedSoldiers + " "
        // + doubleSpawnedSplashers + " "
        // + spawnedSplashers + " " + doubleSpawnedMoppers + " " + spawnedMoppers + "
        // ");

        double soldier = doubleSpawnedSoldiers + soldierWeight - spawnedSoldiers;
        double splasher = doubleSpawnedSplashers + splasherWeight - spawnedSplashers;
        double mopper = doubleSpawnedMoppers + mopperWeight - spawnedMoppers;

        if (soldier >= splasher && soldier >= mopper) {
            trying = UnitType.SOLDIER;
        } else if (mopper >= splasher) {
            trying = UnitType.MOPPER;
        } else {
            trying = UnitType.SPLASHER;
        }

        // IMPORTANT: this prioritizes mopper > splasher > soldier at the start
        // if (mopper >= splasher && mopper >= soldier) {
        // trying = UnitType.MOPPER;
        // } else if (splasher >= soldier) {
        // trying = UnitType.SPLASHER;
        // } else {
        // trying = UnitType.SOLDIER;
        // }

        if ((G.round < 50 || G.rc.getType().getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER) && spawnedRobots < 3) {
            trying = UnitType.SOLDIER;
        }

        // if (G.rc.getNumberTowers() == 25 || G.rc.getMoney() - trying.moneyCost >= 900
        // || G.rc.getPaint() == 1000) {
        if ((G.rc.getNumberTowers() == 25 || G.rc.getMoney() - trying.moneyCost >= 900
                && (G.round < 100 || (lastSpawn + 1 < G.round && G.allyRobots.length < 4)) || G.round < 10)) {
            switch (trying) {
                case UnitType.MOPPER:
                    for (MapLocation loc : spawnLocs) {
                        if (G.rc.canBuildRobot(UnitType.MOPPER, loc)) {
                            G.rc.buildRobot(UnitType.MOPPER, loc);
                            spawnedRobots++;
                            spawnedMoppers++;
                            doubleSpawnedSoldiers += soldierWeight;
                            doubleSpawnedSplashers += splasherWeight;
                            doubleSpawnedMoppers += mopperWeight;
                            lastSpawn = G.round;
                            break;
                        }
                    }
                    break;
                case UnitType.SPLASHER:
                    for (MapLocation loc : spawnLocs) {
                        if (G.rc.canBuildRobot(UnitType.SPLASHER, loc)) {
                            G.rc.buildRobot(UnitType.SPLASHER, loc);
                            spawnedRobots++;
                            spawnedSplashers++;
                            doubleSpawnedSoldiers += soldierWeight;
                            doubleSpawnedSplashers += splasherWeight;
                            doubleSpawnedMoppers += mopperWeight;
                            lastSpawn = G.round;
                            break;
                        }
                    }
                    break;
                case UnitType.SOLDIER:
                    for (MapLocation loc : spawnLocs) {
                        if (G.rc.canBuildRobot(UnitType.SOLDIER, loc)) {
                            G.rc.buildRobot(UnitType.SOLDIER, loc);
                            spawnedRobots++;
                            spawnedSoldiers++;
                            doubleSpawnedSoldiers += soldierWeight;
                            doubleSpawnedSplashers += splasherWeight;
                            doubleSpawnedMoppers += mopperWeight;
                            lastSpawn = G.round;
                            break;
                        }
                    }
                    break;
                default:
                    throw new Exception("what are you spawning?? a tower???");
            }
        }
        // more specialized here
        switch (G.rc.getType()) {
            case LEVEL_ONE_DEFENSE_TOWER -> {
                level = 0;
                DefenseTower.run();
            }
            case LEVEL_TWO_DEFENSE_TOWER -> {
                level = 1;
                DefenseTower.run();
            }
            case LEVEL_THREE_DEFENSE_TOWER -> {
                level = 2;
                DefenseTower.run();
            }
            case LEVEL_ONE_MONEY_TOWER -> {
                level = 0;
                MoneyTower.run();
            }
            case LEVEL_TWO_MONEY_TOWER -> {
                level = 1;
                MoneyTower.run();
            }
            case LEVEL_THREE_MONEY_TOWER -> {
                level = 2;
                MoneyTower.run();
            }
            case LEVEL_ONE_PAINT_TOWER -> {
                level = 0;
                PaintTower.run();
            }
            case LEVEL_TWO_PAINT_TOWER -> {
                level = 1;
                PaintTower.run();
            }
            case LEVEL_THREE_PAINT_TOWER -> {
                level = 2;
                PaintTower.run();
            }
            default -> throw new Exception("Challenge Complete! How Did We Get Here?");
        }
        // TODO: make required chips based on tower level
        // i really hope all money towers dont go boom
        // destroy: if (G.rc.getNumberTowers() == 25 && ((G.rc.getType().getBaseType()
        // == UnitType.LEVEL_ONE_PAINT_TOWER && G.rc.getChips() > 25000) ||
        // G.rc.getType().getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER &&
        // G.rc.getChips() > 100000)) {
        // // destroy: if (((G.rc.getType().getBaseType() ==
        // UnitType.LEVEL_ONE_PAINT_TOWER && G.rc.getChips() > 25000) ||
        // G.rc.getType().getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER &&
        // G.rc.getChips() > 50000)) {
        // int best = -1;
        // int bestDistance = 0;
        // for (int i = POI.numberOfTowers; --i >= 0;) {
        // if (POI.towerTeams[i] != G.opponentTeam) {
        // continue;
        // }
        // int distance = Motion.getChebyshevDistance(G.me, POI.towerLocs[i]);
        // if (best == -1 || distance < bestDistance) {
        // best = i;
        // bestDistance = distance;
        // }
        // }
        // MapLocation closestOpponentTower = new MapLocation(-1, -1);
        // if (best == -1) {
        // if (POI.symmetry[0]) {
        // closestOpponentTower = new MapLocation(G.mapWidth - 1 - G.me.x, G.me.y);
        // }
        // if (POI.symmetry[1]) {
        // closestOpponentTower = new MapLocation(G.me.x, G.mapHeight - 1 - G.me.y);
        // }
        // if (POI.symmetry[2]) {
        // closestOpponentTower = new MapLocation(G.mapWidth - 1 - G.me.x, G.mapHeight -
        // 1 - G.me.y);
        // }
        // break destroy;
        // }
        // else {
        // closestOpponentTower = POI.towerLocs[best];
        // }
        // boolean foundFurther = false;
        // for (int i = POI.numberOfTowers; --i >= 0;) {
        // if (POI.towerTeams[i] != G.team) {
        // continue;
        // }
        // int distance = Motion.getChebyshevDistance(closestOpponentTower,
        // POI.towerLocs[i]);
        // if (distance > bestDistance) {
        // foundFurther = true;
        // break;
        // }
        // if (Clock.getBytecodesLeft() < 2500) {
        // break;
        // }
        // }
        // if (foundFurther) {
        // break destroy;
        // }
        // // better to attack before disintegration
        // attack();
        // System.out.println("Tower " + G.rc.getID() + " disintegrated!!!!");
        // G.rc.setTimelineMarker("disintegrated", 255, 0, 0);
        // G.rc.disintegrate();
        // return;
        // }
        if (G.rc.getChips() > (G.rc.getID() < 10000 ? 20000 : G.rc.getID() * 3 - 10000) && G.lastChips < G.rc.getChips() && G.rc.getNumberTowers() >= G.lastNumberTowers && G.rc.getType().getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER) {
            if (G.rc.getRoundNum() % 5 == 0) {
                attack();
                // G.rc.setTimelineMarker("disintegrated", 255, 0, 0);
                G.rc.disintegrate();
                return;
            }
        } else {
            while (G.rc.canUpgradeTower(G.me) && G.rc.getMoney() - (level == 0 ? 2500 : 5000) >= 1000) {
                G.rc.upgradeTower(G.me);
            }
        }
        // attack after upgrading
        attack();
    }

    public static void attack() throws Exception {
        // prioritize bots with low hp, unless they have less hp then our attack power
        if (G.rc.canAttack(null)) {
            G.rc.attack(null);
        }
        // check cooldown on single target attack
        if (G.rc.canAttack(G.me)) {
            MapLocation bestEnemyLoc = null;
            int bestEnemyHp = 1000000;
            int attackStrength = G.rc.getType().attackStrength;
            for (int i = G.opponentRobots.length; --i >= 0;) {
                RobotInfo r = G.opponentRobots[i];
                // check if it's still alive
                if (G.rc.canSenseRobotAtLocation(r.location)
                        && G.me.isWithinDistanceSquared(r.location, G.rc.getType().actionRadiusSquared)) {
                    // just do lowest hp it's basically the same as having priorities and it's more
                    // gold efficient to kill moppers anyways
                    if (bestEnemyHp > attackStrength && r.health < bestEnemyHp) {
                        bestEnemyHp = r.health;
                        bestEnemyLoc = r.location;
                    } else if (r.health > bestEnemyHp && r.health <= attackStrength) {
                        bestEnemyHp = r.health;
                        bestEnemyLoc = r.location;
                    }
                }
            }
            if (bestEnemyLoc != null) {
                G.rc.attack(bestEnemyLoc);
            }
        }
    }
}
