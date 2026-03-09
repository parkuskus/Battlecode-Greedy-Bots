package athillabot2;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class TowerBot {
    private static final int EARLY_ROUND_END = 220;
    private static final int MID_ROUND_END = 900;

    public static void run(RobotController rc) throws GameActionException {
        readMessages(rc);
        attackBestTarget(rc);
        spawnGreedy(rc);
    }

    private static void attackBestTarget(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) {
            return;
        }

        MapLocation myLoc = rc.getLocation();
        RobotInfo bestTarget = null;
        int bestScore = Integer.MIN_VALUE;

        for (RobotInfo enemy : enemies) {
            MapLocation loc = enemy.getLocation();
            if (!rc.canAttack(loc)) {
                continue;
            }

            int dist = myLoc.distanceSquaredTo(loc);
            int score = 0;
            if (enemy.getType().isTowerType()) {
                score += 1000;
            }
            score += (200 - dist);

            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }

        if (bestTarget != null && rc.canAttack(bestTarget.getLocation())) {
            rc.attack(bestTarget.getLocation());
            return;
        }

        if (enemies.length >= 2 && rc.canAttack(null)) {
            rc.attack(null);
        }
    }

    private static void spawnGreedy(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        int round = rc.getRoundNum();
        int chips = rc.getChips();
        int paint = rc.getPaint();
        int nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length;

        UnitType[] plan;
        if (nearbyEnemies >= 4) {
            plan = new UnitType[] {UnitType.MOPPER, UnitType.SOLDIER, UnitType.SPLASHER};
        } else if (round < EARLY_ROUND_END) {
            plan = new UnitType[] {UnitType.SOLDIER, UnitType.MOPPER, UnitType.SOLDIER};
        } else if (round < MID_ROUND_END) {
            if (chips >= 700 && paint >= 350) {
                plan = new UnitType[] {UnitType.SPLASHER, UnitType.SOLDIER, UnitType.MOPPER};
            } else {
                plan = new UnitType[] {UnitType.SOLDIER, UnitType.MOPPER, UnitType.SPLASHER};
            }
        } else {
            if (paint >= 450) {
                plan = new UnitType[] {UnitType.SPLASHER, UnitType.SPLASHER, UnitType.MOPPER};
            } else {
                plan = new UnitType[] {UnitType.MOPPER, UnitType.SOLDIER, UnitType.SPLASHER};
            }
        }

        for (UnitType type : plan) {
            if (trySpawn(rc, type)) {
                return;
            }
        }
    }

    private static boolean trySpawn(RobotController rc, UnitType type) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation enemyTower = Navigation.findNearestEnemyTower(rc);

        MapLocation bestSpawnLoc = null;
        int bestScore = Integer.MIN_VALUE;

        for (battlecode.common.Direction dir : RobotPlayer.directions) {
            MapLocation spawnLoc = myLoc.add(dir);
            if (!rc.canBuildRobot(type, spawnLoc)) {
                continue;
            }

            int score = 0;
            MapInfo info = rc.senseMapInfo(spawnLoc);
            PaintType paint = info.getPaint();

            if (paint.isAlly()) {
                score += 8;
            } else if (paint == PaintType.EMPTY) {
                score += 4;
            } else {
                score -= 2;
            }

            if (enemyTower != null) {
                int dist = spawnLoc.distanceSquaredTo(enemyTower);
                score += (100 - Math.min(100, dist));
            }

            score += RobotPlayer.rng.nextInt(5);

            if (score > bestScore) {
                bestScore = score;
                bestSpawnLoc = spawnLoc;
            }
        }

        if (bestSpawnLoc != null) {
            rc.buildRobot(type, bestSpawnLoc);
            return true;
        }
        return false;
    }

    private static void readMessages(RobotController rc) throws GameActionException {
        Communication.processMessages(rc, (type, loc, extra, senderID, round) -> {
            if (type == Communication.MSG_ENEMY_CLUSTER) {
                rc.setIndicatorString("cluster " + extra + " @" + loc);
            } else if (type == Communication.MSG_RUIN_FOUND) {
                rc.setIndicatorString("ruin @" + loc);
            }
        });
    }
}
