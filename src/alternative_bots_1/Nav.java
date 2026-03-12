package alternative_bots_1;
import battlecode.common.*;

public class Nav {
    // menyimpan 6 lokasi terakhir yang sudah pernah dikunjungi robot
    private static final int HISTORY_SIZE = 6;
    private static MapLocation[] history = new MapLocation[HISTORY_SIZE];
    private static int historyIdx = 0;

    static void recordPosition(MapLocation loc) {
        history[historyIdx % HISTORY_SIZE] = loc;
        historyIdx++;
    }

    private static boolean wasRecentlyAt(MapLocation loc) {
        for (int i = 0; i < HISTORY_SIZE; i++) {
            if (history[i] != null && history[i].equals(loc)) return true;
        }
        return false;
    }

    // algoritma fuzzy move
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
                MapLocation next = rc.getLocation().add(d);
                int s = tileScore(next);
                if (wasRecentlyAt(next)) s -= 30;
                // robot memilih arah dengan skor tertinggi yang ditemukan saat itu
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

    /* ---- safe fuzzy: additionally avoid enemy tower ranges ---- */
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

    // Algoritma bug navigation
    // robot akan menyusuri dinding rintangan sampai menemukan jalan keluar
    private static Direction[] bugStack = new Direction[80];
    private static int bugIdx = 0;
    private static MapLocation bugTarget = null;
    private static boolean bugRight = true;

    static void bugNav(MapLocation target) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.isMovementReady()) return;

        // reset memori saat langkah terlalu jauh atau memori terlalu penuh
        if (bugTarget == null || bugTarget.distanceSquaredTo(target) > 8 || bugIdx >= 70) {
            bugStack = new Direction[80];
            bugIdx = 0;
            bugTarget = target;
        }
        bugTarget = target;

        // cek apakah rintangan sudah hilang
        while (bugIdx > 0 && rc.canMove(bugStack[bugIdx - 1])) {
            bugIdx--;
        }

        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        // fase normal
        if (bugIdx == 0) {
            Direction d = rc.getLocation().directionTo(target);
            Direction best = null;
            int bestScore = -9999;
            for (Direction try_ : new Direction[]{d, d.rotateLeft(), d.rotateRight()}) {
                MapLocation nextLoc = rc.getLocation().add(try_);
                if (rc.canMove(try_) && !inEnemyTowerRange(nextLoc, nearbyEnemies)) {
                    int s = tileScore(nextLoc);
                    if (wasRecentlyAt(nextLoc)) s -= 30;
                    if (s > bestScore) {
                        bestScore = s;
                        best = try_;
                    }
                }
            }
            if (best != null && bestScore > -50) {
                rc.move(best);
                return;
            }
            bugStack[bugIdx++] = bugRight ? d.rotateLeft() : d.rotateRight();
        }

        // bagian rotasi dan exploration saat menabrak dinding
        Direction dir = bugRight
            ? bugStack[bugIdx - 1].rotateRight()
            : bugStack[bugIdx - 1].rotateLeft();
        for (int i = 0; i < 8; i++) {
            MapLocation wallNext = rc.getLocation().add(dir);
            if (rc.canMove(dir) && !inEnemyTowerRange(wallNext, nearbyEnemies)) {
                rc.move(dir);
                return;
            }

            // jika terjebak robot akan berbalik arah
            if (!rc.onTheMap(wallNext)) {
                bugStack = new Direction[80];
                bugIdx = 0;
                bugRight = !bugRight;
                return;
            }
            bugStack[bugIdx++] = dir;
            dir = bugRight ? dir.rotateRight() : dir.rotateLeft();
        }
    }

    /* ---- tile scoring ---- */
    static int tileScore(MapLocation loc) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        // cegah robot keluar map atau ke dinding
        if (!rc.onTheMap(loc)) return -9999;
        MapInfo info = rc.senseMapInfo(loc);
        if (info.isWall()) return -9999;
        int score = 0;
        // scoring berdasarkan warna tile
        PaintType p = info.getPaint();
        if (p.isAlly()) score += 4;
        else if (p == PaintType.EMPTY) score += 2;
        else if (p.isEnemy()) score -= 8;
        // mencegah crowd 
        if (Clock.getBytecodesLeft() > 3000) {
            int nearbyAllies = rc.senseNearbyRobots(loc, 2, RobotPlayer.myTeam).length;
            score -= nearbyAllies * 2;
        }
        return score;
    }

    // helpers
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
        return fuzzyMove(target);
    }
}
