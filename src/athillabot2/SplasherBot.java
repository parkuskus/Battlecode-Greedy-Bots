package athillabot2;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class SplasherBot {
    private static final int HARD_REFILL_THRESHOLD = 60;
    private static final int SOFT_REFILL_THRESHOLD = 100;
    private static final int ATTACK_COST = 50;

    private static MapLocation homeTower = null;
    private static MapLocation refillTarget = null;

    private static MapLocation enemyTowerHint = null;
    private static int enemyTowerHintRound = -1000;

    private static MapLocation frontierTarget = null;
    private static int frontierStallTurns = 0;
    private static int lastFrontierDist = Integer.MAX_VALUE;

    public static void run(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        initIfNeeded(rc, me);
        Navigation.updateSymmetryModel(rc);
        consumeMessages(rc);

        int visibleEnemyPaint = countVisibleEnemyPaint(rc);

        if (rc.getPaint() < HARD_REFILL_THRESHOLD) {
            doRefill(rc);
            return;
        }

        int openingThreshold = dynamicAttackThreshold(rc, visibleEnemyPaint);
        boolean attacked = tryBestAttack(rc, openingThreshold);

        if (rc.getPaint() < SOFT_REFILL_THRESHOLD && !attacked && visibleEnemyPaint < 5) {
            doRefill(rc);
            return;
        }

        moveToFrontier(rc, visibleEnemyPaint);

        if (rc.getPaint() >= ATTACK_COST) {
            int followUpThreshold = Math.max(9, openingThreshold - 2);
            tryBestAttack(rc, followUpThreshold);
        }
    }

    private static void initIfNeeded(RobotController rc, MapLocation me) throws GameActionException {
        if (homeTower != null) {
            return;
        }

        homeTower = Navigation.findNearestAllyTower(rc);
        if (homeTower == null) {
            homeTower = me;
        }

        frontierTarget = Navigation.predictEnemyFromAlly(rc, homeTower);
        if (frontierTarget == null) {
            frontierTarget = me;
        }
    }

    private static void consumeMessages(RobotController rc) throws GameActionException {
        Communication.processMessages(rc, (type, loc, extra, senderID, round) -> {
            if (type == Communication.MSG_ENEMY_TOWER_SEEN) {
                enemyTowerHint = loc;
                enemyTowerHintRound = round;
            }
        });
    }

    private static void doRefill(RobotController rc) throws GameActionException {
        if (refillTarget == null) {
            refillTarget = Navigation.findNearestAllyTower(rc);
        }

        if (refillTarget == null) {
            Navigation.moveTowardUnpainted(rc);
            return;
        }

        if (rc.getLocation().distanceSquaredTo(refillTarget) <= 2) {
            int need = rc.getType().paintCapacity - rc.getPaint();
            if (need > 0 && rc.canTransferPaint(refillTarget, -need)) {
                rc.transferPaint(refillTarget, -need);
            }
            if (rc.getPaint() >= SOFT_REFILL_THRESHOLD) {
                refillTarget = null;
            }
            return;
        }

        Navigation.moveToward(rc, refillTarget, true);
    }

    private static boolean tryBestAttack(RobotController rc, int minScore) throws GameActionException {
        if (!rc.isActionReady() || rc.getPaint() < ATTACK_COST) {
            return false;
        }

        MapLocation me = rc.getLocation();
        MapLocation[] candidates = rc.getAllLocationsWithinRadiusSquared(me, 4);

        MapLocation bestCenter = null;
        int bestScore = Integer.MIN_VALUE;
        boolean bestHitsTower = false;
        int bestEnemyOverwrite = 0;

        for (MapLocation center : candidates) {
            if (Clock.getBytecodesLeft() < 2300) {
                break;
            }
            if (!rc.canAttack(center)) {
                continue;
            }

            AttackEval eval = evaluateAttackCenter(rc, center);
            if (eval.score > bestScore) {
                bestScore = eval.score;
                bestCenter = center;
                bestHitsTower = eval.hitsEnemyTower;
                bestEnemyOverwrite = eval.enemyOverwrite;
            }
        }

        if (bestCenter == null) {
            return false;
        }

        if (bestHitsTower && bestScore >= 9) {
            rc.attack(bestCenter);
            return true;
        }

        if (bestEnemyOverwrite >= 2 && bestScore >= minScore) {
            rc.attack(bestCenter);
            return true;
        }

        return false;
    }

    private static AttackEval evaluateAttackCenter(RobotController rc, MapLocation center) throws GameActionException {
        int score = 0;
        int enemyOverwrite = 0;
        boolean hitsEnemyTower = false;

        MapLocation[] affected = rc.getAllLocationsWithinRadiusSquared(center, 4);
        for (MapLocation loc : affected) {
            if (Clock.getBytecodesLeft() < 2000) {
                break;
            }
            if (!rc.canSenseLocation(loc)) {
                continue;
            }

            MapInfo info = rc.senseMapInfo(loc);
            if (info.isWall() || info.hasRuin()) {
                continue;
            }

            PaintType paint = info.getPaint();
            int d = center.distanceSquaredTo(loc);

            if (paint.isEnemy()) {
                if (d <= 2) {
                    score += 6;
                    enemyOverwrite++;
                } else {
                    score += 2;
                }
            } else if (paint == PaintType.EMPTY) {
                score += 1;
            } else {
                score -= 2;
            }

            RobotInfo occ = rc.senseRobotAtLocation(loc);
            if (occ != null && occ.getTeam() != rc.getTeam() && occ.getType().isTowerType()) {
                score += 10;
                hitsEnemyTower = true;
            }
        }

        return new AttackEval(score, enemyOverwrite, hitsEnemyTower);
    }

    private static int dynamicAttackThreshold(RobotController rc, int visibleEnemyPaint) {
        int threshold = 14;
        if (visibleEnemyPaint >= 8) {
            threshold = 12;
        }
        if (rc.getRoundNum() >= 1200) {
            threshold = Math.min(threshold, 11);
        }
        return threshold;
    }

    private static int countVisibleEnemyPaint(RobotController rc) throws GameActionException {
        int count = 0;
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.getPaint().isEnemy()) {
                count++;
            }
        }
        return count;
    }

    private static void moveToFrontier(RobotController rc, int visibleEnemyPaint) throws GameActionException {
        MapLocation me = rc.getLocation();

        if (enemyTowerHint != null && rc.getRoundNum() - enemyTowerHintRound <= 80) {
            frontierTarget = enemyTowerHint;
        } else {
            MapLocation enemyPaintCenter = findEnemyPaintCenter(rc);
            if (enemyPaintCenter != null) {
                frontierTarget = enemyPaintCenter;
            } else if (visibleEnemyPaint == 0) {
                MapLocation sym = Navigation.predictEnemyFromAlly(rc, homeTower);
                if (sym != null) {
                    frontierTarget = sym;
                }
            }
        }

        if (frontierTarget == null) {
            frontierTarget = me;
        }

        int dist = me.distanceSquaredTo(frontierTarget);
        if (dist < lastFrontierDist) {
            lastFrontierDist = dist;
            frontierStallTurns = 0;
        } else {
            frontierStallTurns++;
        }

        if (dist <= 5 || frontierStallTurns >= 12) {
            frontierTarget = rerollFrontierTarget(rc);
            lastFrontierDist = me.distanceSquaredTo(frontierTarget);
            frontierStallTurns = 0;
        }

        if (!Navigation.moveToward(rc, frontierTarget, true)) {
            Navigation.moveTowardUnpainted(rc);
        }
    }

    private static MapLocation findEnemyPaintCenter(RobotController rc) throws GameActionException {
        int sx = 0;
        int sy = 0;
        int n = 0;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (Clock.getBytecodesLeft() < 2100) {
                break;
            }
            if (tile.getPaint().isEnemy()) {
                MapLocation loc = tile.getMapLocation();
                sx += loc.x;
                sy += loc.y;
                n++;
            }
        }

        if (n < 2) {
            return null;
        }
        return new MapLocation(sx / n, sy / n);
    }

    private static MapLocation rerollFrontierTarget(RobotController rc) {
        MapLocation me = rc.getLocation();
        int choice = (rc.getRoundNum() / 20) % 3;

        if (choice == 0) {
            MapLocation sym = Navigation.predictEnemyFromAlly(rc, homeTower);
            if (sym != null) {
                return sym;
            }
        }

        if (choice == 1) {
            return new MapLocation(rc.getMapWidth() - 1 - me.x, rc.getMapHeight() - 1 - me.y);
        }

        return new MapLocation(RobotPlayer.rng.nextInt(rc.getMapWidth()), RobotPlayer.rng.nextInt(rc.getMapHeight()));
    }

    private static final class AttackEval {
        final int score;
        final int enemyOverwrite;
        final boolean hitsEnemyTower;

        AttackEval(int score, int enemyOverwrite, boolean hitsEnemyTower) {
            this.score = score;
            this.enemyOverwrite = enemyOverwrite;
            this.hitsEnemyTower = hitsEnemyTower;
        }
    }
}
