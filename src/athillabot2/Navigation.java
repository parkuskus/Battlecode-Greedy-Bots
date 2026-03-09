package athillabot2;

import battlecode.common.Clock;
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

    private enum Symmetry {
        ROTATIONAL,
        VERTICAL,
        HORIZONTAL
    }

    private static boolean canRotational = true;
    private static boolean canVertical = true;
    private static boolean canHorizontal = true;

    public static void updateSymmetryModel(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        for (MapInfo tile : nearbyTiles) {
            if (Clock.getBytecodesLeft() < 2200) {
                return;
            }

            MapLocation loc = tile.getMapLocation();

            if (canRotational) {
                MapLocation mirror = mirror(rc, loc, Symmetry.ROTATIONAL);
                if (rc.onTheMap(mirror) && rc.canSenseLocation(mirror)) {
                    MapInfo mirrorInfo = rc.senseMapInfo(mirror);
                    if (tile.isWall() != mirrorInfo.isWall() || tile.hasRuin() != mirrorInfo.hasRuin()) {
                        canRotational = false;
                    }
                }
            }

            if (canVertical) {
                MapLocation mirror = mirror(rc, loc, Symmetry.VERTICAL);
                if (rc.onTheMap(mirror) && rc.canSenseLocation(mirror)) {
                    MapInfo mirrorInfo = rc.senseMapInfo(mirror);
                    if (tile.isWall() != mirrorInfo.isWall() || tile.hasRuin() != mirrorInfo.hasRuin()) {
                        canVertical = false;
                    }
                }
            }

            if (canHorizontal) {
                MapLocation mirror = mirror(rc, loc, Symmetry.HORIZONTAL);
                if (rc.onTheMap(mirror) && rc.canSenseLocation(mirror)) {
                    MapInfo mirrorInfo = rc.senseMapInfo(mirror);
                    if (tile.isWall() != mirrorInfo.isWall() || tile.hasRuin() != mirrorInfo.hasRuin()) {
                        canHorizontal = false;
                    }
                }
            }
        }
    }

    public static MapLocation predictEnemyFromAlly(RobotController rc, MapLocation allyReference) {
        if (allyReference == null) {
            return null;
        }

        Symmetry symmetry = getLikelySymmetry();
        return mirror(rc, allyReference, symmetry);
    }

    private static Symmetry getLikelySymmetry() {
        if (canRotational) {
            return Symmetry.ROTATIONAL;
        }
        if (canVertical) {
            return Symmetry.VERTICAL;
        }
        return Symmetry.HORIZONTAL;
    }

    private static MapLocation mirror(RobotController rc, MapLocation loc, Symmetry symmetry) {
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();

        return switch (symmetry) {
            case ROTATIONAL -> new MapLocation(w - 1 - loc.x, h - 1 - loc.y);
            case VERTICAL -> new MapLocation(w - 1 - loc.x, loc.y);
            case HORIZONTAL -> new MapLocation(loc.x, h - 1 - loc.y);
        };
    }

    public static boolean moveToward(RobotController rc, MapLocation target) throws GameActionException {
        return moveToward(rc, target, true);
    }

    public static boolean moveToward(RobotController rc, MapLocation target, boolean preferAllyPaint) throws GameActionException {
        if (target == null || !rc.isMovementReady()) {
            return false;
        }

        MapLocation myLoc = rc.getLocation();
        Direction toward = myLoc.directionTo(target);
        if (toward == Direction.CENTER) {
            return false;
        }

        Direction[] tryDirs = {
            toward,
            toward.rotateLeft(),
            toward.rotateRight(),
            toward.rotateLeft().rotateLeft(),
            toward.rotateRight().rotateRight(),
            toward.rotateLeft().rotateLeft().rotateLeft(),
            toward.rotateRight().rotateRight().rotateRight()
        };

        Direction best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : tryDirs) {
            if (!rc.canMove(dir)) {
                continue;
            }

            MapLocation next = myLoc.add(dir);
            int score = scoreMoveTile(rc, next, target, preferAllyPaint);
            if (score > bestScore) {
                bestScore = score;
                best = dir;
            }
        }

        if (best != null) {
            rc.move(best);
            return true;
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
                score += 9;
            } else if (info.getPaint().isEnemy()) {
                score += 8;
            } else {
                score += 2;
            }

            if (info.hasRuin()) {
                score += 3;
            }

            score += rng.nextInt(6);

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
            if (!tile.getPaint().isEnemy()) {
                continue;
            }

            int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestEnemyPaint = tile.getMapLocation();
            }
        }

        return moveToward(rc, nearestEnemyPaint, true);
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
        return findNearestRuin(rc, null, -1000, 0);
    }

    public static MapLocation findNearestRuin(
            RobotController rc,
            MapLocation avoidRuin,
            int avoidRound,
            int avoidTtl
    ) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int round = rc.getRoundNum();

        MapLocation nearest = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (Clock.getBytecodesLeft() < 2300) {
                break;
            }

            if (!tile.hasRuin()) {
                continue;
            }

            MapLocation ruinLoc = tile.getMapLocation();
            RobotInfo occupant = rc.senseRobotAtLocation(ruinLoc);
            if (occupant != null) {
                continue;
            }

            if (avoidRuin != null && round - avoidRound <= avoidTtl && ruinLoc.equals(avoidRuin)) {
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

    public static MapLocation clampInMap(RobotController rc, MapLocation loc) {
        int x = Math.max(0, Math.min(rc.getMapWidth() - 1, loc.x));
        int y = Math.max(0, Math.min(rc.getMapHeight() - 1, loc.y));
        return new MapLocation(x, y);
    }

    private static int scoreMoveTile(RobotController rc, MapLocation next, MapLocation target, boolean preferAllyPaint)
            throws GameActionException {
        MapInfo info = rc.senseMapInfo(next);
        int distAfter = next.distanceSquaredTo(target);
        int score = -distAfter;

        PaintType paint = info.getPaint();
        if (paint.isAlly()) {
            score += preferAllyPaint ? 6 : 2;
        } else if (paint == PaintType.EMPTY) {
            score += 2;
        } else {
            score -= preferAllyPaint ? 5 : 2;
        }

        if (info.hasRuin()) {
            score -= 2;
        }

        score += rng.nextInt(4);
        return score;
    }
}
