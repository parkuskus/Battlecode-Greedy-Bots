package gemilang;

import battlecode.common.*;

/**
 * Soldier: greedy-first objective unit (general-purpose).
 */
public class SoldierBot {

    private static final double REFILL_LOW = 0.12;
    private static final double REFILL_HIGH = 0.52;
    private static final double RETREAT_ENTER = 0.22;
    private static final double RETREAT_EXIT = 0.55;
    private static final int REFILL_MAX_DIST = 120;
    private static final int SRP_TOWER_MIN = 2;
    private static final int MAX_BUILDERS_PER_RUIN = 2;
    private static final int BUILD_TIMEOUT = 60;

    private static final int IDX_RETREAT = 0;
    private static final int IDX_REFILL = 1;
    private static final int IDX_SKIRMISH = 2;
    private static final int IDX_COMBAT_TOWER = 3;
    private static final int IDX_BUILD_TOWER = 4;
    private static final int IDX_BUILD_SRP = 5;
    private static final int IDX_EXPLORE = 6;

    private static MapLocation spawnTower = null;
    private static Direction exploreDir = null;
    private static MapLocation exploreLoc = null;
    private static int exploreSetRound = 0;

    private static boolean retreatActive = false;

    private static MapLocation bestRuin = null;
    private static int bestRuinBuilders = 0;
    private static int bestRuinEnemyPaint = 0;
    private static double bestRuinPressure = 0;

    private static MapLocation bestSRP = null;
    private static MapLocation bestSkirmish = null;
    private static RobotInfo bestEnemyTower = null;

    private static MapLocation activeRuin = null;
    private static int activeRuinStartRound = -999;

    private static final GreedyCore.Metric METRIC = new GreedyCore.Metric();

    private enum State {
        RETREAT,
        REFILL,
        SKIRMISH,
        COMBAT_TOWER,
        BUILD_TOWER,
        BUILD_SRP,
        EXPLORE
    }

    private static State state = State.EXPLORE;

    private static final int[][] SRP_PATTERN = {
            {2, 2, 1, 2, 2},
            {2, 1, 1, 1, 2},
            {1, 1, 2, 1, 1},
            {2, 1, 1, 1, 2},
            {2, 2, 1, 2, 2}
    };

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();

        GreedyCore.startTurn(rc, METRIC);

        if (spawnTower == null) {
            initSpawnTower(rc, me);
            exploreDir = RobotPlayer.DIRS[(RobotPlayer.myID * 37) % 8];
            exploreLoc = Nav.extendToEdge(me, exploreDir);
            exploreSetRound = rc.getRoundNum();
        }

        Messaging.readMessages();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        for (RobotInfo a : allies) {
            if (a.type.isTowerType()) Messaging.registerTower(a.location, a.type);
        }
        Messaging.relayToNearbyTower(allies);

        METRIC.enemyPaintSeen += countEnemyPaint(nearby);

        state = greedyDecide(rc, me, nearby, allies, enemies);

        switch (state) {
            case RETREAT -> retreatState(rc, me, allies, enemies);
            case REFILL -> refillState(rc, me, allies);
            case SKIRMISH -> skirmishState(rc, me, allies, enemies);
            case COMBAT_TOWER -> combatTowerState(rc, me, enemies);
            case BUILD_TOWER -> buildTowerState(rc, me, allies, enemies);
            case BUILD_SRP -> buildSRPState(rc, me, enemies);
            case EXPLORE -> exploreState(rc, me, nearby, enemies);
        }

        me = rc.getLocation();
        paintUnderSelf(rc, me);
        if (state != State.REFILL) {
            paintNearbyGreedy(rc, me, nearby, enemies);
        }
        confirmPatterns(rc, me);
        Nav.recordPosition(rc.getLocation());

        GreedyCore.endTurn(rc, METRIC);
        GreedyCore.traceMetric(rc, "SOLDIER", METRIC);
    }

    private static State greedyDecide(RobotController rc, MapLocation me,
                                      MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;

        MapLocation refillTower = findVisibleRefillTower(allies, me);
        if (refillTower == null) refillTower = Messaging.nearestRefillTower(me);

        bestEnemyTower = Nav.nearestEnemyTower(enemies);
        bestSkirmish = nearestNonTowerEnemy(me, enemies);
        bestRuin = findBestRuin(rc, me, allies, enemies);
        bestSRP = findBestSRP(rc, me, enemies);

        double pressure = localPressure(me, enemies, nearby);
        boolean canExitRetreat = paintRatio > RETREAT_EXIT && pressure < 1.0;

        double[] score = new double[7];
        boolean[] feasible = new boolean[7];
        int[] dist = new int[7];
        int[] tie = new int[7];

        feasible[IDX_RETREAT] = !canExitRetreat && (retreatActive || paintRatio < RETREAT_ENTER || rc.getPaint() <= 8 || pressure >= 2.4);
        score[IDX_RETREAT] = GreedyCore.score(0.1, 0.2, 6.0 + Math.max(0.0, pressure - 1.0), 1.2, 0.2, Math.max(0.0, paintRatio - 0.5), 0.2);
        dist[IDX_RETREAT] = 0;
        tie[IDX_RETREAT] = 1;

        int refillDist = refillTower == null ? 999 : me.distanceSquaredTo(refillTower);
        feasible[IDX_REFILL] = refillTower != null
                && refillDist <= REFILL_MAX_DIST
                && ((state != State.REFILL && paintRatio < REFILL_LOW) || (state == State.REFILL && paintRatio < REFILL_HIGH));
        score[IDX_REFILL] = GreedyCore.score((1.0 - paintRatio) * 4.5, 0, 2.1, 2.8, 0.3, pressure * 0.3, refillDist * 0.03);
        dist[IDX_REFILL] = refillDist;
        tie[IDX_REFILL] = GreedyCore.tieId(refillTower);

        int skDist = bestSkirmish == null ? 999 : me.distanceSquaredTo(bestSkirmish);
        feasible[IDX_SKIRMISH] = bestSkirmish != null && paintRatio > 0.18;
        score[IDX_SKIRMISH] = GreedyCore.score(1.2, 2.1, 2.8, 0.7, countAlliedSoldiersNear(allies, bestSkirmish, 8) * 0.5, pressure * 0.8, 1.0);
        dist[IDX_SKIRMISH] = skDist;
        tie[IDX_SKIRMISH] = GreedyCore.tieId(bestSkirmish);

        int twDist = bestEnemyTower == null ? 999 : me.distanceSquaredTo(bestEnemyTower.location);
        feasible[IDX_COMBAT_TOWER] = bestEnemyTower != null && rc.getHealth() > 45 && paintRatio > 0.30;
        score[IDX_COMBAT_TOWER] = GreedyCore.score(0.8, 1.0, 3.4, 0.5, 0.2, 1.4, twDist * 0.02);
        dist[IDX_COMBAT_TOWER] = twDist;
        tie[IDX_COMBAT_TOWER] = bestEnemyTower == null ? Integer.MAX_VALUE : bestEnemyTower.ID;

        int ruinDist = bestRuin == null ? 999 : me.distanceSquaredTo(bestRuin);
        boolean ruinTimedOut = activeRuin != null && bestRuin != null && activeRuin.equals(bestRuin)
                && rc.getRoundNum() - activeRuinStartRound > BUILD_TIMEOUT;
        feasible[IDX_BUILD_TOWER] = bestRuin != null
                && rc.getChips() >= 200
                && bestRuinBuilders < MAX_BUILDERS_PER_RUIN
                && !ruinTimedOut
                && !(bestRuinPressure > 3.0 && bestRuinEnemyPaint > 6);
        score[IDX_BUILD_TOWER] = GreedyCore.score(
                Math.max(1, 20 - ruinDist) * 0.15,
                bestRuinEnemyPaint,
                7.0 - ruinDist * 0.03,
                2.3,
                Math.max(0, MAX_BUILDERS_PER_RUIN - bestRuinBuilders),
                bestRuinPressure,
                1.2);
        dist[IDX_BUILD_TOWER] = ruinDist;
        tie[IDX_BUILD_TOWER] = GreedyCore.tieId(bestRuin);

        int srpDist = bestSRP == null ? 999 : me.distanceSquaredTo(bestSRP);
        feasible[IDX_BUILD_SRP] = bestSRP != null
                && Messaging.towerCount >= SRP_TOWER_MIN
                && rc.getChips() >= 120
                && pressure < 1.8;
        score[IDX_BUILD_SRP] = GreedyCore.score(Math.max(1, 14 - srpDist) * 0.2, 0.2, 4.4, 2.1, 0.3, pressure, 1.0);
        dist[IDX_BUILD_SRP] = srpDist;
        tie[IDX_BUILD_SRP] = GreedyCore.tieId(bestSRP);

        int exDist = exploreLoc == null ? 999 : me.distanceSquaredTo(exploreLoc);
        feasible[IDX_EXPLORE] = true;
        score[IDX_EXPLORE] = GreedyCore.score(2.1, 0.4, 2.3, 1.2, 0.4, pressure * 0.4, 0.6);
        dist[IDX_EXPLORE] = exDist;
        tie[IDX_EXPLORE] = GreedyCore.tieId(exploreLoc);

        int[] rejected = new int[1];
        double[] second = new double[1];
        int best = GreedyCore.pickBest(score, feasible, dist, tie, rejected, second);
        if (best < 0) best = IDX_EXPLORE;

        State next = switch (best) {
            case IDX_RETREAT -> State.RETREAT;
            case IDX_REFILL -> State.REFILL;
            case IDX_SKIRMISH -> State.SKIRMISH;
            case IDX_COMBAT_TOWER -> State.COMBAT_TOWER;
            case IDX_BUILD_TOWER -> State.BUILD_TOWER;
            case IDX_BUILD_SRP -> State.BUILD_SRP;
            default -> State.EXPLORE;
        };

        if (next == State.RETREAT && !retreatActive) {
            retreatActive = true;
            METRIC.retreatEntries++;
        } else if (retreatActive && next != State.RETREAT) {
            retreatActive = false;
            METRIC.retreatRecoveries++;
        }

        GreedyCore.traceDecision(rc, "SOLDIER", "STATE", score[best], second[0], rejected[0], next.name());
        return next;
    }

    private static void refillState(RobotController rc, MapLocation me, RobotInfo[] allies) throws GameActionException {
        MapLocation target = findVisibleRefillTower(allies, me);
        if (target == null) target = Messaging.nearestRefillTower(me);
        if (target == null) target = spawnTower != null ? spawnTower : me;

        if (me.distanceSquaredTo(target) <= 2) {
            int need = rc.getType().paintCapacity - rc.getPaint();
            if (need > 0 && rc.canTransferPaint(target, -need)) {
                GreedyCore.check(rc.isActionReady(), "transfer paint without action ready");
                rc.transferPaint(target, -need);
            }
        } else {
            Nav.bugNav(target);
        }
    }

    private static void retreatState(RobotController rc, MapLocation me,
                                     RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        MapLocation target = findVisibleRefillTower(allies, me);
        if (target == null) target = Messaging.nearestRefillTower(me);
        if (target == null) target = spawnTower != null ? spawnTower : me;

        RobotInfo nearest = nearestNonTowerEnemyInfo(me, enemies);

        if (rc.isActionReady() && nearest != null && me.distanceSquaredTo(nearest.location) <= rc.getType().actionRadiusSquared
                && rc.canAttack(nearest.location)) {
            rc.attack(nearest.location);
            METRIC.effectivePaintActions++;
            if (nearest.paintAmount > 0) METRIC.enemyPaintCleaned++;
        }

        if (rc.isMovementReady()) {
            if (nearest != null) {
                Direction away = me.directionTo(nearest.location).opposite();
                if (!Nav.fuzzyMove(away)) Nav.bugNav(target, enemies);
            } else Nav.bugNav(target, enemies);
        }

        int sev = (int) Math.max(0, Math.min(3, (RETREAT_ENTER - (double) rc.getPaint() / rc.getType().paintCapacity) * 12.0));
        if (sev > 0) sendOneTowerSignal(allies, Messaging.encodeRetreatBeacon(rc.getLocation(), sev));
    }

    private static void skirmishState(RobotController rc, MapLocation me,
                                      RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        RobotInfo target = nearestNonTowerEnemyInfo(me, enemies);
        if (target == null) {
            state = State.EXPLORE;
            return;
        }

        int d = me.distanceSquaredTo(target.location);
        if (rc.isActionReady() && d <= rc.getType().actionRadiusSquared && rc.canAttack(target.location)) {
            rc.attack(target.location);
            METRIC.effectivePaintActions++;
            if (target.paintAmount > 0) METRIC.enemyPaintCleaned++;
        }

        if (rc.isMovementReady()) {
            if (d > rc.getType().actionRadiusSquared) Nav.safeFuzzyMove(target.location, enemies);
            else Nav.fuzzyMove(me.directionTo(target.location).opposite());
        }

        int cluster = countNonTowerEnemies(enemies);
        if (cluster >= 3) sendOneTowerSignal(allies, Messaging.encodeEnemyBlob(target.location, Math.min(3, cluster - 1)));
    }

    private static void combatTowerState(RobotController rc, MapLocation me, RobotInfo[] enemies) throws GameActionException {
        RobotInfo tower = Nav.nearestEnemyTower(enemies);
        if (tower == null) {
            state = State.EXPLORE;
            return;
        }

        if (me.distanceSquaredTo(tower.location) <= rc.getType().actionRadiusSquared) {
            if (rc.canAttack(tower.location)) {
                rc.attack(tower.location);
                METRIC.effectivePaintActions++;
            }
            Direction away = me.directionTo(tower.location).opposite();
            Nav.fuzzyMove(away);
        } else {
            Nav.moveIntoRange(tower.location, rc.getType().actionRadiusSquared);
            me = rc.getLocation();
            if (rc.canAttack(tower.location)) {
                rc.attack(tower.location);
                METRIC.effectivePaintActions++;
            }
        }
    }

    private static void buildTowerState(RobotController rc, MapLocation me,
                                        RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        if (bestRuin == null) {
            state = State.EXPLORE;
            return;
        }

        if (activeRuin == null || !activeRuin.equals(bestRuin)) {
            activeRuin = bestRuin;
            activeRuinStartRound = rc.getRoundNum();
        }
        if (rc.getRoundNum() - activeRuinStartRound > BUILD_TIMEOUT) {
            activeRuin = null;
            state = State.EXPLORE;
            return;
        }

        UnitType towerType = decideTowerType(rc, bestRuin, enemies);

        boolean needsMark = false;
        MapLocation checkTile = bestRuin.translate(1, 0);
        if (rc.canSenseLocation(checkTile)) {
            MapInfo checkInfo = rc.senseMapInfo(checkTile);
            if (!checkInfo.hasRuin() && !checkInfo.isWall() && checkInfo.getMark() == PaintType.EMPTY) needsMark = true;
        } else needsMark = true;
        if (needsMark && rc.canMarkTowerPattern(towerType, bestRuin)) rc.markTowerPattern(towerType, bestRuin);

        paintMarkedTiles(rc, bestRuin);

        if (me.distanceSquaredTo(bestRuin) > 8) Nav.bugNav(bestRuin, enemies);
        else if (me.distanceSquaredTo(bestRuin) > 2) Nav.safeFuzzyMove(bestRuin, enemies);

        for (UnitType t : new UnitType[]{UnitType.LEVEL_ONE_PAINT_TOWER,
                UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
            if (rc.canCompleteTowerPattern(t, bestRuin)) {
                rc.completeTowerPattern(t, bestRuin);
                Messaging.registerTower(bestRuin, t);
                broadcastTowerBuilt(allies, bestRuin, t);
                METRIC.towerCompletions++;
                activeRuin = null;
                return;
            }
        }

        if (bestRuinPressure > 2.6 && bestRuinEnemyPaint > 5) {
            sendOneTowerSignal(allies, Messaging.encodeContestedRuin(bestRuin, Math.min(3, (int) bestRuinPressure)));
        }
    }

    private static void buildSRPState(RobotController rc, MapLocation me, RobotInfo[] enemies) throws GameActionException {
        if (bestSRP == null) {
            state = State.EXPLORE;
            return;
        }

        boolean needsMark = false;
        MapLocation checkTile = bestSRP.translate(1, 0);
        if (rc.canSenseLocation(checkTile)) {
            MapInfo checkInfo = rc.senseMapInfo(checkTile);
            if (!checkInfo.isWall() && checkInfo.getMark() == PaintType.EMPTY) needsMark = true;
        } else needsMark = true;
        if (needsMark && rc.canMarkResourcePattern(bestSRP)) rc.markResourcePattern(bestSRP);

        if (!me.equals(bestSRP)) Nav.safeFuzzyMove(bestSRP, enemies);

        paintMarkedTiles(rc, bestSRP);

        me = rc.getLocation();
        if (me.distanceSquaredTo(bestSRP) <= 2 && rc.canCompleteResourcePattern(bestSRP)) {
            rc.completeResourcePattern(bestSRP);
            METRIC.srpCompletions++;
        }
    }

    private static void exploreState(RobotController rc, MapLocation me,
                                     MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        int round = rc.getRoundNum();
        if (me.distanceSquaredTo(exploreLoc) <= 8 || (round - exploreSetRound) > 30) {
            exploreDir = RobotPlayer.DIRS[(RobotPlayer.myID * 37 + round / 30) % 8];
            exploreLoc = Nav.extendToEdge(me, exploreDir);
            exploreSetRound = round;
        }

        if (Messaging.contestedRuinAt != null && round - Messaging.contestedRuinRound <= 4) {
            exploreLoc = Messaging.contestedRuinAt;
        } else if (Messaging.enemyBlobAt != null && round - Messaging.enemyBlobRound <= 4) {
            exploreLoc = Messaging.enemyBlobAt;
        }

        if (!rc.isMovementReady()) return;

        MapLocation frontier = null;
        int bestFrontierDist = Integer.MAX_VALUE;
        MapLocation empty = null;
        int bestEmptyDist = Integer.MAX_VALUE;

        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 2000) break;
            if (info.isWall() || info.hasRuin()) continue;
            PaintType p = info.getPaint();
            MapLocation loc = info.getMapLocation();
            int d = me.distanceSquaredTo(loc);

            if ((p == PaintType.EMPTY || p.isEnemy()) && isFrontierTile(rc, loc, info) && d < bestFrontierDist) {
                bestFrontierDist = d;
                frontier = loc;
            }
            if (p == PaintType.EMPTY && d < bestEmptyDist) {
                bestEmptyDist = d;
                empty = loc;
            }
        }

        if (frontier != null) Nav.safeFuzzyMove(frontier, enemies);
        else if (empty != null) Nav.fuzzyMove(empty);
        else Nav.bugNav(exploreLoc, enemies);
    }

    private static UnitType decideTowerType(RobotController rc, MapLocation ruin, RobotInfo[] enemies) {
        double pressure = localPressureAt(ruin, enemies);
        if (pressure > 2.2 && Messaging.countDefenseTowers() < 3) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }

        int money = Messaging.countMoneyTowers();
        int paint = Messaging.countPaintTowers();
        if (paint == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (money <= paint) return UnitType.LEVEL_ONE_MONEY_TOWER;
        if (rc.getChips() < 450) return UnitType.LEVEL_ONE_PAINT_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    private static void paintUnderSelf(RobotController rc, MapLocation me) throws GameActionException {
        if (!rc.isActionReady()) return;
        MapInfo myInfo = rc.senseMapInfo(me);
        PaintType p = myInfo.getPaint();
        PaintType mark = myInfo.getMark();
        if (p == PaintType.EMPTY || p.isEnemy() || (mark != PaintType.EMPTY && mark != p)) {
            if (rc.canAttack(me)) {
                rc.attack(me, mark == PaintType.ALLY_SECONDARY);
                METRIC.effectivePaintActions++;
                if (p.isEnemy()) METRIC.enemyPaintCleaned++;
            }
        }
    }

    private static void paintNearbyGreedy(RobotController rc, MapLocation me,
                                          MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        if (!rc.isActionReady()) return;

        MapLocation best = null;
        double bestScore = -9999;
        double second = -9999;
        int rejected = 0;

        for (MapInfo info : nearby) {
            if (Clock.getBytecodesLeft() < 1700) break;
            MapLocation loc = info.getMapLocation();
            if (!rc.canAttack(loc)) {
                rejected++;
                continue;
            }
            if (info.isWall() || info.hasRuin()) {
                rejected++;
                continue;
            }

            PaintType paint = info.getPaint();
            PaintType mark = info.getMark();
            boolean markMismatch = mark != PaintType.EMPTY && mark != paint;
            if (paint.isAlly() && !markMismatch) {
                rejected++;
                continue;
            }

            boolean frontier = isFrontierTile(rc, loc, info);
            double sc = GreedyCore.score(
                    paint == PaintType.EMPTY ? 1.0 : paint.isEnemy() ? 1.3 : 0.2,
                    paint.isEnemy() ? 1.0 : 0.0,
                    frontier ? 1.5 : 0.5,
                    0.8,
                    0.3,
                    Nav.inEnemyTowerRange(loc, enemies) ? 1.8 : 0.1,
                    1.0);
            if (sc > bestScore) {
                second = bestScore;
                bestScore = sc;
                best = loc;
            } else if (sc > second) second = sc;
        }

        if (best != null && bestScore > -0.2) {
            MapInfo before = rc.senseMapInfo(best);
            boolean frontier = isFrontierTile(rc, best, before);
            if (frontier) METRIC.frontierAttempts++;
            rc.attack(best, before.getMark() == PaintType.ALLY_SECONDARY);
            METRIC.effectivePaintActions++;
            if (before.getPaint().isEnemy()) METRIC.enemyPaintCleaned++;
            if (frontier) METRIC.frontierConverted++;
            GreedyCore.traceDecision(rc, "SOLDIER", "PAINT", bestScore, second, rejected, GreedyCore.locLabel(best));
        }
    }

    private static void paintMarkedTiles(RobotController rc, MapLocation center) throws GameActionException {
        if (!rc.isActionReady()) return;
        for (MapInfo tile : rc.senseNearbyMapInfos(center, 8)) {
            if (Clock.getBytecodesLeft() < 1500) return;
            PaintType mark = tile.getMark();
            if (mark == PaintType.EMPTY) continue;
            PaintType paint = tile.getPaint();
            if (mark == paint) continue;
            if (tile.hasRuin() || tile.isWall()) continue;
            MapLocation loc = tile.getMapLocation();
            if (rc.canAttack(loc)) {
                boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                rc.attack(loc, useSecondary);
                METRIC.effectivePaintActions++;
                if (paint.isEnemy()) METRIC.enemyPaintCleaned++;
                if (isFrontierTile(rc, loc, tile)) {
                    METRIC.frontierAttempts++;
                    METRIC.frontierConverted++;
                }
                return;
            }
        }
    }

    private static MapLocation findBestRuin(RobotController rc, MapLocation me,
                                            RobotInfo[] allies, RobotInfo[] enemies) throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        MapLocation best = null;
        double bestScore = -9999;

        bestRuinBuilders = 0;
        bestRuinEnemyPaint = 0;
        bestRuinPressure = 0;

        for (MapLocation ruin : ruins) {
            if (Clock.getBytecodesLeft() < 2800) break;
            if (rc.senseRobotAtLocation(ruin) != null) continue;

            int allyPaint = 0;
            int enemyPaint = 0;
            int total = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    MapLocation tile = ruin.translate(dx, dy);
                    if (!rc.canSenseLocation(tile)) continue;
                    MapInfo info = rc.senseMapInfo(tile);
                    if (info.hasRuin() || info.isWall()) continue;
                    total++;
                    if (info.getPaint().isAlly()) allyPaint++;
                    else if (info.getPaint().isEnemy()) enemyPaint++;
                }
            }
            int missing = total - allyPaint;

            int builders = 0;
            for (RobotInfo a : allies) {
                if (a.type == UnitType.SOLDIER && a.location.distanceSquaredTo(ruin) <= 8) builders++;
            }

            double pressure = localPressureAt(ruin, enemies);
            int expectedTurns = (missing + 1) / 2 + me.distanceSquaredTo(ruin) / 9;
            if (expectedTurns > BUILD_TIMEOUT) continue;

            double score = GreedyCore.score(
                    allyPaint,
                    enemyPaint,
                    8.0 - expectedTurns * 0.4,
                    2.0,
                    Math.max(0, MAX_BUILDERS_PER_RUIN - builders),
                    pressure,
                    missing * 0.8 + me.distanceSquaredTo(ruin) * 0.06);

            if (score > bestScore) {
                bestScore = score;
                best = ruin;
                bestRuinBuilders = builders;
                bestRuinEnemyPaint = enemyPaint;
                bestRuinPressure = pressure;
            }
        }
        return best;
    }

    private static MapLocation findBestSRP(RobotController rc, MapLocation me, RobotInfo[] enemies) throws GameActionException {
        MapLocation best = null;
        double bestScore = -9999;

        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dy = -4; dy <= 4; dy += 4) {
                if (Clock.getBytecodesLeft() < 2700) return best;
                MapLocation center = new MapLocation(
                        ((me.x + dx + 2) / 4) * 4 + 2,
                        ((me.y + dy + 2) / 4) * 4 + 2);
                if (!rc.onTheMap(center)) continue;
                if (me.distanceSquaredTo(center) > GameConstants.RESOURCE_PATTERN_RADIUS_SQUARED) continue;
                if (!rc.canMarkResourcePattern(center)) continue;

                int missing = 0;
                int enemyPaint = 0;
                boolean bad = false;
                for (int x = -2; x <= 2 && !bad; x++) {
                    for (int y = -2; y <= 2 && !bad; y++) {
                        MapLocation tile = center.translate(x, y);
                        if (!rc.canSenseLocation(tile)) continue;
                        MapInfo info = rc.senseMapInfo(tile);
                        if (info.isWall() || info.hasRuin()) continue;
                        if (info.getPaint().isEnemy()) {
                            enemyPaint++;
                            if (enemyPaint > 4) bad = true;
                        }
                        boolean wantSec = SRP_PATTERN[x + 2][y + 2] == 2;
                        PaintType want = wantSec ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                        if (info.getPaint() != want) missing++;
                    }
                }
                if (bad || missing == 0) continue;

                double pressure = localPressureAt(center, enemies);
                double score = GreedyCore.score(
                        6 - missing * 0.2,
                        enemyPaint * 0.5,
                        4.5,
                        1.9,
                        0.2,
                        pressure,
                        me.distanceSquaredTo(center) * 0.05);
                if (score > bestScore) {
                    bestScore = score;
                    best = center;
                }
            }
        }
        return best;
    }

    private static MapLocation findVisibleRefillTower(RobotInfo[] allies, MapLocation me) {
        RobotInfo bestPaint = null;
        int bpd = Integer.MAX_VALUE;
        RobotInfo bestAny = null;
        int bad = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && a.paintAmount > 30) {
                int d = me.distanceSquaredTo(a.location);
                if (a.type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                    if (d < bpd) {
                        bpd = d;
                        bestPaint = a;
                    }
                }
                if (d < bad) {
                    bad = d;
                    bestAny = a;
                }
            }
        }
        if (bestPaint != null) return bestPaint.location;
        return bestAny != null ? bestAny.location : null;
    }

    private static void initSpawnTower(RobotController rc, MapLocation me) throws GameActionException {
        spawnTower = me;
        for (RobotInfo r : rc.senseNearbyRobots(4, RobotPlayer.myTeam)) {
            if (r.type.isTowerType()) {
                int d = me.distanceSquaredTo(r.location);
                if (spawnTower.equals(me) || d < me.distanceSquaredTo(spawnTower)) spawnTower = r.location;
            }
        }
    }

    private static void broadcastTowerBuilt(RobotInfo[] allies, MapLocation loc, UnitType type) throws GameActionException {
        int msg = Messaging.encodeTowerBuilt(loc, type);
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && Messaging.trySendMessage(a.location, msg)) return;
        }
    }

    private static void sendOneTowerSignal(RobotInfo[] allies, int msg) throws GameActionException {
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && Messaging.trySendMessage(a.location, msg)) return;
        }
    }

    private static void confirmPatterns(RobotController rc, MapLocation me) throws GameActionException {
        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            for (UnitType t : new UnitType[]{UnitType.LEVEL_ONE_PAINT_TOWER,
                    UnitType.LEVEL_ONE_MONEY_TOWER, UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
                if (rc.canCompleteTowerPattern(t, ruin)) {
                    rc.completeTowerPattern(t, ruin);
                    Messaging.registerTower(ruin, t);
                    METRIC.towerCompletions++;
                    break;
                }
            }
        }
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dy = -2; dy <= 2; dy += 4) {
                MapLocation c = new MapLocation(((me.x + dx + 2) / 4) * 4 + 2, ((me.y + dy + 2) / 4) * 4 + 2);
                if (me.distanceSquaredTo(c) <= 2 && rc.canCompleteResourcePattern(c)) {
                    rc.completeResourcePattern(c);
                    METRIC.srpCompletions++;
                }
            }
        }
    }

    private static boolean isFrontierTile(RobotController rc, MapLocation loc, MapInfo info) throws GameActionException {
        PaintType p = info.getPaint();
        if (!(p == PaintType.EMPTY || p.isEnemy())) return false;
        for (Direction d : RobotPlayer.DIRS) {
            MapLocation adj = loc.add(d);
            if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) return true;
        }
        return false;
    }

    private static int countEnemyPaint(MapInfo[] nearby) {
        int c = 0;
        for (MapInfo info : nearby) {
            if (info.getPaint().isEnemy()) c++;
        }
        return c;
    }

    private static int countNonTowerEnemies(RobotInfo[] enemies) {
        int c = 0;
        for (RobotInfo e : enemies) {
            if (!e.type.isTowerType()) c++;
        }
        return c;
    }

    private static int countAlliedSoldiersNear(RobotInfo[] allies, MapLocation loc, int distSq) {
        if (loc == null) return 0;
        int c = 0;
        for (RobotInfo a : allies) {
            if (a.type == UnitType.SOLDIER && a.location.distanceSquaredTo(loc) <= distSq) c++;
        }
        return c;
    }

    private static MapLocation nearestNonTowerEnemy(MapLocation me, RobotInfo[] enemies) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            int d = me.distanceSquaredTo(e.location);
            if (d < bd) {
                bd = d;
                best = e.location;
            }
        }
        return best;
    }

    private static RobotInfo nearestNonTowerEnemyInfo(MapLocation me, RobotInfo[] enemies) {
        RobotInfo best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) continue;
            int d = me.distanceSquaredTo(e.location);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static double localPressure(MapLocation me, RobotInfo[] enemies, MapInfo[] nearby) {
        double p = 0;
        for (RobotInfo e : enemies) {
            if (e.type == UnitType.SOLDIER) p += 1.4;
            else if (e.type == UnitType.SPLASHER) p += 1.1;
            else if (e.type == UnitType.MOPPER) p += 1.0;
            else p += 0.6;
        }
        p += countEnemyPaint(nearby) * 0.08;
        return p;
    }

    private static double localPressureAt(MapLocation loc, RobotInfo[] enemies) {
        double p = 0;
        for (RobotInfo e : enemies) {
            if (loc.distanceSquaredTo(e.location) > 20) continue;
            if (e.type == UnitType.SOLDIER) p += 1.4;
            else if (e.type == UnitType.SPLASHER) p += 1.0;
            else if (e.type == UnitType.MOPPER) p += 0.8;
            else p += 0.7;
        }
        return p;
    }
}
