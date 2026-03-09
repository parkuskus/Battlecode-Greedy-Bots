package athillabot2;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class SoldierBot {
    private static final int HARD_REFILL_THRESHOLD = 28;
    private static final int SOFT_REFILL_THRESHOLD = 58;
    private static final int RUIN_IDLE_LIMIT = 12;
    private static final int EXPLORE_IDLE_LIMIT = 14;
    private static final int COVER_MODE_ROUND = 1200;

    private static MapLocation homeTower = null;
    private static MapLocation refillTarget = null;
    private static MapLocation ruinTarget = null;
    private static MapLocation claimedRuinByAlly = null;
    private static int claimedRuinRound = -1000;

    private static MapLocation enemyTowerHint = null;
    private static int enemyTowerHintRound = -1000;

    private static MapLocation srpTarget = null;

    private static MapLocation exploreTarget = null;
    private static int exploreStallTurns = 0;
    private static int lastExploreDist = Integer.MAX_VALUE;

    private static int ruinStallTurns = 0;
    private static int lastRuinDist = Integer.MAX_VALUE;

    private static int lastClaimBroadcast = -1000;
    private static int lastEnemyBroadcast = -1000;

    public static void run(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        initIfNeeded(rc, me);
        Navigation.updateSymmetryModel(rc);
        consumeMessages(rc);

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        boolean inCoverDominantPhase = rc.getRoundNum() >= COVER_MODE_ROUND;

        if (shouldHardRefill(rc)) {
            doRefill(rc);
            return;
        }

        if (!inCoverDominantPhase) {
            if (handleRuinObjective(rc, enemies)) {
                paintCurrentTile(rc);
                return;
            }

            // Soft refill is deferred when we can finish a key objective immediately.
            if (shouldSoftRefill(rc) && !canFinishCriticalObjectiveSoon(rc)) {
                doRefill(rc);
                return;
            }

            if (tryAttackEnemyTower(rc)) {
                paintCurrentTile(rc);
                return;
            }
        }

        if (tryHandleSRP(rc, enemies.length)) {
            paintCurrentTile(rc);
            return;
        }

        doCoverageAndExplore(rc, enemies);

        paintCurrentTile(rc);
        broadcastHighValueEvents(rc);
    }

    private static void initIfNeeded(RobotController rc, MapLocation me) throws GameActionException {
        if (homeTower != null) {
            return;
        }

        homeTower = Navigation.findNearestAllyTower(rc);
        if (homeTower == null) {
            homeTower = me;
        }

        MapLocation symmetryTarget = Navigation.predictEnemyFromAlly(rc, homeTower);
        exploreTarget = (symmetryTarget != null) ? symmetryTarget : me;
    }

    private static void consumeMessages(RobotController rc) throws GameActionException {
        Communication.processMessages(rc, (type, loc, extra, senderID, round) -> {
            if (type == Communication.MSG_RUIN_CLAIM) {
                claimedRuinByAlly = loc;
                claimedRuinRound = round;
            } else if (type == Communication.MSG_ENEMY_TOWER_SEEN) {
                enemyTowerHint = loc;
                enemyTowerHintRound = round;
            }
        });
    }

    private static boolean shouldHardRefill(RobotController rc) {
        return rc.getPaint() <= HARD_REFILL_THRESHOLD;
    }

    private static boolean shouldSoftRefill(RobotController rc) {
        return rc.getPaint() <= SOFT_REFILL_THRESHOLD;
    }

    private static boolean canFinishCriticalObjectiveSoon(RobotController rc) throws GameActionException {
        if (ruinTarget != null) {
            for (UnitType type : new UnitType[] {
                UnitType.LEVEL_ONE_PAINT_TOWER,
                UnitType.LEVEL_ONE_MONEY_TOWER,
                UnitType.LEVEL_ONE_DEFENSE_TOWER
            }) {
                if (rc.canCompleteTowerPattern(type, ruinTarget)) {
                    return true;
                }
            }
        }

        if (srpTarget != null && rc.canCompleteResourcePattern(srpTarget)) {
            return true;
        }

        if (enemyTowerHint != null && rc.canAttack(enemyTowerHint)) {
            return true;
        }

        return false;
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

    private static boolean handleRuinObjective(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        MapLocation me = rc.getLocation();

        if (ruinTarget == null) {
            ruinTarget = Navigation.findNearestRuin(rc, claimedRuinByAlly, claimedRuinRound, 20);
            ruinStallTurns = 0;
            lastRuinDist = Integer.MAX_VALUE;
            if (ruinTarget != null) {
                sendRuinClaim(rc, ruinTarget);
            }
        }

        if (ruinTarget == null) {
            return false;
        }

        if (rc.canSenseLocation(ruinTarget)) {
            RobotInfo occupant = rc.senseRobotAtLocation(ruinTarget);
            if (occupant != null) {
                ruinTarget = null;
                return false;
            }
        }

        int dist = me.distanceSquaredTo(ruinTarget);
        if (dist < lastRuinDist) {
            ruinStallTurns = 0;
            lastRuinDist = dist;
        } else {
            ruinStallTurns++;
        }

        if (ruinStallTurns >= RUIN_IDLE_LIMIT) {
            ruinTarget = null;
            ruinStallTurns = 0;
            return false;
        }

        UnitType towerType = chooseTowerType(rc);

        if (rc.canMarkTowerPattern(towerType, ruinTarget)) {
            rc.markTowerPattern(towerType, ruinTarget);
        }

        paintTowerPatternTiles(rc, ruinTarget);

        if (dist > 2) {
            Navigation.moveToward(rc, ruinTarget, true);
        }

        if (rc.canCompleteTowerPattern(towerType, ruinTarget)) {
            rc.completeTowerPattern(towerType, ruinTarget);
            rc.setTimelineMarker("tower built", 0, 190, 30);
            ruinTarget = null;
            ruinStallTurns = 0;
            return true;
        }

        // If enemies are too close while building, prioritize surviving and cover.
        if (enemies.length >= 4 && dist > 8) {
            ruinTarget = null;
            return false;
        }

        return true;
    }

    private static UnitType chooseTowerType(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

        int money = 0;
        int paint = 0;
        int lowPaintTower = 0;

        for (RobotInfo ally : allies) {
            if (!ally.getType().isTowerType()) {
                continue;
            }
            UnitType base = ally.getType().getBaseType();
            if (base == UnitType.LEVEL_ONE_MONEY_TOWER) {
                money++;
            } else if (base == UnitType.LEVEL_ONE_PAINT_TOWER) {
                paint++;
            }

            if (ally.getPaintAmount() <= 120) {
                lowPaintTower++;
            }
        }

        int round = rc.getRoundNum();
        int enemyCount = rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length;

        if (paint == 0 || lowPaintTower >= 2) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }

        if (enemyCount >= 4 && round > 850) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }

        if (round < 500) {
            return (money <= paint * 2) ? UnitType.LEVEL_ONE_MONEY_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER;
        }

        return (paint <= money) ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    private static void paintTowerPatternTiles(RobotController rc, MapLocation ruinCenter) throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        MapInfo[] pattern = rc.senseNearbyMapInfos(ruinCenter, 8);
        for (MapInfo tile : pattern) {
            if (Clock.getBytecodesLeft() < 2300) {
                return;
            }

            PaintType mark = tile.getMark();
            if (mark == PaintType.EMPTY || mark == tile.getPaint()) {
                continue;
            }

            MapLocation loc = tile.getMapLocation();
            boolean useSecondary = mark == PaintType.ALLY_SECONDARY;
            if (rc.canAttack(loc)) {
                rc.attack(loc, useSecondary);
                return;
            }
        }
    }

    private static boolean tryAttackEnemyTower(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        for (RobotInfo enemy : enemies) {
            if (!enemy.getType().isTowerType()) {
                continue;
            }

            enemyTowerHint = enemy.getLocation();
            enemyTowerHintRound = rc.getRoundNum();
            if (rc.canAttack(enemyTowerHint)) {
                rc.attack(enemyTowerHint);
                return true;
            }
        }

        if (enemyTowerHint != null && rc.getRoundNum() - enemyTowerHintRound <= 80) {
            if (Navigation.moveToward(rc, enemyTowerHint, true)) {
                if (rc.canAttack(enemyTowerHint)) {
                    rc.attack(enemyTowerHint);
                }
                return true;
            }
        }

        return false;
    }

    private static boolean tryHandleSRP(RobotController rc, int enemyCount) throws GameActionException {
        if (!isSRPAllowed(rc, enemyCount)) {
            srpTarget = null;
            return false;
        }

        if (srpTarget == null || !rc.onTheMap(srpTarget)) {
            srpTarget = findNearbySrpCenter(rc);
        }

        if (srpTarget == null) {
            return false;
        }

        if (!rc.getLocation().isWithinDistanceSquared(srpTarget, 2)) {
            Navigation.moveToward(rc, srpTarget, true);
            return true;
        }

        if (rc.canMarkResourcePattern(srpTarget)) {
            rc.markResourcePattern(srpTarget);
        }

        paintMarkedSrpTiles(rc, srpTarget);

        if (rc.canCompleteResourcePattern(srpTarget)) {
            rc.completeResourcePattern(srpTarget);
            srpTarget = null;
            return true;
        }

        return true;
    }

    private static boolean isSRPAllowed(RobotController rc, int enemyCount) {
        if (rc.getRoundNum() < 320) {
            return false;
        }
        if (rc.getNumberTowers() < 6) {
            return false;
        }
        if (enemyCount > 0) {
            return false;
        }
        return rc.getChips() >= 350;
    }

    private static MapLocation findNearbySrpCenter(RobotController rc) {
        MapLocation me = rc.getLocation();
        int baseX = (me.x / 4) * 4 + 2;
        int baseY = (me.y / 4) * 4 + 2;

        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dy = -4; dy <= 4; dy += 4) {
                int x = baseX + dx;
                int y = baseY + dy;
                MapLocation center = new MapLocation(x, y);

                if (!rc.onTheMap(center)) {
                    continue;
                }

                int dist = me.distanceSquaredTo(center);
                if (dist > 20) {
                    continue;
                }

                if (dist < bestDist) {
                    best = center;
                    bestDist = dist;
                }
            }
        }

        return best;
    }

    private static void paintMarkedSrpTiles(RobotController rc, MapLocation center) throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        MapInfo[] tiles = rc.senseNearbyMapInfos(center, 8);
        for (MapInfo tile : tiles) {
            if (Clock.getBytecodesLeft() < 2200) {
                return;
            }

            PaintType mark = tile.getMark();
            if (mark == PaintType.EMPTY || mark == tile.getPaint()) {
                continue;
            }
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc)) {
                continue;
            }

            boolean useSecondary = mark == PaintType.ALLY_SECONDARY;
            rc.attack(loc, useSecondary);
            return;
        }
    }

    private static void doCoverageAndExplore(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapInfo[] nearby = rc.senseNearbyMapInfos();

        MapLocation bestCover = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo tile : nearby) {
            if (Clock.getBytecodesLeft() < 2400) {
                break;
            }

            if (tile.isWall() || tile.hasRuin()) {
                continue;
            }

            PaintType paint = tile.getPaint();
            if (paint.isAlly()) {
                continue;
            }

            MapLocation loc = tile.getMapLocation();
            int score = 0;

            if (paint == PaintType.EMPTY) {
                score += 11;
            } else if (paint.isEnemy()) {
                score += 9;
            }

            int dist = me.distanceSquaredTo(loc);
            score -= dist;

            if (enemyTowerHint != null) {
                score += Math.max(0, 10 - loc.distanceSquaredTo(enemyTowerHint) / 4);
            }

            if (score > bestScore) {
                bestScore = score;
                bestCover = loc;
            }
        }

        if (bestCover != null) {
            if (!me.isWithinDistanceSquared(bestCover, 2)) {
                Navigation.moveToward(rc, bestCover, true);
            }
            if (rc.canAttack(bestCover)) {
                rc.attack(bestCover);
            }
            return;
        }

        MapLocation symmetryTarget = Navigation.predictEnemyFromAlly(rc, homeTower);
        if (symmetryTarget == null) {
            symmetryTarget = me;
        }

        if (exploreTarget == null) {
            exploreTarget = symmetryTarget;
            exploreStallTurns = 0;
            lastExploreDist = Integer.MAX_VALUE;
        }

        int distToExplore = me.distanceSquaredTo(exploreTarget);
        if (distToExplore < lastExploreDist) {
            lastExploreDist = distToExplore;
            exploreStallTurns = 0;
        } else {
            exploreStallTurns++;
        }

        if (distToExplore <= 8 || exploreStallTurns >= EXPLORE_IDLE_LIMIT) {
            exploreTarget = rotateExploreTarget(rc, symmetryTarget);
            lastExploreDist = me.distanceSquaredTo(exploreTarget);
            exploreStallTurns = 0;
        }

        if (!Navigation.moveToward(rc, exploreTarget, true)) {
            Navigation.moveTowardUnpainted(rc);
        }

        // When enemy tower pressure exists, avoid walking deep into enemy paint blindly.
        if (enemies.length >= 3 && !Navigation.isOnAllyPaint(rc)) {
            Navigation.moveToward(rc, homeTower, true);
        }
    }

    private static MapLocation rotateExploreTarget(RobotController rc, MapLocation symmetryTarget) {
        MapLocation me = rc.getLocation();
        int roundBand = (rc.getRoundNum() / 35) % 3;

        if (roundBand == 0) {
            return symmetryTarget;
        }

        if (roundBand == 1) {
            MapLocation diag = new MapLocation(rc.getMapWidth() - 1 - me.x, rc.getMapHeight() - 1 - me.y);
            return Navigation.clampInMap(rc, diag);
        }

        int rx = (RobotPlayer.rng.nextInt(rc.getMapWidth()));
        int ry = (RobotPlayer.rng.nextInt(rc.getMapHeight()));
        return new MapLocation(rx, ry);
    }

    private static void paintCurrentTile(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) {
            return;
        }

        MapLocation me = rc.getLocation();
        MapInfo current = rc.senseMapInfo(me);
        if (!current.getPaint().isAlly() && rc.canAttack(me)) {
            rc.attack(me);
        }
    }

    private static void sendRuinClaim(RobotController rc, MapLocation ruinLoc) throws GameActionException {
        int round = rc.getRoundNum();
        if (round - lastClaimBroadcast < 12) {
            return;
        }

        int msg = Communication.encode(Communication.MSG_RUIN_CLAIM, ruinLoc);
        if (Communication.sendHighValueMessage(rc, msg)) {
            lastClaimBroadcast = round;
        }
    }

    private static void broadcastHighValueEvents(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        if (enemyTowerHint != null && round - enemyTowerHintRound <= 60 && round - lastEnemyBroadcast >= 12) {
            int msg = Communication.encode(Communication.MSG_ENEMY_TOWER_SEEN, enemyTowerHint);
            if (Communication.sendHighValueMessage(rc, msg)) {
                lastEnemyBroadcast = round;
            }
        }
    }
}
