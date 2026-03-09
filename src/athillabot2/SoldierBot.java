package athillabot2;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class SoldierBot {
    private static final int PAINT_REFILL_THRESHOLD = 45;

    private static MapLocation refillTarget = null;
    private static MapLocation ruinTarget = null;
    private static MapLocation enemyTowerHint = null;
    private static int enemyTowerHintRound = -1000;
    private static int lastBroadcastRound = -1000;

    public static void run(RobotController rc) throws GameActionException {
        consumeMessages(rc);

        if (rc.getPaint() < PAINT_REFILL_THRESHOLD) {
            if (handleRefill(rc)) {
                return;
            }
        } else {
            refillTarget = null;
        }

        tryUpgradeNearbyTower(rc);

        if (handleRuinObjective(rc)) {
            return;
        }

        if (tryAttackEnemyTower(rc)) {
            return;
        }

        tryCompleteResourcePattern(rc);
        paintCurrentTile(rc);

        if (!Navigation.moveTowardEnemyPaint(rc)) {
            if (!Navigation.moveTowardUnpainted(rc)) {
                Navigation.moveRandom(rc);
            }
        }

        paintCurrentTile(rc);
        broadcastFindings(rc);
    }

    private static void consumeMessages(RobotController rc) throws GameActionException {
        Communication.processMessages(rc, (type, loc, extra, senderID, round) -> {
            if (type == Communication.MSG_ENEMY_TOWER) {
                enemyTowerHint = loc;
                enemyTowerHintRound = round;
            }
            if (type == Communication.MSG_RUIN_FOUND && ruinTarget == null) {
                ruinTarget = loc;
            }
        });
    }

    private static boolean handleRefill(RobotController rc) throws GameActionException {
        if (refillTarget == null) {
            refillTarget = Navigation.findNearestAllyTower(rc);
        }

        if (refillTarget == null) {
            Navigation.moveRandom(rc);
            return true;
        }

        if (rc.getLocation().distanceSquaredTo(refillTarget) <= 2) {
            int need = rc.getType().paintCapacity - rc.getPaint();
            if (need > 0 && rc.canTransferPaint(refillTarget, -need)) {
                rc.transferPaint(refillTarget, -need);
                refillTarget = null;
            }
            return true;
        }

        Navigation.moveToward(rc, refillTarget);
        paintCurrentTile(rc);
        return true;
    }

    private static boolean handleRuinObjective(RobotController rc) throws GameActionException {
        if (ruinTarget == null) {
            ruinTarget = Navigation.findNearestRuin(rc);
        }

        if (ruinTarget == null) {
            return false;
        }

        if (rc.canSenseLocation(ruinTarget)) {
            RobotInfo occupant = rc.senseRobotAtLocation(ruinTarget);
            if (occupant != null) {
                ruinTarget = null;
                return false;
            }
        }

        Navigation.moveToward(rc, ruinTarget);
        UnitType towerType = chooseTowerType(rc);

        if (rc.canMarkTowerPattern(towerType, ruinTarget)) {
            rc.markTowerPattern(towerType, ruinTarget);
        }

        for (MapInfo tile : rc.senseNearbyMapInfos(ruinTarget, 8)) {
            if (tile.getMark() == PaintType.EMPTY) {
                continue;
            }
            if (tile.getMark() == tile.getPaint()) {
                continue;
            }

            boolean useSecondary = tile.getMark() == PaintType.ALLY_SECONDARY;
            MapLocation loc = tile.getMapLocation();
            if (rc.canAttack(loc)) {
                rc.attack(loc, useSecondary);
            }
        }

        if (rc.canCompleteTowerPattern(towerType, ruinTarget)) {
            rc.completeTowerPattern(towerType, ruinTarget);
            rc.setTimelineMarker("tower built", 0, 200, 40);
            ruinTarget = null;
            return true;
        }

        return true;
    }

    private static UnitType chooseTowerType(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int money = 0;
        int paint = 0;

        for (RobotInfo ally : allies) {
            UnitType t = ally.getType();
            if (t == UnitType.LEVEL_ONE_MONEY_TOWER || t == UnitType.LEVEL_TWO_MONEY_TOWER || t == UnitType.LEVEL_THREE_MONEY_TOWER) {
                money++;
            }
            if (t == UnitType.LEVEL_ONE_PAINT_TOWER || t == UnitType.LEVEL_TWO_PAINT_TOWER || t == UnitType.LEVEL_THREE_PAINT_TOWER) {
                paint++;
            }
        }

        int round = rc.getRoundNum();
        int enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length;

        if (enemies >= 3 && round > 700) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        if (round < 400) {
            return (money <= paint) ? UnitType.LEVEL_ONE_MONEY_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return (paint <= money) ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    private static boolean tryAttackEnemyTower(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (!enemy.getType().isTowerType()) {
                continue;
            }

            enemyTowerHint = enemy.getLocation();
            enemyTowerHintRound = rc.getRoundNum();
            if (rc.canAttack(enemyTowerHint)) {
                rc.attack(enemyTowerHint);
                return true;
            }
        }

        if (enemyTowerHint != null && rc.getRoundNum() - enemyTowerHintRound <= 60) {
            if (Navigation.moveToward(rc, enemyTowerHint)) {
                return true;
            }
        }

        return false;
    }

    private static void tryUpgradeNearbyTower(RobotController rc) throws GameActionException {
        if (rc.getChips() < 600) {
            return;
        }

        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType() && rc.canUpgradeTower(ally.getLocation())) {
                rc.upgradeTower(ally.getLocation());
                return;
            }
        }
    }

    private static void tryCompleteResourcePattern(RobotController rc) throws GameActionException {
        if (rc.getChips() < 220) {
            return;
        }

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            MapLocation loc = tile.getMapLocation();
            if (rc.canCompleteResourcePattern(loc)) {
                rc.completeResourcePattern(loc);
                return;
            }
        }
    }

    private static void paintCurrentTile(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapInfo current = rc.senseMapInfo(myLoc);
        if (!current.getPaint().isAlly() && rc.canAttack(myLoc)) {
            rc.attack(myLoc);
        }
    }

    private static void broadcastFindings(RobotController rc) throws GameActionException {
        if (rc.getRoundNum() - lastBroadcastRound < 15) {
            return;
        }

        if (ruinTarget != null) {
            int msg = Communication.encode(Communication.MSG_RUIN_FOUND, ruinTarget);
            if (Communication.broadcastToNearbyAllies(rc, msg)) {
                lastBroadcastRound = rc.getRoundNum();
                return;
            }
        }

        if (enemyTowerHint != null) {
            int msg = Communication.encode(Communication.MSG_ENEMY_TOWER, enemyTowerHint);
            if (Communication.broadcastToNearbyAllies(rc, msg)) {
                lastBroadcastRound = rc.getRoundNum();
            }
        }
    }
}
