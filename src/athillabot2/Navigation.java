package athillabot2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;

import java.util.Random;

public class Navigation {
    static final Direction[] directions = RobotPlayer.directions;
    static final Random rng = RobotPlayer.rng;

    public static boolean moveToward(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null || !rc.isMovementReady()) {
            return false;
        }

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = myLoc.directionTo(target);
        if (bestDir == Direction.CENTER) {
            return false;
        }

        Direction[] tryDirs = {
            bestDir,
            bestDir.rotateLeft(),
            bestDir.rotateRight(),
            bestDir.rotateLeft().rotateLeft(),
            bestDir.rotateRight().rotateRight(),
            bestDir.rotateLeft().rotateLeft().rotateLeft(),
            bestDir.rotateRight().rotateRight().rotateRight(),
        };

        for (Direction dir : tryDirs) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        }
        return false;
    }

    public static boolean moveRandom(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) {
            return false;
        }

        int startIdx = rng.nextInt(8);
        for (int i = 0; i < 8; i++) {
            Direction dir = directions[(startIdx + i) % 8];
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        }
        return false;
    }

    public static boolean moveTowardUnpainted(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) {
            return false;
        }

        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) {
                continue;
            }

            MapLocation nextLoc = myLoc.add(dir);
            MapInfo info = rc.senseMapInfo(nextLoc);
            int score = 0;

            if (info.getPaint() == PaintType.EMPTY) {
                score += 4;
            } else if (info.getPaint().isEnemy()) {
                score += 5;
            } else {
                score += 1;
            }

            if (info.hasRuin()) {
                score += 2;
            }

            score = score * 10 + rng.nextInt(10);
            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            return true;
        }
        return false;
    }

    public static boolean moveTowardEnemyPaint(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) {
            return false;
        }

        MapLocation myLoc = rc.getLocation();
        MapInfo[] tiles = rc.senseNearbyMapInfos();
        MapLocation nearestEnemyPaint = null;
        int nearestDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (tile.getPaint().isEnemy()) {
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestEnemyPaint = tile.getMapLocation();
                }
            }
        }

        return moveToward(rc, nearestEnemyPaint);
    }

    public static MapLocation findNearestAllyTower(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

        MapLocation nearest = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) {
                continue;
            }

            int dist = myLoc.distanceSquaredTo(ally.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                nearest = ally.getLocation();
            }
        }

        return nearest;
    }

    public static MapLocation findNearestRuin(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        MapLocation nearest = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.hasRuin()) {
                continue;
            }

            MapLocation ruinLoc = tile.getMapLocation();
            RobotInfo occupant = rc.senseRobotAtLocation(ruinLoc);
            if (occupant != null) {
                continue;
            }

            int dist = myLoc.distanceSquaredTo(ruinLoc);
            if (dist < bestDist) {
                bestDist = dist;
                nearest = ruinLoc;
            }
        }

        return nearest;
    }

    public static MapLocation findNearestEnemyTower(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Team enemyTeam = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);

        MapLocation nearest = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            if (!enemy.getType().isTowerType()) {
                continue;
            }

            int dist = myLoc.distanceSquaredTo(enemy.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                nearest = enemy.getLocation();
            }
        }

        return nearest;
    }

    public static boolean isOnAllyPaint(RobotController rc) throws GameActionException {
        return rc.senseMapInfo(rc.getLocation()).getPaint().isAlly();
    }
}
