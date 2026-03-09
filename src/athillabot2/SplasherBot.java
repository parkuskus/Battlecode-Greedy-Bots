package athillabot2;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class SplasherBot {
    private static final int PAINT_REFILL_THRESHOLD = 70;
    private static final int ATTACK_COST = 50;

    private static MapLocation refillTarget = null;
    private static MapLocation hotspot = null;
    private static int hotspotRound = -1000;

    public static void run(RobotController rc) throws GameActionException {
        consumeMessages(rc);

        int myPaint = rc.getPaint();
        if (myPaint < PAINT_REFILL_THRESHOLD) {
            if (handleRefill(rc)) {
                return;
            }
        } else {
            refillTarget = null;
        }

        if (myPaint >= ATTACK_COST) {
            tryBestAttack(rc);
        }

        if (!moveGreedy(rc)) {
            Navigation.moveRandom(rc);
        }

        if (rc.getPaint() >= ATTACK_COST) {
            tryBestAttack(rc);
        }
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

    private static boolean moveGreedy(RobotController rc) throws GameActionException {
        if (hotspot != null && rc.getRoundNum() - hotspotRound <= 70) {
            if (Navigation.moveToward(rc, hotspot)) {
                return true;
            }
        }

        if (Navigation.moveTowardEnemyPaint(rc)) {
            return true;
        }

        return Navigation.moveTowardUnpainted(rc);
    }

    private static void tryBestAttack(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        MapLocation myLoc = rc.getLocation();
        MapLocation[] candidates = rc.getAllLocationsWithinRadiusSquared(myLoc, 4);

        MapLocation bestCenter = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapLocation center : candidates) {
            if (!rc.canAttack(center)) {
                continue;
            }

            int score = evaluateAttackCenter(rc, center);
            if (score > bestScore) {
                bestScore = score;
                bestCenter = center;
            }
        }

        if (bestCenter != null && bestScore >= 8) {
            rc.attack(bestCenter);
        }
    }

    private static int evaluateAttackCenter(RobotController rc, MapLocation center) throws GameActionException {
        int score = 0;
        MapLocation[] splashTiles = rc.getAllLocationsWithinRadiusSquared(center, 4);

        for (MapLocation tileLoc : splashTiles) {
            if (!rc.canSenseLocation(tileLoc)) {
                continue;
            }

            MapInfo info = rc.senseMapInfo(tileLoc);
            if (info.isWall() || info.hasRuin()) {
                continue;
            }

            PaintType paint = info.getPaint();
            int distToCenter = center.distanceSquaredTo(tileLoc);

            if (paint == PaintType.EMPTY) {
                score += 2;
            } else if (paint.isEnemy()) {
                if (distToCenter <= 2) {
                    score += 5;
                } else {
                    score += 1;
                }
            } else {
                score -= 1;
            }

            RobotInfo occupant = rc.senseRobotAtLocation(tileLoc);
            if (occupant != null && occupant.getTeam() != rc.getTeam() && occupant.getType().isTowerType()) {
                score += 8;
            }
        }

        return score;
    }
}
