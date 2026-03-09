package athillabot2;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class MopperBot {
    private static final int HARD_REFILL_THRESHOLD = 18;
    private static final int SOFT_REFILL_THRESHOLD = 40;

    private static MapLocation homeTower = null;
    private static MapLocation refillTarget = null;

    private static MapLocation urgentTarget = null;
    private static int urgentTargetRound = -1000;

    private static MapLocation enemyTowerHint = null;
    private static int enemyTowerHintRound = -1000;

    public static void run(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        initIfNeeded(rc, me);
        Navigation.updateSymmetryModel(rc);
        consumeMessages(rc);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (rc.getPaint() <= HARD_REFILL_THRESHOLD) {
            doRefill(rc);
            return;
        }

        RobotInfo combatTarget = findCombatTarget(enemies, me);
        if (combatTarget != null && me.distanceSquaredTo(combatTarget.getLocation()) <= 9) {
            doCombat(rc, combatTarget, enemies);
            tryTransferPaintToAlly(rc, allies);
            return;
        }

        if (rc.getPaint() <= SOFT_REFILL_THRESHOLD && urgentTarget == null) {
            doRefill(rc);
            return;
        }

        if (tryMopSwing(rc, enemies)) {
            retreatFromEnemy(rc, enemies);
            tryTransferPaintToAlly(rc, allies);
            return;
        }

        tryTransferPaintToAlly(rc, allies);

        if (tryMopEnemyPaint(rc)) {
            return;
        }

        movePatrol(rc, enemies);
        tryMopEnemyPaint(rc);
    }

    private static void initIfNeeded(RobotController rc, MapLocation me) throws GameActionException {
        if (homeTower != null) {
            return;
        }

        homeTower = Navigation.findNearestAllyTower(rc);
        if (homeTower == null) {
            homeTower = me;
        }
    }

    private static void consumeMessages(RobotController rc) throws GameActionException {
        Communication.processMessages(rc, (type, loc, extra, senderID, round) -> {
            if (type == Communication.MSG_URGENT_MOPPER) {
                urgentTarget = loc;
                urgentTargetRound = round;
            } else if (type == Communication.MSG_ENEMY_TOWER_SEEN) {
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

    private static RobotInfo findCombatTarget(RobotInfo[] enemies, MapLocation me) {
        RobotInfo bestSoldier = null;
        int bestPaint = Integer.MAX_VALUE;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            if (enemy.getType() != UnitType.SOLDIER) {
                continue;
            }
            int dist = me.distanceSquaredTo(enemy.getLocation());
            int paint = enemy.getPaintAmount();
            if (paint < bestPaint || (paint == bestPaint && dist < bestDist)) {
                bestPaint = paint;
                bestDist = dist;
                bestSoldier = enemy;
            }
        }

        if (bestSoldier != null) {
            return bestSoldier;
        }

        RobotInfo bestOther = null;
        bestDist = Integer.MAX_VALUE;
        for (RobotInfo enemy : enemies) {
            if (enemy.getType().isTowerType()) {
                continue;
            }
            int dist = me.distanceSquaredTo(enemy.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                bestOther = enemy;
            }
        }
        return bestOther;
    }

    private static void doCombat(RobotController rc, RobotInfo target, RobotInfo[] enemies) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation targetLoc = target.getLocation();

        if (tryMopSwing(rc, enemies)) {
            return;
        }

        if (rc.canAttack(targetLoc)) {
            rc.attack(targetLoc);
            return;
        }

        if (myLoc.distanceSquaredTo(targetLoc) > 2) {
            Navigation.moveToward(rc, targetLoc, true);
        }

        if (rc.canAttack(targetLoc)) {
            rc.attack(targetLoc);
        }
    }

    private static boolean tryMopSwing(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        if (!rc.isActionReady()) {
            return false;
        }

        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        Direction bestDir = null;
        int bestHits = 0;

        for (Direction dir : dirs) {
            if (!rc.canMopSwing(dir)) {
                continue;
            }
            int hits = countSwingHits(rc, dir, enemies);
            if (hits > bestHits) {
                bestHits = hits;
                bestDir = dir;
            }
        }

        // Multi-hit only to preserve action value.
        if (bestDir != null && bestHits >= 2) {
            rc.mopSwing(bestDir);
            return true;
        }
        return false;
    }

    private static int countSwingHits(RobotController rc, Direction dir, RobotInfo[] enemies) {
        MapLocation me = rc.getLocation();
        Direction left = dir.rotateLeft().rotateLeft();
        Direction right = dir.rotateRight().rotateRight();

        MapLocation s1 = me.add(dir);
        MapLocation s2 = s1.add(dir);

        int hits = 0;
        for (RobotInfo enemy : enemies) {
            MapLocation e = enemy.getLocation();
            if (e.equals(s1) || e.equals(s1.add(left)) || e.equals(s1.add(right)) ||
                e.equals(s2) || e.equals(s2.add(left)) || e.equals(s2.add(right))) {
                hits++;
            }
        }
        return hits;
    }

    private static boolean tryMopEnemyPaint(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return false;
        }

        MapLocation me = rc.getLocation();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation[] ruins = rc.senseNearbyRuins(-1);

        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo tile : nearby) {
            if (Clock.getBytecodesLeft() < 2200) {
                break;
            }

            if (!tile.getPaint().isEnemy()) {
                continue;
            }

            MapLocation loc = tile.getMapLocation();
            int dist = me.distanceSquaredTo(loc);
            int score = 10 - dist;

            for (MapLocation ruin : ruins) {
                if (loc.distanceSquaredTo(ruin) <= 8) {
                    score += 8;
                    break;
                }
            }

            if (enemyTowerHint != null && rc.getRoundNum() - enemyTowerHintRound <= 80) {
                score += Math.max(0, 7 - loc.distanceSquaredTo(enemyTowerHint) / 3);
            }

            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        if (best == null) {
            return false;
        }

        if (me.distanceSquaredTo(best) > 2) {
            Navigation.moveToward(rc, best, true);
        }

        if (rc.canAttack(best)) {
            rc.attack(best);
            return true;
        }

        return false;
    }

    private static void movePatrol(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        MapLocation me = rc.getLocation();

        if (urgentTarget != null && rc.getRoundNum() - urgentTargetRound <= 90) {
            if (me.distanceSquaredTo(urgentTarget) <= 2) {
                urgentTarget = null;
            } else {
                Navigation.moveToward(rc, urgentTarget, true);
                return;
            }
        }

        RobotInfo target = findCombatTarget(enemies, me);
        if (target != null) {
            Navigation.moveToward(rc, target.getLocation(), true);
            return;
        }

        if (Navigation.moveTowardEnemyPaint(rc)) {
            return;
        }

        if (enemyTowerHint != null && rc.getRoundNum() - enemyTowerHintRound <= 90) {
            Navigation.moveToward(rc, enemyTowerHint, true);
            return;
        }

        MapLocation symmetryTarget = Navigation.predictEnemyFromAlly(rc, homeTower);
        if (symmetryTarget != null && Navigation.moveToward(rc, symmetryTarget, true)) {
            return;
        }

        Navigation.moveTowardUnpainted(rc);
    }

    private static void retreatFromEnemy(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        MapLocation me = rc.getLocation();
        MapLocation nearest = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            int dist = me.distanceSquaredTo(enemy.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                nearest = enemy.getLocation();
            }
        }

        if (nearest == null) {
            return;
        }

        Direction retreat = me.directionTo(nearest).opposite();
        if (rc.canMove(retreat)) {
            rc.move(retreat);
            return;
        }

        Navigation.moveToward(rc, homeTower == null ? me : homeTower, true);
    }

    private static void tryTransferPaintToAlly(RobotController rc, RobotInfo[] allies) throws GameActionException {
        if (!rc.isActionReady() || rc.getPaint() < 38) {
            return;
        }

        RobotInfo neediest = null;
        double minRatio = 0.5;

        for (RobotInfo ally : allies) {
            if (Clock.getBytecodesLeft() < 2200) {
                break;
            }
            if (ally.getType().isTowerType()) {
                continue;
            }

            double ratio = (double) ally.getPaintAmount() / ally.getType().paintCapacity;
            if (ratio < minRatio && rc.getLocation().distanceSquaredTo(ally.getLocation()) <= 2) {
                minRatio = ratio;
                neediest = ally;
            }
        }

        if (neediest == null) {
            return;
        }

        int toGive = Math.min(rc.getPaint() / 2, neediest.getType().paintCapacity - neediest.getPaintAmount());
        if (toGive > 0 && rc.canTransferPaint(neediest.getLocation(), toGive)) {
            rc.transferPaint(neediest.getLocation(), toGive);
        }
    }
}
