package kevin_bot_1;

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
 * Greedy Soldier — the workhorse unit.
 *
 * Greedy priority each turn:
 *  1. If paint < 25%  → retreat to nearest paint tower to refill
 *  2. If enemy tower in sight and HP OK → attack it
 *  3. If completable ruin nearby → build tower (greedy: pick most-complete ruin)
 *  4. If SRP (Special Resource Pattern) completable → build SRP
 *  5. Otherwise → paint tiles & explore unpainted territory
 *
 * Painting follows a greedy heuristic:
 *   - Always paint under self if empty
 *   - Paint the nearest empty tile within action range
 *   - Use correct SRP/tower color when building patterns
 */
public class SoldierBot {

    private static MapLocation spawnTower = null;
    private static Direction   exploreDir = null;
    private static MapLocation exploreLoc = null;

    /* greedy paint thresholds */
    private static final double REFILL_LOW  = 0.25;
    private static final double REFILL_HIGH = 0.75;

    /* tower-build economy threshold */
    private static final int SRP_TOWER_MIN = 4;

    private enum State { EXPLORE, REFILL, COMBAT, BUILD_TOWER, BUILD_SRP }
    private static State state = State.EXPLORE;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();

        /* ---- first turn init ---- */
        if (spawnTower == null) {
            initSpawnTower(rc, me);
            exploreDir = spawnTower.directionTo(me);
            if (exploreDir == Direction.CENTER)
                exploreDir = RobotPlayer.DIRS[RobotPlayer.myID % 8];
            exploreLoc = extendToEdge(me, exploreDir);
        }

        /* ---- sense & communicate ---- */
        Messaging.readMessages();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        updateKnownTowers(rc, allies);
        Messaging.relayToNearbyTower(allies);

        /* ---- pick greedy state ---- */
        state = greedyDecide(rc, me, nearby, allies, enemies);

        /* ---- execute state ---- */
        switch (state) {
            case REFILL      -> refillState(rc, me, allies, enemies);
            case COMBAT       -> combatState(rc, me, enemies);
            case BUILD_TOWER  -> buildTowerState(rc, me, nearby, enemies);
            case BUILD_SRP    -> buildSRPState(rc, me, nearby);
            case EXPLORE      -> exploreState(rc, me, nearby, enemies);
        }

        /* ---- invariant: always paint under self + confirm patterns ---- */
        paintUnderSelf(rc, me);
        if (state != State.REFILL) paintNearby(rc, me, nearby);
        confirmPatterns(rc, me);
    }

    /* ============================================================ */
    /*                   GREEDY STATE SELECTION                     */
    /* ============================================================ */
    private static MapLocation bestRuin = null;
    private static MapLocation bestSRP  = null;

    private static State greedyDecide(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies)
            throws GameActionException {

        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;

        /* refill check */
        if ((state != State.REFILL && paintRatio < REFILL_LOW)
         || (state == State.REFILL && paintRatio < REFILL_HIGH)) {
            return State.REFILL;
        }

        /* combat: enemy tower reachable and I'm healthy */
        RobotInfo enemyTower = Nav.nearestEnemyTower(enemies);
        if (enemyTower != null && rc.getHealth() > 40
            && enemyTower.type.getBaseType() != UnitType.LEVEL_ONE_DEFENSE_TOWER) {
            return State.COMBAT;
        }

        /* build tower: pick the most-complete towerless ruin (greedy) */
        boolean canAffordTower = rc.getChips() >= 700 || rc.getNumberTowers() < 5;
        bestRuin = findBestRuin(rc, me, nearby, allies);
        if (bestRuin != null && canAffordTower) {
            return State.BUILD_TOWER;
        }

        /* build SRP if enough towers */
        if (Messaging.towerCount >= SRP_TOWER_MIN && Messaging.countPaintTowers() > 0
            && enemies.length == 0) {
            bestSRP = findBestSRP(rc, me, nearby);
            if (bestSRP != null) return State.BUILD_SRP;
        }

        return State.EXPLORE;
    }

    /* ============================================================ */
    /*                       STATE HANDLERS                        */
    /* ============================================================ */

    private static void refillState(RobotController rc, MapLocation me,
            RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {

        /* try nearby visible towers first */
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
            MapLocation pt = Messaging.nearestPaintTower(me);
            target = pt != null ? pt : (spawnTower != null ? spawnTower : me);
        }

        // within transfer range?
        if (me.distanceSquaredTo(target) <= 2) {
            if (nearestTower != null) {
                int amt = Math.min(rc.getType().paintCapacity - rc.getPaint(),
                                   nearestTower.paintAmount);
                if (amt > 0 && rc.canTransferPaint(target, -amt)) {
                    rc.transferPaint(target, -amt);
                }
            }
            if ((double) rc.getPaint() / rc.getType().paintCapacity >= REFILL_HIGH) {
                state = State.EXPLORE;
            }
        } else {
            Nav.bugNav(target);
        }
    }

    private static void combatState(RobotController rc, MapLocation me,
            RobotInfo[] enemies) throws GameActionException {
        RobotInfo tower = Nav.nearestEnemyTower(enemies);
        if (tower == null) { state = State.EXPLORE; return; }

        if (me.distanceSquaredTo(tower.location) <= rc.getType().actionRadiusSquared) {
            if (rc.canAttack(tower.location)) rc.attack(tower.location);
            // retreat after attack
            Direction away = me.directionTo(tower.location).opposite();
            Nav.fuzzyMove(away);
        } else {
            Nav.moveIntoRange(tower.location, rc.getType().actionRadiusSquared);
            if (rc.canAttack(tower.location)) rc.attack(tower.location);
        }
    }

    private static void buildTowerState(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        if (bestRuin == null) { state = State.EXPLORE; return; }

        /* determine tower type greedily:
           money:paint ratio ≈ 2.5:1 early, then 1:1 */
        UnitType towerType = decideTowerType(rc);

        /* mark pattern */
        if (rc.canMarkTowerPattern(towerType, bestRuin))
            rc.markTowerPattern(towerType, bestRuin);

        /* paint tiles around ruin with correct pattern */
        paintRuinTiles(rc, me, bestRuin, towerType);

        /* move toward ruin */
        if (me.distanceSquaredTo(bestRuin) > 8) {
            Nav.bugNav(bestRuin);
        } else if (me.distanceSquaredTo(bestRuin) > 2) {
            Nav.fuzzyMove(bestRuin);
        }

        /* try completing */
        for (UnitType t : new UnitType[]{
                UnitType.LEVEL_ONE_PAINT_TOWER,
                UnitType.LEVEL_ONE_MONEY_TOWER,
                UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
            if (rc.canCompleteTowerPattern(t, bestRuin)) {
                rc.completeTowerPattern(t, bestRuin);
                Messaging.registerTower(bestRuin, t);
                // broadcast
                for (RobotInfo a : rc.senseNearbyRobots(-1, RobotPlayer.myTeam)) {
                    if (a.type.isTowerType() && rc.canSendMessage(a.location)) {
                        rc.sendMessage(a.location,
                            Messaging.encodeTowerBuilt(bestRuin, t));
                        break;
                    }
                }
                break;
            }
        }
    }

    private static void buildSRPState(RobotController rc, MapLocation me,
            MapInfo[] nearby) throws GameActionException {
        if (bestSRP == null) { state = State.EXPLORE; return; }

        /* move to SRP center */
        if (!me.equals(bestSRP)) {
            Nav.fuzzyMove(bestSRP);
        }

        /* paint SRP pattern */
        paintSRPTiles(rc, me, bestSRP);

        /* try completing */
        if (me.distanceSquaredTo(bestSRP) <= 2 && rc.canCompleteResourcePattern(bestSRP))
            rc.completeResourcePattern(bestSRP);
    }

    private static void exploreState(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {

        /* find nearest empty paint tile in vision to greedily approach */
        MapLocation closestEmpty = null;
        int closestDist = Integer.MAX_VALUE;
        for (MapInfo info : nearby) {
            if (info.getPaint() == PaintType.EMPTY && !info.isWall() && !info.hasRuin()) {
                int d = me.distanceSquaredTo(info.getMapLocation());
                if (d < closestDist) { closestDist = d; closestEmpty = info.getMapLocation(); }
            }
        }

        if (closestEmpty != null && closestDist > 4 && rc.isActionReady()) {
            Nav.safeFuzzyMove(closestEmpty, enemies);
        }

        /* if at explore target, pick new random direction */
        if (me.distanceSquaredTo(exploreLoc) <= 8) {
            exploreDir = RobotPlayer.DIRS[(RobotPlayer.myID + rc.getRoundNum()) % 8];
            exploreLoc = extendToEdge(me, exploreDir);
        }

        if (rc.isMovementReady()) {
            Nav.bugNav(exploreLoc);
        }
    }

    /* ============================================================ */
    /*                    PAINTING HELPERS                          */
    /* ============================================================ */

    /** SRP color pattern (5×5 grid, 1=primary, 2=secondary). */
    private static final int[][] SRP_PATTERN = {
        {2,2,1,2,2},
        {2,1,1,1,2},
        {1,1,2,1,1},
        {2,1,1,1,2},
        {2,2,1,2,2}
    };

    private static final int[][] PAINT_TOWER_PATTERN = {
        {2,1,1,1,2},{1,2,1,2,1},{1,1,2,1,1},{1,2,1,2,1},{2,1,1,1,2}
    };
    private static final int[][] MONEY_TOWER_PATTERN = {
        {1,2,2,2,1},{2,2,1,2,2},{2,1,1,1,2},{2,2,1,2,2},{1,2,2,2,1}
    };
    private static final int[][] DEFENSE_TOWER_PATTERN = {
        {1,1,2,1,1},{1,2,2,2,1},{2,2,2,2,2},{1,2,2,2,1},{1,1,2,1,1}
    };

    private static void paintUnderSelf(RobotController rc, MapLocation me)
            throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo mi = rc.senseMapInfo(me);
        if (mi.getPaint() == PaintType.EMPTY && rc.canAttack(me)) {
            rc.attack(me);
        }
    }

    private static void paintNearby(RobotController rc, MapLocation me,
            MapInfo[] nearby) throws GameActionException {
        if (!rc.isActionReady()) return;
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1500) break;
            MapLocation loc = info.getMapLocation();
            if (info.getPaint() == PaintType.EMPTY
                && !info.isWall() && !info.hasRuin()
                && rc.canAttack(loc)) {
                rc.attack(loc);
                break;
            }
        }
    }

    private static void paintRuinTiles(RobotController rc, MapLocation me,
            MapLocation ruin, UnitType type) throws GameActionException {
        if (!rc.isActionReady()) return;
        int[][] pattern = type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER
            ? PAINT_TOWER_PATTERN
            : type.getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER
                ? MONEY_TOWER_PATTERN : DEFENSE_TOWER_PATTERN;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (Clock.getBytecodesLeft() < 1500) return;
                MapLocation tile = ruin.translate(dx, dy);
                if (!rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (info.hasRuin() || info.isWall()) continue;
                boolean wantSecondary = pattern[dx + 2][dy + 2] == 2;
                PaintType want = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                if (info.getPaint() != want && !info.getPaint().isEnemy()
                    && rc.canAttack(tile)) {
                    rc.attack(tile, wantSecondary);
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
                boolean wantSecondary = SRP_PATTERN[dx + 2][dy + 2] == 2;
                PaintType want = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                if (info.getPaint() != want && !info.getPaint().isEnemy()
                    && rc.canAttack(tile)) {
                    rc.attack(tile, wantSecondary);
                    return;
                }
            }
        }
    }

    /* ============================================================ */
    /*               GREEDY RUIN / SRP FINDING                     */
    /* ============================================================ */

    /** Find the ruin with the most ally paint already on it (most completable). */
    private static MapLocation findBestRuin(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies) throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        MapLocation best = null;
        int bestCorrect = -1;

        for (MapLocation ruin : ruins) {
            if (rc.senseRobotAtLocation(ruin) != null) continue; // already has tower

            // check for enemy paint — skip if present and no mopper
            boolean hasEnemyPaint = false;
            int correctPaint = 0;
            int senseable = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    MapLocation tile = ruin.translate(dx, dy);
                    if (!rc.canSenseLocation(tile)) continue;
                    MapInfo info = rc.senseMapInfo(tile);
                    senseable++;
                    if (info.getPaint().isEnemy()) { hasEnemyPaint = true; break; }
                    if (info.getPaint().isAlly()) correctPaint++;
                }
                if (hasEnemyPaint) break;
            }
            if (hasEnemyPaint) continue;

            // skip if already 2+ soldiers working on it
            int soldiers = 0;
            for (RobotInfo a : allies) {
                if (a.type == UnitType.SOLDIER
                    && a.location.distanceSquaredTo(ruin) <= 4)
                    soldiers++;
            }
            if (soldiers >= 2) continue;

            // greedy: pick ruin with MOST correct paint (closest to done)
            if (correctPaint > bestCorrect) {
                bestCorrect = correctPaint;
                best = ruin;
            }
        }
        return best;
    }

    /** Find closest completable SRP center. */
    private static MapLocation findBestSRP(RobotController rc, MapLocation me,
            MapInfo[] nearby) throws GameActionException {
        // check grid positions near me
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

                // check no enemy paint and not already complete
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

    /* ============================================================ */
    /*                    TOWER TYPE DECISION                       */
    /* ============================================================ */

    /** Greedy tower type: money first (2.5:1 ratio), then paint. */
    private static UnitType decideTowerType(RobotController rc) {
        int money = Messaging.countMoneyTowers();
        int paint = Messaging.countPaintTowers();
        double ratio = money < 6 ? 2.5 : 1.5;
        if (money < Math.max(1, paint) * ratio && rc.getChips() < 4000) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    /* ============================================================ */
    /*                       UTILITY                               */
    /* ============================================================ */

    private static void updateKnownTowers(RobotController rc, RobotInfo[] allies) {
        for (RobotInfo a : allies)
            if (a.type.isTowerType())
                Messaging.registerTower(a.location, a.type);
    }

    private static void initSpawnTower(RobotController rc, MapLocation me)
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

    private static MapLocation extendToEdge(MapLocation from, Direction dir) {
        MapLocation loc = from;
        for (int i = 0; i < 60; i++) {
            MapLocation next = loc.add(dir);
            if (next.x < 0 || next.y < 0
                || next.x >= RobotPlayer.mapW || next.y >= RobotPlayer.mapH) break;
            loc = next;
        }
        return loc;
    }

    private static void confirmPatterns(RobotController rc, MapLocation me)
            throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        for (MapLocation ruin : ruins) {
            for (UnitType t : new UnitType[]{
                    UnitType.LEVEL_ONE_PAINT_TOWER,
                    UnitType.LEVEL_ONE_MONEY_TOWER,
                    UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
                if (rc.canCompleteTowerPattern(t, ruin)) {
                    rc.completeTowerPattern(t, ruin);
                    Messaging.registerTower(ruin, t);
                    break;
                }
            }
        }

        /* try completing any SRP */
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
}
