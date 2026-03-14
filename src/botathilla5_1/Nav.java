package botathilla5_1;

import battlecode.common.*;

/**
 * Fuzzy + BugNav + anti-oscillation.
 * Adds anti-stuck helpers and interior-biased explore targets to reduce edge idling.
 */
public class Nav {

    private static final int HISTORY = 8;
    private static MapLocation[] history = new MapLocation[HISTORY];
    private static int histIdx = 0;

    static void recordPosition(MapLocation loc) {
        history[histIdx % HISTORY] = loc;
        histIdx++;
    }

    static boolean isLikelyStuck(MapLocation loc, int repeats) {
        int cnt = 0;
        for (int i = 0; i < HISTORY; i++) {
            if (history[i] != null && history[i].equals(loc)) cnt++;
        }
        return cnt >= repeats;
    }

    private static boolean wasRecentlyAt(MapLocation loc) {
        for (int i = 0; i < HISTORY; i++) {
            if (history[i] != null && history[i].equals(loc)) return true;
        }
        return false;
    }

    // ---- fuzzy move ----

    static boolean fuzzyMove(MapLocation target) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return false;
        Direction dir = rc.getLocation().directionTo(target);
        if (dir == Direction.CENTER) {
            Direction escape = escapeFromEdge(rc.getLocation());
            if (escape != Direction.CENTER) return fuzzyMove(escape);
            return false;
        }
        return fuzzyMove(dir);
    }

    static boolean fuzzyMove(Direction dir) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady() || dir == Direction.CENTER) return false;
        Direction best = null;
        int bestScore = -9999;
        for (Direction d : fuzzyOrder(dir)) {
            if (rc.canMove(d)) {
                MapLocation next = rc.getLocation().add(d);
                int s = tileScore(next);
                if (wasRecentlyAt(next)) s -= 30;
                if (s > bestScore) {
                    bestScore = s;
                    best = d;
                }
            }
        }
        if (best != null && bestScore > -50) {
            rc.move(best);
            return true;
        }
        return false;
    }

    static boolean safeFuzzyMove(MapLocation target, RobotInfo[] enemies) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return false;
        Direction[] tries = fuzzyOrder(rc.getLocation().directionTo(target));
        Direction best = null;
        int bestScore = -9999;
        for (Direction d : tries) {
            MapLocation next = rc.getLocation().add(d);
            if (rc.canMove(d) && !inEnemyTowerRange(next, enemies)) {
                int s = tileScore(next);
                if (wasRecentlyAt(next)) s -= 30;
                if (s > bestScore) {
                    bestScore = s;
                    best = d;
                }
            }
        }
        if (best != null) {
            rc.move(best);
            return true;
        }
        return false;
    }

    // ---- BugNav ----

    private static Direction[] bugStack = new Direction[80];
    private static int bugIdx = 0;
    private static MapLocation bugTarget = null;
    private static boolean bugRight = true;

    static void bugNav(MapLocation target) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return;
        if (bugTarget == null || bugTarget.distanceSquaredTo(target) > 8 || bugIdx >= 70) {
            bugStack = new Direction[80];
            bugIdx = 0;
            bugTarget = target;
        }
        bugTarget = target;
        while (bugIdx > 0 && rc.canMove(bugStack[bugIdx - 1])) bugIdx--;

        RobotInfo[] nearEn = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        if (bugIdx == 0) {
            Direction d = rc.getLocation().directionTo(target);
            Direction best = null;
            int bs = -9999;
            for (Direction t : new Direction[]{d, d.rotateLeft(), d.rotateRight()}) {
                MapLocation nl = rc.getLocation().add(t);
                if (rc.canMove(t) && !inEnemyTowerRange(nl, nearEn)) {
                    int s = tileScore(nl);
                    if (wasRecentlyAt(nl)) s -= 30;
                    if (s > bs) {
                        bs = s;
                        best = t;
                    }
                }
            }
            if (best != null && bs > -50) {
                rc.move(best);
                return;
            }
            bugStack[bugIdx++] = bugRight ? d.rotateLeft() : d.rotateRight();
        }
        Direction dir = bugRight ? bugStack[bugIdx - 1].rotateRight() : bugStack[bugIdx - 1].rotateLeft();
        for (int i = 0; i < 8; i++) {
            MapLocation wn = rc.getLocation().add(dir);
            if (rc.canMove(dir) && !inEnemyTowerRange(wn, nearEn)) {
                rc.move(dir);
                return;
            }
            if (!rc.onTheMap(wn)) {
                bugStack = new Direction[80];
                bugIdx = 0;
                bugRight = !bugRight;
                Direction escape = escapeFromEdge(rc.getLocation());
                if (escape != Direction.CENTER) fuzzyMove(escape);
                return;
            }
            bugStack[bugIdx++] = dir;
            dir = bugRight ? dir.rotateRight() : dir.rotateLeft();
        }

        Direction escape = escapeFromEdge(rc.getLocation());
        if (escape != Direction.CENTER) fuzzyMove(escape);
    }

    /** BugNav variant that accepts pre-sensed enemies to save bytecodes. */
    static void bugNav(MapLocation target, RobotInfo[] nearEn) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return;
        if (bugTarget == null || bugTarget.distanceSquaredTo(target) > 8 || bugIdx >= 70) {
            bugStack = new Direction[80];
            bugIdx = 0;
            bugTarget = target;
        }
        bugTarget = target;
        while (bugIdx > 0 && rc.canMove(bugStack[bugIdx - 1])) bugIdx--;

        if (bugIdx == 0) {
            Direction d = rc.getLocation().directionTo(target);
            Direction best = null;
            int bs = -9999;
            for (Direction t : new Direction[]{d, d.rotateLeft(), d.rotateRight()}) {
                MapLocation nl = rc.getLocation().add(t);
                if (rc.canMove(t) && !inEnemyTowerRange(nl, nearEn)) {
                    int s = tileScore(nl);
                    if (wasRecentlyAt(nl)) s -= 30;
                    if (s > bs) {
                        bs = s;
                        best = t;
                    }
                }
            }
            if (best != null && bs > -50) {
                rc.move(best);
                return;
            }
            bugStack[bugIdx++] = bugRight ? d.rotateLeft() : d.rotateRight();
        }
        Direction dir = bugRight ? bugStack[bugIdx - 1].rotateRight() : bugStack[bugIdx - 1].rotateLeft();
        for (int i = 0; i < 8; i++) {
            MapLocation wn = rc.getLocation().add(dir);
            if (rc.canMove(dir) && !inEnemyTowerRange(wn, nearEn)) {
                rc.move(dir);
                return;
            }
            if (!rc.onTheMap(wn)) {
                bugStack = new Direction[80];
                bugIdx = 0;
                bugRight = !bugRight;
                Direction escape = escapeFromEdge(rc.getLocation());
                if (escape != Direction.CENTER) fuzzyMove(escape);
                return;
            }
            bugStack[bugIdx++] = dir;
            dir = bugRight ? dir.rotateRight() : dir.rotateLeft();
        }

        Direction escape = escapeFromEdge(rc.getLocation());
        if (escape != Direction.CENTER) fuzzyMove(escape);
    }

    // ---- tile scoring ----

    static int tileScore(MapLocation loc) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.onTheMap(loc)) return -9999;
        MapInfo info = rc.senseMapInfo(loc);
        if (info.isWall()) return -9999;
        int score = 0;
        PaintType p = info.getPaint();
        if (p.isAlly()) score += 4;
        else if (p == PaintType.EMPTY) score += 2;
        else score -= 8;
        if (Clock.getBytecodesLeft() > 3000) {
            score -= rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam).length * 2;
        }
        if (edgeDistance(loc) <= 1) score -= 3;
        return score;
    }

    // ---- helpers ----

    static boolean inEnemyTowerRange(MapLocation loc, RobotInfo[] enemies) {
        for (RobotInfo e : enemies)
            if (e.type.isTowerType() && loc.distanceSquaredTo(e.location) <= e.type.actionRadiusSquared) return true;
        return false;
    }

    static RobotInfo nearestEnemyTower(RobotInfo[] enemies) {
        RobotInfo best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) {
                int d = RobotPlayer.rc.getLocation().distanceSquaredTo(e.location);
                if (d < bd) {
                    bd = d;
                    best = e;
                }
            }
        }
        return best;
    }

    static Direction[] fuzzyOrder(Direction dir) {
        return new Direction[]{dir, dir.rotateLeft(), dir.rotateRight(),
                dir.rotateLeft().rotateLeft(), dir.rotateRight().rotateRight(),
                dir.rotateLeft().rotateLeft().rotateLeft(),
                dir.rotateRight().rotateRight().rotateRight()};
    }

    static boolean moveIntoRange(MapLocation target, int rangeSq) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return false;
        Direction best = null;
        int bs = -9999;
        for (Direction d : fuzzyOrder(rc.getLocation().directionTo(target))) {
            MapLocation next = rc.getLocation().add(d);
            if (rc.canMove(d) && next.distanceSquaredTo(target) <= rangeSq) {
                int s = tileScore(next);
                if (wasRecentlyAt(next)) s -= 30;
                if (s > bs) {
                    bs = s;
                    best = d;
                }
            }
        }
        if (best != null) {
            rc.move(best);
            return true;
        }
        return fuzzyMove(target);
    }

    static MapLocation pickExploreTarget(MapLocation from, int seed) {
        Direction dir = RobotPlayer.DIRS[Math.floorMod(seed, 8)];
        MapLocation t = extendToEdge(from, dir);
        if (t.equals(from) || edgeDistance(t) <= 1) {
            MapLocation t2 = extendToEdge(from, dir.rotateLeft().rotateLeft());
            if (!t2.equals(from)) t = t2;
        }
        if (t.equals(from) || edgeDistance(t) <= 1) {
            int margin = Math.max(2, Math.min(RobotPlayer.mapW, RobotPlayer.mapH) / 10);
            int ox = ((seed * 11) % 17) - 8;
            int oy = ((seed * 7) % 17) - 8;
            int x = clamp(from.x + ox, margin, RobotPlayer.mapW - margin - 1);
            int y = clamp(from.y + oy, margin, RobotPlayer.mapH - margin - 1);
            t = new MapLocation(x, y);
        }
        return t;
    }

    static MapLocation extendToEdge(MapLocation from, Direction dir) {
        MapLocation loc = from;
        int margin = 2;
        for (int i = 0; i < 60; i++) {
            MapLocation next = loc.add(dir);
            if (next.x < margin || next.y < margin || next.x >= RobotPlayer.mapW - margin || next.y >= RobotPlayer.mapH - margin) {
                break;
            }
            loc = next;
        }
        return loc;
    }

    private static Direction escapeFromEdge(MapLocation me) {
        int left = me.x;
        int right = RobotPlayer.mapW - 1 - me.x;
        int up = me.y;
        int down = RobotPlayer.mapH - 1 - me.y;
        int min = Math.min(Math.min(left, right), Math.min(up, down));
        if (min > 1) return Direction.CENTER;
        if (left == min) return Direction.EAST;
        if (right == min) return Direction.WEST;
        if (up == min) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private static int edgeDistance(MapLocation loc) {
        int left = loc.x;
        int right = RobotPlayer.mapW - 1 - loc.x;
        int up = loc.y;
        int down = RobotPlayer.mapH - 1 - loc.y;
        return Math.min(Math.min(left, right), Math.min(up, down));
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
