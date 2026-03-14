package botathilla5_1;

import battlecode.common.*;

/**
 * Soldier — tower builder + attacker + SRP builder + painter.
 *
 * v5 changes from v3:
 * 1. Mark-based painting: use getMark() instead of hardcoded pattern arrays
 * 2. Only mark patterns once (check if already marked before calling markTowerPattern)
 * 3. Fix stale `me` — refresh after any movement
 * 4. findVisibleTowerLoc: accept ANY tower for refill, prefer paint towers
 * 5. Combat threshold 15% → 30% (enter combat with more paint)
 * 6. Prime scatter for explore: myID * 37 instead of plain myID
 * 7. Removed Math.sqrt — use distanceSquared directly
 * 8. SRP chip guard: don't bankrupt for SRP
 */
public class SoldierBot {

    private static MapLocation spawnTower = null;
    private static Direction exploreDir = null;
    private static MapLocation exploreLoc = null;
    private static int exploreSetRound = 0;

    private static final double REFILL_LOW = 0.09;
    private static final double REFILL_HIGH = 0.42;
    private static final int REFILL_MAX_DIST = 100;
    private static final int SRP_TOWER_MIN = 3;

    private enum State { EXPLORE, REFILL, COMBAT, BUILD_TOWER, BUILD_SRP }
    private static State state = State.EXPLORE;

    private static MapLocation bestRuin = null;
    private static MapLocation bestSRP = null;

    // SRP pattern kept for findBestSRP validation only (mark-based painting used for actual painting)
    private static final int[][] SRP_PATTERN = {
        {2,2,1,2,2},{2,1,1,1,2},{1,1,2,1,1},{2,1,1,1,2},{2,2,1,2,2}
    };

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();

        if (spawnTower == null) {
            initSpawnTower(rc, me);
            // Prime scatter: myID * 37 reduces clustering vs plain myID
            exploreDir = RobotPlayer.DIRS[(RobotPlayer.myID * 37) % 8];
            exploreLoc = Nav.pickExploreTarget(me, RobotPlayer.myID * 37 + rc.getRoundNum());
            exploreSetRound = rc.getRoundNum();
        }

        Messaging.readMessages();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        // Track known towers
        for (RobotInfo a : allies)
            if (a.type.isTowerType()) Messaging.registerTower(a.location, a.type);
        Messaging.relayToNearbyTower(allies);

        // Greedy state decision
        state = greedyDecide(rc, me, nearby, allies, enemies);

        switch (state) {
            case REFILL      -> refillState(rc, me, allies);
            case COMBAT      -> combatState(rc, me, enemies);
            case BUILD_TOWER -> buildTowerState(rc, me, nearby, enemies);
            case BUILD_SRP   -> buildSRPState(rc, me, nearby);
            case EXPLORE     -> exploreState(rc, me, nearby, enemies);
        }

        // Refresh me after potential movement
        me = rc.getLocation();

        // Selalu: paint di bawah kaki + paint petak terdekat (coverage focus)
        paintUnderSelf(rc, me);
        if (state != State.REFILL) paintNearbyEmpty(rc, rc.senseNearbyMapInfos());
        // Selalu coba complete pattern yang kebetulan sudah selesai
        confirmPatterns(rc, me);
        Nav.recordPosition(me);
    }

    // ======== GREEDY DECISION ========

    private static State greedyDecide(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {

        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;

        MapLocation refillTower = findVisibleTowerLoc(allies);
        if (refillTower == null) refillTower = Messaging.nearestRefillTower(me);
        RobotInfo enemyTower = Nav.nearestEnemyTower(enemies);

        bestRuin = findBestRuin(rc, me, nearby, allies);
        bestSRP = null;
        if (Messaging.towerCount >= SRP_TOWER_MIN && Messaging.countPaintTowers() > 0
                && enemies.length == 0 && rc.getChips() >= 120) {
            bestSRP = findBestSRP(rc, me);
        }

        double[] score = new double[5];
        boolean[] feasible = new boolean[5];
        int[] dist = new int[5];
        int[] tie = new int[5];

        int refillDist = refillTower == null ? 999 : me.distanceSquaredTo(refillTower);
        feasible[1] = refillTower != null
                && ((state != State.REFILL && paintRatio < REFILL_LOW)
                || (state == State.REFILL && paintRatio < REFILL_HIGH))
                && refillDist <= REFILL_MAX_DIST;
        score[1] = GreedyCore.score((1.0 - paintRatio) * 4.4, 0, 2.2, 2.8, 0.2, 0.3, refillDist * 0.02);
        dist[1] = refillDist;
        tie[1] = GreedyCore.tieId(refillTower);

        int towerDist = enemyTower == null ? 999 : me.distanceSquaredTo(enemyTower.location);
        feasible[2] = enemyTower != null && rc.getHealth() > 55 && paintRatio > 0.42;
        score[2] = GreedyCore.score(0.6, 0.8, 2.1, 0.5, 0.2, 1.8, towerDist * 0.015);
        dist[2] = towerDist;
        tie[2] = enemyTower == null ? Integer.MAX_VALUE : enemyTower.ID;

        int ruinDist = bestRuin == null ? 999 : me.distanceSquaredTo(bestRuin);
        feasible[3] = bestRuin != null && rc.getChips() >= 200;
        score[3] = GreedyCore.score(2.0, 0.4, 6.2 - ruinDist * 0.02, 2.5, 0.8, 0.5, 1.0);
        dist[3] = ruinDist;
        tie[3] = GreedyCore.tieId(bestRuin);

        int srpDist = bestSRP == null ? 999 : me.distanceSquaredTo(bestSRP);
        feasible[4] = bestSRP != null;
        score[4] = GreedyCore.score(1.8, 0.2, 5.1 - srpDist * 0.03, 2.0, 0.4, 0.4, 1.0);
        dist[4] = srpDist;
        tie[4] = GreedyCore.tieId(bestSRP);

        feasible[0] = true;
        score[0] = GreedyCore.score(2.0, 0.4, 2.0, 1.1, 0.4, 0.4, 0.6);
        dist[0] = exploreLoc == null ? 999 : me.distanceSquaredTo(exploreLoc);
        tie[0] = GreedyCore.tieId(exploreLoc);

        int[] rejected = new int[1];
        double[] second = new double[1];
        int idx = GreedyCore.pickBest(score, feasible, dist, tie, rejected, second);
        if (idx < 0) idx = 0;

        State next = switch (idx) {
            case 1 -> State.REFILL;
            case 2 -> State.COMBAT;
            case 3 -> State.BUILD_TOWER;
            case 4 -> State.BUILD_SRP;
            default -> State.EXPLORE;
        };
        GreedyCore.traceDecision(rc, "SOLDIER", "STATE", next.name(), score[idx], second[0], rejected[0]);
        return next;
    }

    // ======== STATE HANDLERS ========

    private static void refillState(RobotController rc, MapLocation me, RobotInfo[] allies) throws GameActionException {
        // v5: accept any tower type for refill, prefer paint towers
        MapLocation target = findVisibleTowerLoc(allies);
        if (target == null) target = Messaging.nearestRefillTower(me);
        if (target == null) target = spawnTower != null ? spawnTower : me;

        if (me.distanceSquaredTo(target) <= 2) {
            int space = rc.getType().paintCapacity - rc.getPaint();
            if (space > 0 && rc.canTransferPaint(target, -space))
                rc.transferPaint(target, -space);
        } else {
            Nav.bugNav(target);
        }
    }

    private static void combatState(RobotController rc, MapLocation me, RobotInfo[] enemies) throws GameActionException {
        RobotInfo tower = Nav.nearestEnemyTower(enemies);
        if (tower == null) { state = State.EXPLORE; return; }

        if (me.distanceSquaredTo(tower.location) <= rc.getType().actionRadiusSquared) {
            if (rc.canAttack(tower.location)) rc.attack(tower.location);
            Direction away = me.directionTo(tower.location).opposite();
            Nav.fuzzyMove(away);
        } else {
            Nav.moveIntoRange(tower.location, rc.getType().actionRadiusSquared);
            // Refresh me after move
            me = rc.getLocation();
            if (rc.canAttack(tower.location)) rc.attack(tower.location);
        }
    }

    /**
     * BUILD TOWER — v5: mark-based painting using getMark().
     * Mark pattern once, then paint tiles where getMark() != getPaint().
     */
    private static void buildTowerState(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        if (bestRuin == null) { state = State.EXPLORE; return; }

        UnitType towerType = decideTowerType(rc, bestRuin, enemies);

        // v5: Only mark if not already marked (check a tile near ruin)
        boolean needsMark = false;
        MapLocation checkTile = bestRuin.translate(1, 0);
        if (rc.canSenseLocation(checkTile)) {
            MapInfo checkInfo = rc.senseMapInfo(checkTile);
            if (!checkInfo.hasRuin() && !checkInfo.isWall() && checkInfo.getMark() == PaintType.EMPTY) {
                needsMark = true;
            }
        } else {
            needsMark = true; // can't see, try marking anyway
        }
        if (needsMark && rc.canMarkTowerPattern(towerType, bestRuin))
            rc.markTowerPattern(towerType, bestRuin);

        // v5: Mark-based painting — paint tiles where getMark() != getPaint()
        paintMarkedTiles(rc, bestRuin);

        // Gerak menuju ruin
        if (me.distanceSquaredTo(bestRuin) > 8) Nav.bugNav(bestRuin);
        else if (me.distanceSquaredTo(bestRuin) > 2) Nav.fuzzyMove(bestRuin);

        // Try complete semua tipe
        for (UnitType t : new UnitType[]{UnitType.LEVEL_ONE_PAINT_TOWER,
                UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
            if (rc.canCompleteTowerPattern(t, bestRuin)) {
                rc.completeTowerPattern(t, bestRuin);
                Messaging.registerTower(bestRuin, t);
                broadcastTowerBuilt(rc, bestRuin, t);
                break;
            }
        }
    }

    /**
     * BUILD SRP — v5: mark-based painting using getMark().
     */
    private static void buildSRPState(RobotController rc, MapLocation me, MapInfo[] nearby) throws GameActionException {
        if (bestSRP == null) { state = State.EXPLORE; return; }

        // Mark SRP pattern if not already marked
        boolean needsMark = false;
        MapLocation checkTile = bestSRP.translate(1, 0);
        if (rc.canSenseLocation(checkTile)) {
            MapInfo checkInfo = rc.senseMapInfo(checkTile);
            if (!checkInfo.isWall() && checkInfo.getMark() == PaintType.EMPTY) {
                needsMark = true;
            }
        } else {
            needsMark = true;
        }
        if (needsMark && rc.canMarkResourcePattern(bestSRP))
            rc.markResourcePattern(bestSRP);

        if (!me.equals(bestSRP)) Nav.fuzzyMove(bestSRP);

        // v5: Mark-based SRP painting
        paintMarkedTiles(rc, bestSRP);

        // Refresh me after move
        me = rc.getLocation();
        if (me.distanceSquaredTo(bestSRP) <= 2 && rc.canCompleteResourcePattern(bestSRP))
            rc.completeResourcePattern(bestSRP);
    }

    private static void exploreState(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        int round = rc.getRoundNum();
        if (me.distanceSquaredTo(exploreLoc) <= 8 || (round - exploreSetRound) > 30 || Nav.isLikelyStuck(me, 4)) {
            // v5: prime scatter
            exploreDir = RobotPlayer.DIRS[(RobotPlayer.myID * 37 + round / 30) % 8];
            exploreLoc = Nav.pickExploreTarget(me, RobotPlayer.myID * 37 + rc.getRoundNum());
            exploreSetRound = round;
        }
        if (!rc.isMovementReady()) return;

        MapLocation closestFrontier = null, closestEmpty = null;
        int cfDist = Integer.MAX_VALUE, ceDist = Integer.MAX_VALUE;
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 2000) break;
            if (info.getPaint() == PaintType.EMPTY && !info.isWall() && !info.hasRuin()) {
                MapLocation loc = info.getMapLocation();
                int d = me.distanceSquaredTo(loc);
                if (d < ceDist) { ceDist = d; closestEmpty = loc; }
                if (d < cfDist && d > 0) {
                    boolean nearAlly = false;
                    for (Direction dd : RobotPlayer.DIRS) {
                        MapLocation adj = loc.add(dd);
                        if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) {
                            nearAlly = true; break;
                        }
                    }
                    if (nearAlly) { cfDist = d; closestFrontier = loc; }
                }
            }
        }

        if (closestFrontier != null) Nav.fuzzyMove(closestFrontier);
        else if (closestEmpty != null) Nav.fuzzyMove(closestEmpty);
        else Nav.bugNav(exploreLoc);
    }

    // ======== TOWER TYPE DECISION ========

    private static UnitType decideTowerType(RobotController rc, MapLocation ruin, RobotInfo[] enemies) {
        boolean enemyNearby = false;
        for (RobotInfo e : enemies) {
            if (ruin.distanceSquaredTo(e.location) <= 36) { enemyNearby = true; break; }
        }

        if (enemyNearby && Messaging.countDefenseTowers() < 3) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }

        int money = Messaging.countMoneyTowers();
        int paint = Messaging.countPaintTowers();
        if (paint == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (money <= paint) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    // ======== PAINTING HELPERS ========

    private static void paintUnderSelf(RobotController rc, MapLocation me) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo myInfo = rc.senseMapInfo(me);
        PaintType p = myInfo.getPaint();
        if (p == PaintType.EMPTY || p.isEnemy()) {
            // Check if there's a mark — paint correct color
            PaintType mark = myInfo.getMark();
            boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
            if (rc.canAttack(me)) rc.attack(me, useSecondary);
        }
    }

    private static void paintNearbyEmpty(RobotController rc, MapInfo[] nearby) throws GameActionException {
        if (!rc.isActionReady()) return;
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1500) break;
            MapLocation loc = info.getMapLocation();
            if (info.getPaint() == PaintType.EMPTY && !info.isWall() && !info.hasRuin()
                && rc.canAttack(loc)) {
                // Respect marks if present
                PaintType mark = info.getMark();
                boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                rc.attack(loc, useSecondary); break;
            }
        }
    }

    /**
     * v5: Mark-based painting — paint tiles where getMark() differs from getPaint().
     * Works for both tower patterns and SRP patterns.
     */
    private static void paintMarkedTiles(RobotController rc, MapLocation center) throws GameActionException {
        if (!rc.isActionReady()) return;
        for (MapInfo tile : rc.senseNearbyMapInfos(center, 8)) {
            if (Clock.getBytecodesLeft() < 1500) return;
            PaintType mark = tile.getMark();
            if (mark == PaintType.EMPTY) continue; // no mark here
            PaintType paint = tile.getPaint();
            if (mark == paint) continue; // already correct
            if (tile.hasRuin() || tile.isWall()) continue;
            MapLocation loc = tile.getMapLocation();
            boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
            if (rc.canAttack(loc)) {
                rc.attack(loc, useSecondary); return;
            }
        }
    }

    // ======== RUIN/SRP FINDING ========

    /**
     * Greedy ruin selection: prioritas ruin yang paling hampir selesai.
     * v5: removed Math.sqrt, use distanceSquared directly in scoring.
     */
    private static MapLocation findBestRuin(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies) throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        MapLocation best = null; int bestScore = -9999;
        for (MapLocation ruin : ruins) {
            if (Clock.getBytecodesLeft() < 3000) break;
            if (rc.senseRobotAtLocation(ruin) != null) continue;
            int correct = 0, total = 0, enemy = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    MapLocation tile = ruin.translate(dx, dy);
                    if (!rc.canSenseLocation(tile)) continue;
                    MapInfo info = rc.senseMapInfo(tile);
                    if (info.hasRuin() || info.isWall()) continue;
                    total++;
                    if (info.getPaint().isAlly()) correct++;
                    else if (info.getPaint().isEnemy()) enemy++;
                }
            }
            if (enemy > 0 && total > 0 && correct < total * 3 / 4) continue;
            int soldiers = 0;
            for (RobotInfo a : allies)
                if (a.type == UnitType.SOLDIER && a.location.distanceSquaredTo(ruin) <= 8) soldiers++;
            if (soldiers >= 3) continue;
            int missing = total - correct;
            // v5: use distanceSquared / 4 instead of Math.sqrt
            int distPenalty = me.distanceSquaredTo(ruin) / 4;
            int score = correct * 3 - missing * 2 - distPenalty;
            if (score > bestScore) { bestScore = score; best = ruin; }
        }
        return best;
    }

    private static MapLocation findBestSRP(RobotController rc, MapLocation me) throws GameActionException {
        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dy = -4; dy <= 4; dy += 4) {
                if (Clock.getBytecodesLeft() < 3000) return null;
                MapLocation center = new MapLocation(
                    ((me.x + dx + 2) / 4) * 4 + 2,
                    ((me.y + dy + 2) / 4) * 4 + 2);
                if (!rc.onTheMap(center)) continue;
                if (me.distanceSquaredTo(center) > GameConstants.RESOURCE_PATTERN_RADIUS_SQUARED) continue;
                if (!rc.canMarkResourcePattern(center)) continue;
                boolean bad = false, allDone = true;
                for (int x = -2; x <= 2 && !bad; x++) {
                    for (int y = -2; y <= 2 && !bad; y++) {
                        MapLocation tile = center.translate(x, y);
                        if (!rc.canSenseLocation(tile)) { allDone = false; continue; }
                        MapInfo info = rc.senseMapInfo(tile);
                        if (info.getPaint().isEnemy()) { bad = true; break; }
                        boolean wantSec = SRP_PATTERN[x+2][y+2] == 2;
                        PaintType want = wantSec ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                        if (info.getPaint() != want) allDone = false;
                    }
                }
                if (!bad && !allDone) return center;
            }
        }
        return null;
    }

    // ======== UTILITY ========

    /**
     * v5: Accept ANY tower type for refill, prefer paint towers.
     * Also check paintAmount > 30 before considering a tower.
     */
    private static MapLocation findVisibleTowerLoc(RobotInfo[] allies) {
        RobotInfo bestPaint = null; int bpd = Integer.MAX_VALUE;
        RobotInfo bestAny = null; int bad = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && a.paintAmount > 30) {
                int d = RobotPlayer.rc.getLocation().distanceSquaredTo(a.location);
                if (a.type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                    if (d < bpd) { bpd = d; bestPaint = a; }
                }
                if (d < bad) { bad = d; bestAny = a; }
            }
        }
        if (bestPaint != null) return bestPaint.location;
        if (bestAny != null) return bestAny.location;
        return null;
    }

    private static void initSpawnTower(RobotController rc, MapLocation me) throws GameActionException {
        spawnTower = me;
        for (RobotInfo r : rc.senseNearbyRobots(4, RobotPlayer.myTeam)) {
            if (r.type.isTowerType()) {
                int d = me.distanceSquaredTo(r.location);
                if (spawnTower.equals(me) || d < me.distanceSquaredTo(spawnTower))
                    spawnTower = r.location;
            }
        }
    }

    private static void broadcastTowerBuilt(RobotController rc, MapLocation loc, UnitType type) throws GameActionException {
        for (RobotInfo a : rc.senseNearbyRobots(-1, RobotPlayer.myTeam)) {
            if (a.type.isTowerType() && rc.canSendMessage(a.location)) {
                rc.sendMessage(a.location, Messaging.encodeTowerBuilt(loc, type));
                break;
            }
        }
    }

    private static void confirmPatterns(RobotController rc, MapLocation me) throws GameActionException {
        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            for (UnitType t : new UnitType[]{UnitType.LEVEL_ONE_PAINT_TOWER,
                    UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
                if (rc.canCompleteTowerPattern(t, ruin)) {
                    rc.completeTowerPattern(t, ruin);
                    Messaging.registerTower(ruin, t); break;
                }
            }
        }
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dy = -2; dy <= 2; dy += 4) {
                MapLocation c = new MapLocation(((me.x + dx + 2) / 4) * 4 + 2, ((me.y + dy + 2) / 4) * 4 + 2);
                if (me.distanceSquaredTo(c) <= 2 && rc.canCompleteResourcePattern(c))
                    rc.completeResourcePattern(c);
            }
        }
    }
}






