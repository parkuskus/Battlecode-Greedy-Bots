package kevin_bot_2;
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
 * Greedy Soldier — optimized for paint coverage.
 *
 * Key improvements over bot_2_4:
 *  - findBestRuin: greedy score = allyPaintCount - missingCount*2 + proximity bonus
 *    → heavily prioritizes nearly-complete ruins (finish what's almost done first)
 *  - Paints over enemy paint on ruin tiles to complete towers faster
 *  - Lower chip thresholds: always try tower if chips >= 200 (don't hoard)
 *  - BUILD_TOWER state allows up to 3 soldiers per ruin (was 2)
 *  - Frontier-based exploration for coverage expansion
 */
public class SoldierBot {

    private static MapLocation spawnTower = null;
    private static Direction   exploreDir = null;
    private static MapLocation exploreLoc = null;
    private static int         exploreSetRound = 0;

    private static final int SRP_TOWER_MIN = 3;
    private static final double REFILL_LOW  = 0.10;
    private static final double REFILL_HIGH = 0.50;
    private static final int    REFILL_MAX_DIST = 100;

    private enum State { EXPLORE, REFILL, COMBAT, BUILD_TOWER, BUILD_SRP }
    private static State state = State.EXPLORE;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();

        if (spawnTower == null) {
            initSpawnTower(rc, me);
            exploreDir = spawnTower.directionTo(me);
            if (exploreDir == Direction.CENTER)
                exploreDir = RobotPlayer.DIRS[RobotPlayer.myID % 8];
            exploreLoc = extendToEdge(me, exploreDir);
            exploreSetRound = rc.getRoundNum();
        }

        Messaging.readMessages();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        updateKnownTowers(rc, allies);
        Messaging.relayToNearbyTower(allies);

        state = greedyDecide(rc, me, nearby, allies, enemies);

        switch (state) {
            case REFILL      -> refillState(rc, me, allies);
            case COMBAT       -> combatState(rc, me, enemies);
            case BUILD_TOWER  -> buildTowerState(rc, me, nearby, enemies);
            case BUILD_SRP    -> buildSRPState(rc, me, nearby);
            case EXPLORE      -> exploreState(rc, me, nearby, enemies);
        }

        paintUnderSelf(rc, me);
        if (state != State.REFILL) paintNearby(rc, me, nearby);
        confirmPatterns(rc, me);

        Nav.recordPosition(rc.getLocation());
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

        /* refill: retreat only when very low AND paint tower is nearby */
        if ((state != State.REFILL && paintRatio < REFILL_LOW)
         || (state == State.REFILL && paintRatio < REFILL_HIGH)) {
            RobotInfo visTower = findVisiblePaintTower(allies);
            MapLocation pt = Messaging.nearestPaintTower(me);
            MapLocation tgt = visTower != null ? visTower.location : pt;
            if (tgt != null && me.distanceSquaredTo(tgt) <= REFILL_MAX_DIST) {
                return State.REFILL;
            }
        }

        /* combat: enemy tower reachable and I'm healthy enough */
        RobotInfo enemyTower = Nav.nearestEnemyTower(enemies);
        if (enemyTower != null && rc.getHealth() > 40 && paintRatio > 0.15
            && enemyTower.type.getBaseType() != UnitType.LEVEL_ONE_DEFENSE_TOWER) {
            return State.COMBAT;
        }

        /* build tower: greedy — always build if we can afford at all (don't hoard chips)
           Chips don't win games; towers produce more units which paint more tiles. */
        bestRuin = findBestRuin(rc, me, nearby, allies);
        if (bestRuin != null && rc.getChips() >= 200) {
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
            RobotInfo[] allies) throws GameActionException {
        RobotInfo visibleTower = findVisiblePaintTower(allies);
        MapLocation target;
        if (visibleTower != null) {
            target = visibleTower.location;
        } else {
            MapLocation knownPT = Messaging.nearestPaintTower(me);
            target = knownPT != null ? knownPT : (spawnTower != null ? spawnTower : me);
        }

        if (me.distanceSquaredTo(target) <= 2) {
            if (visibleTower != null) {
                int space = rc.getType().paintCapacity - rc.getPaint();
                int avail = visibleTower.paintAmount;
                int amt = Math.min(space, avail);
                if (amt > 0 && rc.canTransferPaint(target, -amt))
                    rc.transferPaint(target, -amt);
            }
        } else {
            Nav.bugNav(target);
        }
    }

    private static RobotInfo findVisiblePaintTower(RobotInfo[] allies) {
        RobotController rc = RobotPlayer.rc;
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType()
                && a.type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER
                && a.paintAmount > 30) {
                int d = rc.getLocation().distanceSquaredTo(a.location);
                if (d < bestDist) { bestDist = d; best = a; }
            }
        }
        return best;
    }

    private static void combatState(RobotController rc, MapLocation me,
            RobotInfo[] enemies) throws GameActionException {
        RobotInfo tower = Nav.nearestEnemyTower(enemies);
        if (tower == null) { state = State.EXPLORE; return; }

        if (me.distanceSquaredTo(tower.location) <= rc.getType().actionRadiusSquared) {
            if (rc.canAttack(tower.location)) rc.attack(tower.location);
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

        UnitType towerType = decideTowerType(rc);

        if (rc.canMarkTowerPattern(towerType, bestRuin))
            rc.markTowerPattern(towerType, bestRuin);

        paintRuinTiles(rc, me, bestRuin, towerType);

        if (me.distanceSquaredTo(bestRuin) > 8) {
            Nav.bugNav(bestRuin);
        } else if (me.distanceSquaredTo(bestRuin) > 2) {
            Nav.fuzzyMove(bestRuin);
        }

        for (UnitType t : new UnitType[]{
                UnitType.LEVEL_ONE_PAINT_TOWER,
                UnitType.LEVEL_ONE_MONEY_TOWER,
                UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
            if (rc.canCompleteTowerPattern(t, bestRuin)) {
                rc.completeTowerPattern(t, bestRuin);
                Messaging.registerTower(bestRuin, t);
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

        if (!me.equals(bestSRP)) Nav.fuzzyMove(bestSRP);

        paintSRPTiles(rc, me, bestSRP);

        if (me.distanceSquaredTo(bestSRP) <= 2 && rc.canCompleteResourcePattern(bestSRP))
            rc.completeResourcePattern(bestSRP);
    }

    private static void exploreState(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {

        int round = rc.getRoundNum();

        if (me.distanceSquaredTo(exploreLoc) <= 8
            || (round - exploreSetRound) > 30) {
            int dirIdx = (RobotPlayer.myID + round / 30) % 8;
            exploreDir = RobotPlayer.DIRS[dirIdx];
            exploreLoc = extendToEdge(me, exploreDir);
            exploreSetRound = round;
        }

        if (!rc.isMovementReady()) return;

        /* move toward nearest frontier tile (empty adjacent to ally paint) */
        MapLocation closestFrontier = null;
        MapLocation closestEmpty = null;
        int closestFrontierDist = Integer.MAX_VALUE;
        int closestEmptyDist = Integer.MAX_VALUE;
        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 2000) break;
            if (info.getPaint() == PaintType.EMPTY && !info.isWall() && !info.hasRuin()) {
                MapLocation loc = info.getMapLocation();
                int d = me.distanceSquaredTo(loc);
                if (d < closestEmptyDist) { closestEmptyDist = d; closestEmpty = loc; }
                if (d < closestFrontierDist && d > 0) {
                    boolean nearAlly = false;
                    for (Direction dd : RobotPlayer.DIRS) {
                        MapLocation adj = loc.add(dd);
                        if (rc.canSenseLocation(adj)) {
                            PaintType ap = rc.senseMapInfo(adj).getPaint();
                            if (ap.isAlly()) { nearAlly = true; break; }
                        }
                    }
                    if (nearAlly) { closestFrontierDist = d; closestFrontier = loc; }
                }
            }
        }

        if (closestFrontier != null) {
            Nav.fuzzyMove(closestFrontier);
        } else if (closestEmpty != null && closestEmptyDist > 0) {
            Nav.fuzzyMove(closestEmpty);
        } else {
            Nav.bugNav(exploreLoc);
        }
    }

    /* ============================================================ */
    /*                    PAINTING HELPERS                          */
    /* ============================================================ */

    private static final int[][] SRP_PATTERN = {
        {2,2,1,2,2},{2,1,1,1,2},{1,1,2,1,1},{2,1,1,1,2},{2,2,1,2,2}
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
        PaintType p = mi.getPaint();
        if ((p == PaintType.EMPTY || p.isEnemy()) && rc.canAttack(me))
            rc.attack(me);
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

    /**
     * Paint ruin tiles — now also paints OVER enemy paint to complete towers faster.
     * This is the key fix: if a ruin has 23/24 ally tiles but 1 enemy tile,
     * we now paint that enemy tile instead of giving up.
     */
    private static void paintRuinTiles(RobotController rc, MapLocation me,
            MapLocation ruin, UnitType type) throws GameActionException {
        if (!rc.isActionReady()) return;
        int[][] pattern = type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER
            ? PAINT_TOWER_PATTERN
            : type.getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER
                ? MONEY_TOWER_PATTERN : DEFENSE_TOWER_PATTERN;

        /* First pass: paint tiles that need correct color (empty or wrong ally color) */
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (Clock.getBytecodesLeft() < 1500) return;
                MapLocation tile = ruin.translate(dx, dy);
                if (!rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (info.hasRuin() || info.isWall()) continue;
                boolean wantSecondary = pattern[dx + 2][dy + 2] == 2;
                PaintType want = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                PaintType cur = info.getPaint();
                if (cur != want && rc.canAttack(tile)) {
                    rc.attack(tile, wantSecondary);
                    return;
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
    /*         GREEDY RUIN FINDING — prioritize nearly-complete     */
    /* ============================================================ */

    /**
     * Greedy ruin selection: score = correctTiles * 3 - missingTiles - distancePenalty
     * This strongly favors ruins that are almost done (e.g. 23/24 painted).
     * Even allows ruins with a few enemy paint tiles (mopper or soldier can fix).
     */
    private static MapLocation findBestRuin(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies) throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        MapLocation best = null;
        int bestScore = -9999;

        for (MapLocation ruin : ruins) {
            if (Clock.getBytecodesLeft() < 3000) break;
            if (rc.senseRobotAtLocation(ruin) != null) continue; // already has tower

            int correctPaint = 0;
            int totalPaintable = 0;
            int enemyPaintCount = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    MapLocation tile = ruin.translate(dx, dy);
                    if (!rc.canSenseLocation(tile)) continue;
                    MapInfo info = rc.senseMapInfo(tile);
                    if (info.hasRuin() || info.isWall()) continue;
                    totalPaintable++;
                    if (info.getPaint().isAlly()) correctPaint++;
                    else if (info.getPaint().isEnemy()) enemyPaintCount++;
                }
            }

            // Skip ruins with enemy paint unless nearly complete (>75% ally)
            if (enemyPaintCount > 0 && totalPaintable > 0
                && correctPaint < totalPaintable * 3 / 4) continue;

            // Don't crowd: allow up to 3 soldiers near a ruin
            int soldiers = 0;
            for (RobotInfo a : allies) {
                if (a.type == UnitType.SOLDIER
                    && a.location.distanceSquaredTo(ruin) <= 8)
                    soldiers++;
            }
            if (soldiers >= 3) continue;

            // Greedy score: reward completion, penalize distance heavily
            // Spec insight: travel time = rounds NOT painting, so nearby > far
            int missing = totalPaintable - correctPaint;
            int dist = (int) Math.sqrt(me.distanceSquaredTo(ruin));
            int distPenalty = dist * 2;
            int score = correctPaint * 3 - missing * 2 - distPenalty;

            if (score > bestScore) {
                bestScore = score;
                best = ruin;
            }
        }
        return best;
    }

    private static MapLocation findBestSRP(RobotController rc, MapLocation me,
            MapInfo[] nearby) throws GameActionException {
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

    /* ============================================================ */
    /*                    TOWER TYPE DECISION                       */
    /* ============================================================ */

    /** Greedy: paint first, then money for income, defense after 6+ towers. */
    private static UnitType decideTowerType(RobotController rc) {
        int money = Messaging.countMoneyTowers();
        int paint = Messaging.countPaintTowers();
        int defense = Messaging.countDefenseTowers();
        int total = Messaging.towerCount;

        if (paint == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
        // Spec: defense tower gives +5/+7/+9 to ALL ally tower single attacks
        if (total >= 6 && defense == 0) return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        if (money <= paint) return UnitType.LEVEL_ONE_MONEY_TOWER;
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
