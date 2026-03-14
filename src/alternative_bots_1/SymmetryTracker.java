package alternative_bots_1;

import battlecode.common.*;

public class SymmetryTracker {
    private static final int SYM_MIRROR_X = 0;
    private static final int SYM_MIRROR_Y = 1;
    private static final int SYM_ROTATIONAL = 2;

    private static final boolean[] possible = { true, true, true };
    private static final int[] evidence = { 0, 0, 0 };

    private static MapLocation origin = null;

    static void init(MapLocation allyOrigin) {
        if (origin == null) {
            origin = allyOrigin;
        }
    }

    static void observe(MapInfo[] nearby) throws GameActionException {
        if (origin == null) return;

        RobotController rc = RobotPlayer.rc;
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 2500) break;

            MapLocation loc = info.getMapLocation();
            for (int s = 0; s < 3; s++) {
                if (!possible[s]) continue;

                MapLocation mirror = mirror(loc, s);
                if (!rc.onTheMap(mirror)) {
                    possible[s] = false;
                    evidence[s] -= 100;
                    continue;
                }
                if (!rc.canSenseLocation(mirror)) continue;

                MapInfo other = rc.senseMapInfo(mirror);
                boolean sameWall = info.isWall() == other.isWall();
                boolean sameRuin = info.hasRuin() == other.hasRuin();

                if (sameWall && sameRuin) {
                    evidence[s]++;
                } else {
                    possible[s] = false;
                    evidence[s] -= 100;
                }
            }
        }

        if (!possible[0] && !possible[1] && !possible[2]) {
            possible[0] = true;
            possible[1] = true;
            possible[2] = true;
        }
    }

    static MapLocation targetForSlot(MapLocation allyOrigin, int slot, int spreadSeed) {
        int[] ordered = orderedSymmetry(spreadSeed);
        int idx = ordered[Math.floorMod(slot, 3)];
        return mirror(allyOrigin, idx);
    }

    static int bestSymmetry() {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int s = 0; s < 3; s++) {
            if (!possible[s]) continue;
            if (evidence[s] > bestScore) {
                best = s;
                bestScore = evidence[s];
            }
        }
        if (best >= 0) return best;

        int fallback = SYM_ROTATIONAL;
        int fallbackScore = evidence[fallback];
        for (int s = 0; s < 3; s++) {
            if (evidence[s] > fallbackScore) {
                fallback = s;
                fallbackScore = evidence[s];
            }
        }
        return fallback;
    }

    private static int[] orderedSymmetry(int spreadSeed) {
        int[] buf = new int[3];
        int n = 0;

        int first = bestSymmetry();
        buf[n++] = first;

        int second = -1;
        int secondScore = Integer.MIN_VALUE;
        for (int s = 0; s < 3; s++) {
            if (s == first || !possible[s]) continue;
            if (evidence[s] > secondScore) {
                second = s;
                secondScore = evidence[s];
            }
        }
        if (second >= 0) buf[n++] = second;

        for (int s = 0; s < 3; s++) {
            if (contains(buf, n, s)) continue;
            buf[n++] = s;
        }

        int rotate = Math.floorMod(spreadSeed, 3);
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = buf[(i + rotate) % 3];
        }
        return out;
    }

    private static boolean contains(int[] arr, int len, int v) {
        for (int i = 0; i < len; i++) {
            if (arr[i] == v) return true;
        }
        return false;
    }

    private static MapLocation mirror(MapLocation loc, int symmetry) {
        return switch (symmetry) {
            case SYM_MIRROR_X -> new MapLocation(RobotPlayer.mapW - loc.x - 1, loc.y);
            case SYM_MIRROR_Y -> new MapLocation(loc.x, RobotPlayer.mapH - loc.y - 1);
            default -> new MapLocation(RobotPlayer.mapW - loc.x - 1, RobotPlayer.mapH - loc.y - 1);
        };
    }
}
