package alternative_bots_2;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Greedy Soldier — coverage-first explorer & builder.
 *
 * Priority:
 *  1. REFILL  — retreat when paint < 15% (aggressive low threshold to maximize painting time)
 *  2. COMBAT  — attack enemy towers if healthy and reachable (not defense towers)
 *  3. BUILD   — build tower at most-complete ruin (greedy completion)
 *  4. SRP     — build Special Resource Pattern when safe and enough towers
 *  5. COVER   — paint tiles & explore toward empty frontier (sector-partitioned)
 *
 * Sector partitioning: map divided into sectors (3 cols × 2 rows = 6 sectors).
 * Each soldier assigned to a sector by myID % 6. Soldiers prefer exploring their
 * own sector to minimize overlap and maximize total coverage.
 */
public class SoldierBot {

    private static MapLocation spawnTower = null;
    private static Direction   exploreDir = null;
    private static MapLocation exploreLoc = null;

    // Aggressive paint thresholds
    private static final double REFILL_LOW  = 0.20;
    private static final double REFILL_HIGH = 0.75;
    private static final int SRP_TOWER_MIN = 4;
    private static final int ENDGAME_ROUND = 1500; // pure coverage mode after this

    // Sector-based exploration
    private static final int SECTOR_COLS = 3;
    private static final int SECTOR_ROWS = 2;
    private static int mySector = -1;

    private enum State { REFILL, COMBAT, BUILD, SRP, COVER }
    private static State state = State.COVER;

    // SRP paint pattern (5×5, 1=primary, 2=secondary)
    private static final int[][] SRP_PATTERN = {
        {2,2,1,2,2},
        {2,1,1,1,2},
        {1,1,2,1,1},
        {2,1,1,1,2},
        {2,2,1,2,2}
    };

    // Tower paint patterns
    private static final int[][] PAINT_TOWER_PATTERN = {
        {2,1,1,1,2},{1,2,1,2,1},{1,1,2,1,1},{1,2,1,2,1},{2,1,1,1,2}
    };
    private static final int[][] MONEY_TOWER_PATTERN = {
        {1,2,2,2,1},{2,2,1,2,2},{2,1,1,1,2},{2,2,1,2,2},{1,2,2,2,1}
    };
    private static final int[][] DEFENSE_TOWER_PATTERN = {
        {1,1,2,1,1},{1,2,2,2,1},{2,2,2,2,2},{1,2,2,2,1},{1,1,2,1,1}
    };

    private static MapLocation bestRuin = null;
    private static MapLocation bestSRP  = null;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();

        // First turn init
        if (spawnTower == null) {
            initSpawn(rc, me);
            mySector = RobotPlayer.myID % (SECTOR_COLS * SECTOR_ROWS);
            exploreDir = spawnTower.directionTo(me);
            if (exploreDir == Direction.CENTER)
                exploreDir = RobotPlayer.DIRS[RobotPlayer.myID % 8];
            exploreLoc = sectorCenter();
        }

        // Sense & communicate
        Comms.readMessages();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);
        Comms.updateFromVisible(allies);
        Comms.relayToNearbyTower(allies);

        // Greedy state selection
        state = greedyDecide(rc, me, nearby, allies, enemies);

        // Execute
        switch (state) {
            case REFILL -> doRefill(rc, me, allies);
            case COMBAT -> doCombat(rc, me, enemies);
            case BUILD  -> doBuild(rc, me, nearby, enemies);
            case SRP    -> doSRP(rc, me, nearby);
            case COVER  -> doCover(rc, me, nearby, enemies);
        }

        // Invariant: always paint under self + paint nearby empty tiles
        paintUnderSelf(rc, me);
        if (state != State.REFILL) paintNearby(rc, me, nearby);
        confirmPatterns(rc, me);
    }

    // Greedy State Selection

    private static State greedyDecide(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies)
            throws GameActionException {

        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;

        // Refill: 15% enter, 75% exit (aggressive — stay painting longer)
        if ((state != State.REFILL && paintRatio < REFILL_LOW)
         || (state == State.REFILL && paintRatio < REFILL_HIGH)) {
            return State.REFILL;
        }

        int round = rc.getRoundNum();

        // ENDGAME: pure coverage mode — ignore combat/build, just paint
        if (round >= ENDGAME_ROUND) {
            return State.COVER;
        }

        // Combat: attack enemy towers (skip defense towers — too risky)
        RobotInfo enemyTower = Nav.nearestEnemyTower(enemies);
        if (enemyTower != null && rc.getHealth() > 40
            && enemyTower.type.getBaseType() != UnitType.LEVEL_ONE_DEFENSE_TOWER) {
            return State.COMBAT;
        }

        // Build tower: pick most-complete ruin
        boolean canAffordTower = rc.getChips() >= 700 || rc.getNumberTowers() < 5;
        bestRuin = findBestRuin(rc, me, nearby, allies);
        if (bestRuin != null && canAffordTower) {
            return State.BUILD;
        }

        // SRP: only when safe and enough infrastructure
        if (Comms.towerCount >= SRP_TOWER_MIN && Comms.countPaintTowers() > 0
            && enemies.length == 0) {
            bestSRP = findBestSRP(rc, me);
            if (bestSRP != null) return State.SRP;
        }

        return State.COVER;
    }

    // State Handlers

    private static void doRefill(RobotController rc, MapLocation me,
            RobotInfo[] allies) throws GameActionException {
        // Find nearest visible paint tower first
        RobotInfo nearestTower = null;
        int nearDist = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && a.paintAmount > 30) {
                int d = me.distanceSquaredTo(a.location);
                if (d < nearDist) { nearDist = d; nearestTower = a; }
            }
        }

        MapLocation target;
        if (nearestTower != null) {
            target = nearestTower.location;
        } else {
            MapLocation pt = Comms.nearestPaintTower(me);
            target = pt != null ? pt : (spawnTower != null ? spawnTower : me);
        }

        if (me.distanceSquaredTo(target) <= 2) {
            if (nearestTower != null) {
                int amt = Math.min(rc.getType().paintCapacity - rc.getPaint(),
                                   nearestTower.paintAmount);
                if (amt > 0 && rc.canTransferPaint(target, -amt))
                    rc.transferPaint(target, -amt);
            }
            if ((double) rc.getPaint() / rc.getType().paintCapacity >= REFILL_HIGH)
                state = State.COVER;
        } else {
            Nav.bugNav(target);
        }
    }

    private static void doCombat(RobotController rc, MapLocation me,
            RobotInfo[] enemies) throws GameActionException {
        RobotInfo tower = Nav.nearestEnemyTower(enemies);
        if (tower == null) { state = State.COVER; return; }

        if (me.distanceSquaredTo(tower.location) <= rc.getType().actionRadiusSquared) {
            if (rc.canAttack(tower.location)) rc.attack(tower.location);
            // retreat after attack to avoid damage
            Direction away = me.directionTo(tower.location).opposite();
            Nav.fuzzyMove(away);
        } else {
            Nav.moveIntoRange(tower.location, rc.getType().actionRadiusSquared);
            if (rc.canAttack(tower.location)) rc.attack(tower.location);
        }
    }

    private static void doBuild(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        if (bestRuin == null) { state = State.COVER; return; }

        UnitType towerType = decideTowerType(rc);

        // Mark pattern
        if (rc.canMarkTowerPattern(towerType, bestRuin))
            rc.markTowerPattern(towerType, bestRuin);

        // Paint ruin tiles with correct pattern
        paintRuinTiles(rc, me, bestRuin, towerType);

        // Move toward ruin
        if (me.distanceSquaredTo(bestRuin) > 8) {
            Nav.bugNav(bestRuin);
        } else if (me.distanceSquaredTo(bestRuin) > 2) {
            Nav.fuzzyMove(bestRuin);
        }

        // Try completing
        tryCompleteTower(rc, bestRuin);
    }

    private static void doSRP(RobotController rc, MapLocation me,
            MapInfo[] nearby) throws GameActionException {
        if (bestSRP == null) { state = State.COVER; return; }

        if (!me.equals(bestSRP)) {
            Nav.fuzzyMove(bestSRP);
        }
        paintSRPTiles(rc, me, bestSRP);

        if (me.distanceSquaredTo(bestSRP) <= 2 && rc.canCompleteResourcePattern(bestSRP))
            rc.completeResourcePattern(bestSRP);
    }

    private static void doCover(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {

        // Score all visible empty tiles by coverage value
        // Sector bonus: prefer my sector (+8), enemy paint (+6)
        MapLocation bestCover = null;
        int bestCoverScore = Integer.MIN_VALUE;

        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 2000) break;
            MapLocation loc = info.getMapLocation();
            if (info.isWall() || info.hasRuin()) continue;

            PaintType p = info.getPaint();
            if (p.isAlly()) continue; // skip already painted

            int d = me.distanceSquaredTo(loc);
            int score = 0;

            if (p == PaintType.EMPTY) score += 10;
            if (p.isEnemy()) score += 6; // flipping enemy paint is valuable
            if (belongsToMySector(loc)) score += 8; // sector bonus for spread
            score -= d; // prefer closer tiles

            if (score > bestCoverScore) {
                bestCoverScore = score;
                bestCover = loc;
            }
        }

        if (bestCover != null && me.distanceSquaredTo(bestCover) > 4 && rc.isActionReady()) {
            Nav.safeFuzzyMove(bestCover, enemies);
        }

        // Explore toward new territory
        if (bestCover == null || me.distanceSquaredTo(exploreLoc) <= 8) {
            // Cycle: sector center → diagonal enemy → random edge
            int phase = (rc.getRoundNum() / 30) % 3;
            if (phase == 0) {
                exploreLoc = sectorCenter();
            } else if (phase == 1) {
                // Move toward enemy diagonal (where enemy territory likely is)
                exploreLoc = new MapLocation(
                    RobotPlayer.mapW - me.x - 1,
                    RobotPlayer.mapH - me.y - 1);
            } else {
                exploreDir = RobotPlayer.DIRS[(RobotPlayer.myID + rc.getRoundNum() / 30) % 8];
                exploreLoc = Nav.extendToEdge(me, exploreDir);
            }
        }

        if (rc.isMovementReady()) {
            Nav.bugNav(exploreLoc);
        }
    }

    // Painting Helpers

    private static void paintUnderSelf(RobotController rc, MapLocation me)
            throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo mi = rc.senseMapInfo(me);
        if (mi.getPaint() == PaintType.EMPTY && rc.canAttack(me))
            rc.attack(me);
    }

    private static void paintNearby(RobotController rc, MapLocation me,
            MapInfo[] nearby) throws GameActionException {
        if (!rc.isActionReady()) return;
        // Greedy: paint the nearest empty tile in action range
        MapLocation bestLoc = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1500) break;
            MapLocation loc = info.getMapLocation();
            if (info.getPaint() == PaintType.EMPTY
                && !info.isWall() && !info.hasRuin()
                && rc.canAttack(loc)) {
                int d = me.distanceSquaredTo(loc);
                if (d < bestDist) { bestDist = d; bestLoc = loc; }
            }
        }
        if (bestLoc != null) rc.attack(bestLoc);
    }

    private static void paintRuinTiles(RobotController rc, MapLocation me,
            MapLocation ruin, UnitType type) throws GameActionException {
        if (!rc.isActionReady()) return;
        int[][] pattern = getPattern(type);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (Clock.getBytecodesLeft() < 1500) return;
                MapLocation tile = ruin.translate(dx, dy);
                if (!rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (info.hasRuin() || info.isWall()) continue;
                boolean wantSec = pattern[dx + 2][dy + 2] == 2;
                PaintType want = wantSec ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                if (info.getPaint() != want && !info.getPaint().isEnemy()
                    && rc.canAttack(tile)) {
                    rc.attack(tile, wantSec);
                    return; // one paint per turn
                }
            }
        }
    }

    private static void paintSRPTiles(RobotController rc, MapLocation me,
            MapLocation center) throws GameActionException {
        if (!rc.isActionReady()) return;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (Clock.getBytecodesLeft() < 1500) return;
                MapLocation tile = center.translate(dx, dy);
                if (!rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (info.hasRuin() || info.isWall()) continue;
                boolean wantSec = SRP_PATTERN[dx + 2][dy + 2] == 2;
                PaintType want = wantSec ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                if (info.getPaint() != want && !info.getPaint().isEnemy()
                    && rc.canAttack(tile)) {
                    rc.attack(tile, wantSec);
                    return;
                }
            }
        }
    }

    // Greedy Ruin / SRP / Tower Finding

    /** Find ruin with most ally paint already placed (closest to completion). */
    private static MapLocation findBestRuin(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies) throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        MapLocation best = null;
        int bestCorrect = -1;

        for (MapLocation ruin : ruins) {
            if (rc.senseRobotAtLocation(ruin) != null) continue; // already has tower

            boolean hasEnemyPaint = false;
            int correctPaint = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    MapLocation tile = ruin.translate(dx, dy);
                    if (!rc.canSenseLocation(tile)) continue;
                    MapInfo info = rc.senseMapInfo(tile);
                    if (info.getPaint().isEnemy()) { hasEnemyPaint = true; break; }
                    if (info.getPaint().isAlly()) correctPaint++;
                }
                if (hasEnemyPaint) break;
            }
            if (hasEnemyPaint) continue;

            // Skip if 2+ soldiers already working on it
            int soldiers = 0;
            for (RobotInfo a : allies) {
                if (a.type == UnitType.SOLDIER && a.location.distanceSquaredTo(ruin) <= 4)
                    soldiers++;
            }
            if (soldiers >= 2) continue;

            // Greedy: pick ruin with MOST correct paint
            if (correctPaint > bestCorrect) {
                bestCorrect = correctPaint;
                best = ruin;
            }
        }
        return best;
    }

    /* Find closest completable SRP center. */
    private static MapLocation findBestSRP(RobotController rc, MapLocation me)
            throws GameActionException {
        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dy = -4; dy <= 4; dy += 4) {
                if (Clock.getBytecodesLeft() < 3000) return null;
                MapLocation center = new MapLocation(
                    ((me.x + dx + 2) / 4) * 4 + 2,
                    ((me.y + dy + 2) / 4) * 4 + 2);
                if (!rc.onTheMap(center)) continue;
                if (me.distanceSquaredTo(center) > GameConstants.RESOURCE_PATTERN_RADIUS_SQUARED)
                    continue;
                if (!rc.canMarkResourcePattern(center)) continue;

                boolean bad = false;
                boolean allDone = true;
                for (int x = -2; x <= 2 && !bad; x++) {
                    for (int y = -2; y <= 2 && !bad; y++) {
                        MapLocation tile = center.translate(x, y);
                        if (!rc.canSenseLocation(tile)) { allDone = false; continue; }
                        MapInfo info = rc.senseMapInfo(tile);
                        if (info.getPaint().isEnemy()) { bad = true; break; }
                        boolean wantSec = SRP_PATTERN[x + 2][y + 2] == 2;
                        PaintType want = wantSec ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                        if (info.getPaint() != want) allDone = false;
                    }
                }
                if (!bad && !allDone) return center;
            }
        }
        return null;
    }

    /* Money first (2.5:1 ratio early, 1.5:1 later), then paint. */
    private static UnitType decideTowerType(RobotController rc) {
        int money = Comms.countMoneyTowers();
        int paint = Comms.countPaintTowers();
        double ratio = money < 6 ? 2.5 : 1.5;
        if (money < Math.max(1, paint) * ratio && rc.getChips() < 4000)
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    private static void tryCompleteTower(RobotController rc, MapLocation ruin)
            throws GameActionException {
        for (UnitType t : new UnitType[]{
                UnitType.LEVEL_ONE_PAINT_TOWER,
                UnitType.LEVEL_ONE_MONEY_TOWER,
                UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
            if (rc.canCompleteTowerPattern(t, ruin)) {
                rc.completeTowerPattern(t, ruin);
                Comms.registerTower(ruin, t);
                // Notify a nearby tower
                for (RobotInfo a : rc.senseNearbyRobots(-1, RobotPlayer.myTeam)) {
                    if (a.type.isTowerType() && rc.canSendMessage(a.location)) {
                        rc.sendMessage(a.location, Comms.encodeTowerBuilt(ruin, t));
                        break;
                    }
                }
                break;
            }
        }
    }

    private static void confirmPatterns(RobotController rc, MapLocation me)
            throws GameActionException {
        // Confirm any completable tower patterns nearby
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        for (MapLocation ruin : ruins) {
            for (UnitType t : new UnitType[]{
                    UnitType.LEVEL_ONE_PAINT_TOWER,
                    UnitType.LEVEL_ONE_MONEY_TOWER,
                    UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
                if (rc.canCompleteTowerPattern(t, ruin)) {
                    rc.completeTowerPattern(t, ruin);
                    Comms.registerTower(ruin, t);
                    break;
                }
            }
        }
        // Confirm SRP patterns
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dy = -2; dy <= 2; dy += 4) {
                MapLocation c = new MapLocation(
                    ((me.x + dx + 2) / 4) * 4 + 2,
                    ((me.y + dy + 2) / 4) * 4 + 2);
                if (me.distanceSquaredTo(c) <= 2 && rc.canCompleteResourcePattern(c))
                    rc.completeResourcePattern(c);
            }
        }
    }

    // Sector Partitioning Helpers

    private static boolean belongsToMySector(MapLocation loc) {
        int col = loc.x * SECTOR_COLS / RobotPlayer.mapW;
        int row = loc.y * SECTOR_ROWS / RobotPlayer.mapH;
        int sector = row * SECTOR_COLS + col;
        return sector == mySector;
    }

    private static MapLocation sectorCenter() {
        int col = mySector % SECTOR_COLS;
        int row = mySector / SECTOR_COLS;
        int cx = (col * RobotPlayer.mapW / SECTOR_COLS) + (RobotPlayer.mapW / (2 * SECTOR_COLS));
        int cy = (row * RobotPlayer.mapH / SECTOR_ROWS) + (RobotPlayer.mapH / (2 * SECTOR_ROWS));
        return new MapLocation(
            Math.min(cx, RobotPlayer.mapW - 1),
            Math.min(cy, RobotPlayer.mapH - 1));
    }

    private static int[][] getPattern(UnitType type) {
        UnitType base = type.getBaseType();
        if (base == UnitType.LEVEL_ONE_PAINT_TOWER) return PAINT_TOWER_PATTERN;
        if (base == UnitType.LEVEL_ONE_MONEY_TOWER) return MONEY_TOWER_PATTERN;
        return DEFENSE_TOWER_PATTERN;
    }

    // Init

    private static void initSpawn(RobotController rc, MapLocation me)
            throws GameActionException {
        int best = Integer.MAX_VALUE;
        spawnTower = me;
        for (RobotInfo r : rc.senseNearbyRobots(4, RobotPlayer.myTeam)) {
            if (r.type.isTowerType()) {
                int d = me.distanceSquaredTo(r.location);
                if (d < best) { best = d; spawnTower = r.location; }
            }
        }
    }
}
