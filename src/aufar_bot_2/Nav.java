package aufar_bot_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

/**
 * Navigation helpers — coverage-aware movement for all mobile units.
 *
 * Tile scoring prioritizes EMPTY tiles highly (+5) to maximize fresh coverage,
 * rewards enemy paint flipping (+3), and slightly rewards ally paint (+1).
 * BugNav with wall-following for pathfinding around obstacles.
 */
public class Nav {

    // ====== Fuzzy Move (direction-cone with tile scoring) ======

    static boolean fuzzyMove(MapLocation target) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return false;
        Direction dir = rc.getLocation().directionTo(target);
        return fuzzyMove(dir);
    }

    static boolean fuzzyMove(Direction dir) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady() || dir == Direction.CENTER) return false;

        Direction[] tries = fuzzyOrder(dir);
        Direction best = null;
        int bestScore = -9999;
        for (Direction d : tries) {
            if (rc.canMove(d)) {
                int s = tileScore(rc.getLocation().add(d));
                if (s > bestScore) { bestScore = s; best = d; }
            }
        }
        if (best != null && bestScore > -50) {
            rc.move(best);
            return true;
        }
        return false;
    }

    // ====== Safe Fuzzy Move (avoids enemy tower ranges) ======

    static boolean safeFuzzyMove(MapLocation target, RobotInfo[] enemies) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return false;
        Direction dir = rc.getLocation().directionTo(target);
        Direction[] tries = fuzzyOrder(dir);
        Direction best = null;
        int bestScore = -9999;
        for (Direction d : tries) {
            MapLocation next = rc.getLocation().add(d);
            if (rc.canMove(d) && !inEnemyTowerRange(next, enemies)) {
                int s = tileScore(next);
                if (s > bestScore) { bestScore = s; best = d; }
            }
        }
        if (best != null) {
            rc.move(best);
            return true;
        }
        return false;
    }

    // ====== BugNav (wall-following with tile scoring) ======

    private static Direction[] bugStack = new Direction[80];
    private static int bugIdx = 0;
    private static MapLocation bugTarget = null;
    private static boolean bugRight = true;

    static void bugNav(MapLocation target) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return;

        // reset if target changed significantly or stack overflow
        if (bugTarget == null || bugTarget.distanceSquaredTo(target) > 8 || bugIdx >= 70) {
            bugStack = new Direction[80];
            bugIdx = 0;
            bugTarget = target;
        }
        bugTarget = target;

        // pop passable directions off stack
        while (bugIdx > 0 && rc.canMove(bugStack[bugIdx - 1])) {
            bugIdx--;
        }

        // direct movement with scoring
        if (bugIdx == 0) {
            Direction d = rc.getLocation().directionTo(target);
            Direction best = null;
            int bestScore = -9999;
            for (Direction try_ : new Direction[]{d, d.rotateLeft(), d.rotateRight()}) {
                if (rc.canMove(try_)) {
                    int s = tileScore(rc.getLocation().add(try_));
                    if (s > bestScore) { bestScore = s; best = try_; }
                }
            }
            if (best != null && bestScore > -50) {
                rc.move(best);
                return;
            }
            // blocked — start wall following
            bugStack[bugIdx++] = bugRight ? d.rotateLeft() : d.rotateRight();
        }

        // wall following
        Direction dir = bugRight
            ? bugStack[bugIdx - 1].rotateRight()
            : bugStack[bugIdx - 1].rotateLeft();
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return;
            }
            if (!rc.onTheMap(rc.getLocation().add(dir))) {
                bugStack = new Direction[80];
                bugIdx = 0;
                bugRight = !bugRight;
                return;
            }
            bugStack[bugIdx++] = dir;
            dir = bugRight ? dir.rotateRight() : dir.rotateLeft();
        }
    }

    // ====== Coverage-Aware Tile Scoring ======
    // EMPTY tiles scored highest (+6) to maximize fresh paint coverage.
    // Enemy paint scored +4 (valuable to flip). Ally paint +1 (safe but no coverage gain).

    static int tileScore(MapLocation loc) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.onTheMap(loc)) return -9999;
        MapInfo info = rc.senseMapInfo(loc);
        if (info.isWall()) return -9999;
        PaintType p = info.getPaint();
        if (p == PaintType.EMPTY) return 6;
        if (p.isEnemy()) return 4;
        if (p.isAlly()) return 1;
        return 0;
    }

    // ====== Move Into Range ======

    static boolean moveIntoRange(MapLocation target, int rangeSq) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return false;
        Direction dir = rc.getLocation().directionTo(target);
        Direction best = null;
        int bestScore = -9999;
        for (Direction d : fuzzyOrder(dir)) {
            MapLocation next = rc.getLocation().add(d);
            if (rc.canMove(d) && next.distanceSquaredTo(target) <= rangeSq) {
                int s = tileScore(next);
                if (s > bestScore) { bestScore = s; best = d; }
            }
        }
        if (best != null) { rc.move(best); return true; }
        return rc.getLocation().distanceSquaredTo(target) <= rangeSq;
    }

    // ====== Helpers ======

    static boolean inEnemyTowerRange(MapLocation loc, RobotInfo[] enemies) {
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()
                && loc.distanceSquaredTo(e.location) <= e.type.actionRadiusSquared) {
                return true;
            }
        }
        return false;
    }

    static RobotInfo nearestEnemyTower(RobotInfo[] enemies) {
        RobotController rc = RobotPlayer.rc;
        RobotInfo best = null;
        int bestD = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) {
                int d = rc.getLocation().distanceSquaredTo(e.location);
                if (d < bestD) { bestD = d; best = e; }
            }
        }
        return best;
    }

    static Direction[] fuzzyOrder(Direction dir) {
        return new Direction[]{
            dir,
            dir.rotateLeft(), dir.rotateRight(),
            dir.rotateLeft().rotateLeft(), dir.rotateRight().rotateRight(),
            dir.rotateLeft().rotateLeft().rotateLeft(),
            dir.rotateRight().rotateRight().rotateRight()
        };
    }

    /** Extend a location in a direction until reaching the map edge. */
    static MapLocation extendToEdge(MapLocation from, Direction dir) {
        MapLocation loc = from;
        for (int i = 0; i < 60; i++) {
            MapLocation next = loc.add(dir);
            if (next.x < 0 || next.y < 0
                || next.x >= RobotPlayer.mapW || next.y >= RobotPlayer.mapH) break;
            loc = next;
        }
        return loc;
    }
}
