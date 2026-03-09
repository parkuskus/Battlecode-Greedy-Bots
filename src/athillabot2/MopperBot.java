package athillabot2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class MopperBot {
    private static final int PAINT_REFILL_THRESHOLD = 22;

    private static MapLocation refillTarget = null;
    private static MapLocation hotspot = null;
    private static int hotspotRound = -1000;

    public static void run(RobotController rc) throws GameActionException {
        consumeMessages(rc);

        if (rc.getPaint() < PAINT_REFILL_THRESHOLD) {
            if (handleRefill(rc)) {
                return;
            }
        } else {
            refillTarget = null;
        }

        if (tryMopSwing(rc)) {
            retreatFromEnemy(rc);
            broadcastEnemyCluster(rc);
            return;
        }

        tryTransferPaintToAlly(rc);
        tryMopEnemyPaint(rc);

        if (!movePatrol(rc)) {
            Navigation.moveRandom(rc);
        }

        if (!tryMopSwing(rc)) {
            tryMopEnemyPaint(rc);
        }
        broadcastEnemyCluster(rc);
    }

    private static void consumeMessages(RobotController rc) throws GameActionException {
        Communication.processMessages(rc, (type, loc, extra, senderID, round) -> {
            if (type == Communication.MSG_ENEMY_CLUSTER || type == Communication.MSG_ENEMY_TOWER) {
                hotspot = loc;
                hotspotRound = round;
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
        return true;
    }

    private static boolean tryMopSwing(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return false;
        }

        Direction[] candidates = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        Direction bestDir = null;
        int bestHits = 0;

        for (Direction dir : candidates) {
            if (!rc.canMopSwing(dir)) {
                continue;
            }
            int hits = countSwingTargets(rc, dir);
            if (hits > bestHits) {
                bestHits = hits;
                bestDir = dir;
            }
        }

        if (bestDir != null && bestHits > 0) {
            rc.mopSwing(bestDir);
            return true;
        }
        return false;
    }

    private static int countSwingTargets(RobotController rc, Direction dir) throws GameActionException {
        int count = 0;
        MapLocation myLoc = rc.getLocation();

        Direction left = dir.rotateLeft().rotateLeft();
        Direction right = dir.rotateRight().rotateRight();

        MapLocation s1 = myLoc.add(dir);
        MapLocation s2 = s1.add(dir);

        MapLocation[] tiles = {
            s1, s1.add(left), s1.add(right),
            s2, s2.add(left), s2.add(right)
        };

        for (MapLocation loc : tiles) {
            if (!rc.canSenseLocation(loc)) {
                continue;
            }
            RobotInfo enemy = rc.senseRobotAtLocation(loc);
            if (enemy != null && enemy.getTeam() != rc.getTeam()) {
                count++;
            }
        }

        return count;
    }

    private static void tryMopEnemyPaint(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        MapLocation myLoc = rc.getLocation();
        MapInfo[] tiles = rc.senseNearbyMapInfos();

        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo tile : tiles) {
            MapLocation loc = tile.getMapLocation();
            int dist = myLoc.distanceSquaredTo(loc);
            if (dist > 2) {
                continue;
            }

            if (!tile.getPaint().isEnemy()) {
                continue;
            }

            int score = 10 - dist;
            if (score > bestScore && rc.canAttack(loc)) {
                bestScore = score;
                best = loc;
            }
        }

        if (best != null) {
            rc.attack(best);
        }
    }

    private static void tryTransferPaintToAlly(RobotController rc) throws GameActionException {
        if (!rc.isActionReady() || rc.getPaint() < 35) {
            return;
        }

        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        RobotInfo neediest = null;
        double lowestRatio = 0.55;

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                continue;
            }
            double ratio = (double) ally.getPaintAmount() / ally.getType().paintCapacity;
            if (ratio < lowestRatio) {
                lowestRatio = ratio;
                neediest = ally;
            }
        }

        if (neediest == null) {
            return;
        }

        int capacityLeft = neediest.getType().paintCapacity - neediest.getPaintAmount();
        int amount = Math.min(capacityLeft, rc.getPaint() / 2);
        if (amount > 0 && rc.canTransferPaint(neediest.getLocation(), amount)) {
            rc.transferPaint(neediest.getLocation(), amount);
        }
    }

    private static boolean movePatrol(RobotController rc) throws GameActionException {
        if (hotspot != null && rc.getRoundNum() - hotspotRound <= 60) {
            if (Navigation.moveToward(rc, hotspot)) {
                return true;
            }
        }

        if (Navigation.moveTowardEnemyPaint(rc)) {
            return true;
        }

        if (Navigation.moveTowardUnpainted(rc)) {
            return true;
        }

        return false;
    }

    private static void retreatFromEnemy(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) {
            return;
        }

        MapLocation nearest = enemies[0].getLocation();
        int bestDist = rc.getLocation().distanceSquaredTo(nearest);
        for (RobotInfo enemy : enemies) {
            int dist = rc.getLocation().distanceSquaredTo(enemy.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                nearest = enemy.getLocation();
            }
        }

        Direction retreatDir = rc.getLocation().directionTo(nearest).opposite();
        if (rc.canMove(retreatDir)) {
            rc.move(retreatDir);
            return;
        }

        Navigation.moveRandom(rc);
    }

    private static void broadcastEnemyCluster(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length < 3) {
            return;
        }

        MapLocation anchor = enemies[0].getLocation();
        int msg = Communication.encode(Communication.MSG_ENEMY_CLUSTER, anchor, enemies.length);
        Communication.broadcastToNearbyAllies(rc, msg);
    }
}
