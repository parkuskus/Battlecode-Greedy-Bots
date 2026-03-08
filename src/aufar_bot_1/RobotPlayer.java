package aufar_bot_1;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Bot-1 (Tahap 1): greedy sederhana untuk ekspansi area paint.
 */
public class RobotPlayer {
    static final Random rng = new Random(2211);

    static final Direction[] DIRECTIONS = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                switch (rc.getType()) {
                    case SOLDIER -> runSoldier(rc);
                    case MOPPER -> runMopper(rc);
                    case SPLASHER -> runSplasher(rc);
                    default -> runTower(rc);
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    private static void runTower(RobotController rc) throws GameActionException {
        attackEnemyIfPossible(rc);

        // Tahap-1 greedy produksi:
        // 1) Prioritaskan SOLDIER untuk ekspansi paint.
        // 2) Jika tidak ada slot/biaya cocok, coba MOPPER.
        if (tryBuild(rc, UnitType.SOLDIER)) {
            rc.setIndicatorString("B1-T1: spawn SOLDIER");
            return;
        }
        if (tryBuild(rc, UnitType.MOPPER)) {
            rc.setIndicatorString("B1-T1: spawn MOPPER");
        }
    }

    private static void runSoldier(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Prioritas greedy soldier:
        // enemy paint > empty > ally paint.
        MapLocation bestPaintTarget = selectBestPaintTarget(rc, nearbyTiles);
        if (bestPaintTarget != null && rc.canAttack(bestPaintTarget)) {
            rc.attack(bestPaintTarget);
        }

        // Gerak greedy menuju tile yang paling berpotensi menambah area paint.
        Direction bestMove = selectBestMoveForExpansion(rc);
        if (bestMove != null && rc.canMove(bestMove)) {
            rc.move(bestMove);
        }

        // Setelah bergerak, jika tile pijakan belum ally, cat tile sendiri.
        MapLocation here = rc.getLocation();
        if (rc.canAttack(here)) {
            PaintType p = rc.senseMapInfo(here).getPaint();
            if (!p.isAlly()) {
                rc.attack(here);
            }
        }

        rc.setIndicatorString("B1-T1 Soldier: expand paint");
    }

    private static void runMopper(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Greedy mopper: bersihkan enemy paint terdekat yang bisa diserang sekarang.
        MapLocation bestCleanTarget = selectNearestEnemyPaintInRange(rc, nearbyTiles);
        if (bestCleanTarget != null && rc.canAttack(bestCleanTarget)) {
            rc.attack(bestCleanTarget);
        }

        // Lalu bergerak ke arah konsentrasi enemy paint.
        Direction bestMove = selectBestMoveForCleaning(rc);
        if (bestMove != null && rc.canMove(bestMove)) {
            rc.move(bestMove);
        }

        rc.setIndicatorString("B1-T1 Mopper: clean enemy paint");
    }

    private static void runSplasher(RobotController rc) throws GameActionException {
        // Tahap-1: belum ada logika khusus splasher.
        Direction d = DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
        if (rc.canMove(d)) {
            rc.move(d);
        }
    }

    private static boolean tryBuild(RobotController rc, UnitType type) throws GameActionException {
        for (Direction d : DIRECTIONS) {
            MapLocation loc = rc.getLocation().add(d);
            if (rc.canBuildRobot(type, loc)) {
                rc.buildRobot(type, loc);
                return true;
            }
        }
        return false;
    }

    private static void attackEnemyIfPossible(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapLocation target = null;
        int bestHp = Integer.MAX_VALUE;

        for (RobotInfo e : enemies) {
            if (rc.canAttack(e.location) && e.health < bestHp) {
                target = e.location;
                bestHp = e.health;
            }
        }

        if (target != null) {
            rc.attack(target);
        }
    }

    private static MapLocation selectBestPaintTarget(RobotController rc, MapInfo[] infos) throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo info : infos) {
            MapLocation loc = info.getMapLocation();
            if (!rc.canAttack(loc)) {
                continue;
            }
            PaintType p = info.getPaint();
            int score = 0;
            if (p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) {
                score = 100;
            } else if (p == PaintType.EMPTY) {
                score = 60;
            } else if (p.isAlly()) {
                score = -20;
            }

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private static MapLocation selectNearestEnemyPaintInRange(RobotController rc, MapInfo[] infos) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        MapLocation me = rc.getLocation();

        for (MapInfo info : infos) {
            PaintType p = info.getPaint();
            if (p != PaintType.ENEMY_PRIMARY && p != PaintType.ENEMY_SECONDARY) {
                continue;
            }
            MapLocation loc = info.getMapLocation();
            if (!rc.canAttack(loc)) {
                continue;
            }
            int d = me.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }
        return best;
    }

    private static Direction selectBestMoveForExpansion(RobotController rc) throws GameActionException {
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation cur = rc.getLocation();

        for (Direction d : DIRECTIONS) {
            if (!rc.canMove(d)) {
                continue;
            }
            MapLocation next = cur.add(d);
            MapInfo tile = rc.senseMapInfo(next);
            int score = scoreTileForExpansion(tile.getPaint());

            // Sedikit bonus untuk tetap tersebar acak agar tidak macet.
            score += rng.nextInt(3);

            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }
        return bestDir;
    }

    private static int scoreTileForExpansion(PaintType p) {
        if (p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) {
            return 20;
        }
        if (p == PaintType.EMPTY) {
            return 12;
        }
        if (p.isAlly()) {
            return 4;
        }
        return 0;
    }

    private static Direction selectBestMoveForCleaning(RobotController rc) throws GameActionException {
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;
        MapLocation cur = rc.getLocation();

        for (Direction d : DIRECTIONS) {
            if (!rc.canMove(d)) {
                continue;
            }
            MapLocation next = cur.add(d);
            MapInfo tile = rc.senseMapInfo(next);
            PaintType p = tile.getPaint();
            int score = 0;

            if (p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) {
                score = 20;
            } else if (p == PaintType.EMPTY) {
                score = 8;
            } else if (p.isAlly()) {
                score = 2;
            }

            if (score > bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }
        return bestDir;
    }
}
