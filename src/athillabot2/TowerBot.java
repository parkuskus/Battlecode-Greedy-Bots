package athillabot2;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class TowerBot {
    private static final int EARLY_ROUND_END = 260;
    private static final int MID_ROUND_END = 900;

    private static int spawnCounter = 0;
    private static int urgentMopperRound = -1000;
    private static MapLocation urgentMopperLoc = null;

    private static MapLocation enemyTowerHint = null;
    private static int enemyTowerHintRound = -1000;

    public static void run(RobotController rc) throws GameActionException {
        Navigation.updateSymmetryModel(rc);
        readMessages(rc);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        tryUpgrade(rc, allies, enemies);
        attackBestTarget(rc, enemies);
        requestUrgentMopperIfNeeded(rc, allies);
        spawnGreedy(rc, allies, enemies);
    }

    private static void readMessages(RobotController rc) throws GameActionException {
        Communication.processMessages(rc, (type, loc, extra, senderID, round) -> {
            if (type == Communication.MSG_ENEMY_TOWER_SEEN) {
                enemyTowerHint = loc;
                enemyTowerHintRound = round;
            } else if (type == Communication.MSG_URGENT_MOPPER) {
                urgentMopperLoc = loc;
                urgentMopperRound = round;
            }
        });
    }

    private static void tryUpgrade(RobotController rc, RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        if (!rc.canUpgradeTower(rc.getLocation())) {
            return;
        }

        UnitType base = rc.getType().getBaseType();
        int chips = rc.getChips();

        int threshold = (base == UnitType.LEVEL_ONE_PAINT_TOWER) ? 2600 : 3400;
        if (enemies.length >= 3) {
            threshold += 800;
        }

        if (chips >= threshold && allies.length >= 2) {
            rc.upgradeTower(rc.getLocation());
        }
    }

    private static void attackBestTarget(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo best = null;
        int bestScore = Integer.MIN_VALUE;

        for (RobotInfo enemy : enemies) {
            MapLocation loc = enemy.getLocation();
            if (!rc.canAttack(loc)) {
                continue;
            }

            int score = 0;
            if (enemy.getType() == UnitType.SOLDIER) {
                score += 120;
            } else if (enemy.getType() == UnitType.MOPPER) {
                score += 95;
            } else if (enemy.getType() == UnitType.SPLASHER) {
                score += 85;
            } else if (enemy.getType().isTowerType()) {
                score += 70;
            }

            score += (500 - enemy.getHealth());
            score -= rc.getLocation().distanceSquaredTo(loc);

            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }

        if (best != null) {
            rc.attack(best.getLocation());
            return;
        }

        if (enemies.length >= 2 && rc.canAttack(null)) {
            rc.attack(null);
        }
    }

    private static void requestUrgentMopperIfNeeded(RobotController rc, RobotInfo[] allies) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation enemyPaintLoc = null;

        for (MapInfo tile : nearbyTiles) {
            if (Clock.getBytecodesLeft() < 2500) {
                break;
            }
            if (tile.getPaint().isEnemy()) {
                enemyPaintLoc = tile.getMapLocation();
                break;
            }
        }

        if (enemyPaintLoc == null) {
            return;
        }

        for (RobotInfo ally : allies) {
            if (ally.getType() != UnitType.MOPPER) {
                continue;
            }
            if (!rc.canSendMessage(ally.getLocation())) {
                continue;
            }
            int msg = Communication.encode(Communication.MSG_URGENT_MOPPER, enemyPaintLoc);
            rc.sendMessage(ally.getLocation(), msg);
            break;
        }
    }

    private static void spawnGreedy(RobotController rc, RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        int round = rc.getRoundNum();
        int paint = rc.getPaint();
        int chips = rc.getChips();
        int enemySoldiers = countEnemySoldiers(enemies);

        boolean urgentMopper = urgentMopperLoc != null && round - urgentMopperRound <= 30;

        if (enemySoldiers >= 2 || urgentMopper) {
            if (trySpawn(rc, UnitType.MOPPER, urgentMopperLoc)) {
                return;
            }
        }

        if (round < 400 && rc.getNumberTowers() < 6) {
            if (trySpawn(rc, UnitType.SOLDIER, enemyDirectionTarget(rc))) {
                spawnCounter++;
                return;
            }
        }

        UnitType[] phasePlan = choosePhasePlan(round, paint, chips);
        MapLocation directionalTarget = enemyDirectionTarget(rc);

        for (UnitType type : phasePlan) {
            if (type == UnitType.SPLASHER && paint < 220) {
                continue;
            }
            if (trySpawn(rc, type, directionalTarget)) {
                spawnCounter++;
                return;
            }
        }
    }

    private static UnitType[] choosePhasePlan(int round, int paint, int chips) {
        if (round < EARLY_ROUND_END) {
            int slot = spawnCounter % 5;
            if (slot == 3) {
                return new UnitType[] {UnitType.SPLASHER, UnitType.SOLDIER, UnitType.MOPPER};
            }
            if (slot == 4) {
                return new UnitType[] {UnitType.MOPPER, UnitType.SOLDIER, UnitType.SPLASHER};
            }
            return new UnitType[] {UnitType.SOLDIER, UnitType.MOPPER, UnitType.SPLASHER};
        }

        if (round < MID_ROUND_END) {
            int slot = spawnCounter % 4;
            if (slot == 0 || slot == 2) {
                return new UnitType[] {UnitType.SOLDIER, UnitType.SPLASHER, UnitType.MOPPER};
            }
            if (slot == 1) {
                return new UnitType[] {UnitType.SPLASHER, UnitType.SOLDIER, UnitType.MOPPER};
            }
            return new UnitType[] {UnitType.MOPPER, UnitType.SOLDIER, UnitType.SPLASHER};
        }

        if (paint >= 450 && chips >= 1200) {
            return new UnitType[] {UnitType.SPLASHER, UnitType.SPLASHER, UnitType.MOPPER, UnitType.SOLDIER};
        }
        return new UnitType[] {UnitType.SOLDIER, UnitType.MOPPER, UnitType.SPLASHER};
    }

    private static MapLocation enemyDirectionTarget(RobotController rc) {
        if (enemyTowerHint != null && rc.getRoundNum() - enemyTowerHintRound <= 80) {
            return enemyTowerHint;
        }

        MapLocation symmetryTarget = Navigation.predictEnemyFromAlly(rc, rc.getLocation());
        if (symmetryTarget != null) {
            return symmetryTarget;
        }

        return new MapLocation(rc.getMapWidth() - 1 - rc.getLocation().x, rc.getMapHeight() - 1 - rc.getLocation().y);
    }

    private static boolean trySpawn(RobotController rc, UnitType type, MapLocation directionalTarget) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation bestLoc = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : RobotPlayer.directions) {
            if (Clock.getBytecodesLeft() < 2300) {
                break;
            }

            MapLocation loc = myLoc.add(dir);
            if (!rc.canBuildRobot(type, loc)) {
                continue;
            }

            int score = 0;
            MapInfo info = rc.senseMapInfo(loc);
            PaintType tilePaint = info.getPaint();

            if (tilePaint.isAlly()) {
                score += 8;
            } else if (tilePaint == PaintType.EMPTY) {
                score += 4;
            } else {
                score -= 4;
            }

            int edgeDist = Math.min(Math.min(loc.x, rc.getMapWidth() - 1 - loc.x), Math.min(loc.y, rc.getMapHeight() - 1 - loc.y));
            score += Math.min(4, edgeDist);

            int nearbyUnits = countAdjacentAllies(rc, loc);
            score -= nearbyUnits * 4;

            if (directionalTarget != null) {
                score -= loc.distanceSquaredTo(directionalTarget) / 2;
            }

            score += RobotPlayer.rng.nextInt(4);

            if (score > bestScore) {
                bestScore = score;
                bestLoc = loc;
            }
        }

        if (bestLoc != null) {
            rc.buildRobot(type, bestLoc);
            return true;
        }
        return false;
    }

    private static int countAdjacentAllies(RobotController rc, MapLocation loc) throws GameActionException {
        int count = 0;
        RobotInfo[] allies = rc.senseNearbyRobots(loc, 2, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) {
                count++;
            }
        }
        return count;
    }

    private static int countEnemySoldiers(RobotInfo[] enemies) {
        int count = 0;
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.SOLDIER) {
                count++;
            }
        }
        return count;
    }
}
