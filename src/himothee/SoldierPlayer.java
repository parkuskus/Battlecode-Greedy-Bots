package himothee;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;
import static himothee.Shared.LATEGAME_OFFENSIVE_TURNS;
import static himothee.Shared.LOW_PAINT_THRESHOLD;
import himothee.Shared.RobotState;
import static himothee.Shared.SPLASHER_UNLOCK_TURNS;
import static himothee.Shared.SRP_PATTERN;
import static himothee.Shared.SRP_TOWER_MIN;
import static himothee.Shared.bug0;
import static himothee.Shared.bug2;
import static himothee.Shared.checkNearbyRuins;
import static himothee.Shared.directions;
import static himothee.Shared.exploreDir;
import static himothee.Shared.exploreLoc;
import static himothee.Shared.exploreSetRound;
import static himothee.Shared.extendToEdge;
import static himothee.Shared.getIsSecondary;
import static himothee.Shared.guessEnemyLocation;
import static himothee.Shared.initExploreDir;
import static himothee.Shared.knownEnemyTowers;
import static himothee.Shared.knownTowers;
import static himothee.Shared.mapScale;
import static himothee.Shared.preRetreatState;
import static himothee.Shared.reportEnemyTowers;
import static himothee.Shared.runRetreat;
import static himothee.Shared.state;
import static himothee.Shared.targetEnemyRuin;
import static himothee.Shared.updateFriendlyTowers;
import static himothee.Shared.updateSymmetryGuess;

/**
 * Soldier logic: exploring, building towers, painting patterns, attacking, SRP.
 */
public class SoldierPlayer {

    static MapLocation paintingRuinLoc = null;
    static UnitType paintingRuinType = null;
    static int paintingTurns = 0;
    static int turnsWithoutAttack = 0;
    static int soldierExploreTurns = 0;
    static MapLocation srpTarget = null;
    static boolean needsToDeliverSave = false;
    static MapLocation deliverTarget = null;
    static int lastSaveRequestTurn = -999;
    static int deliveryTurns = 0;
    static final int MAX_DELIVERY_TURNS = 10;

    static void clearPaintingState() {
        paintingRuinLoc = null;
        needsToDeliverSave = false;
        deliverTarget = null;
        lastSaveRequestTurn = -999;
        deliveryTurns = 0;
    }

    /** True if this soldier has the lowest ID among ally soldiers near the painting ruin. */
    static boolean isLowestIdSoldierNearRuin(RobotController rc) throws GameActionException {
        if (paintingRuinLoc == null) return true;
        int myID = rc.getID();
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(paintingRuinLoc, 18, rc.getTeam());
        for (RobotInfo ally : nearbyAllies) {
            if (ally.getType() == UnitType.SOLDIER && ally.getID() < myID) {
                return false;
            }
        }
        return true;
    }

    public static void runSoldier(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();

        if (state == RobotState.STARTING) {
            if (round >= SPLASHER_UNLOCK_TURNS || rc.getNumberTowers() >= 6) {
                if (rc.getID() % 2 == 0) {
                    state = RobotState.ATTACKING;
                } else {
                    state = RobotState.EXPLORING;
                }
            } else {
                state = RobotState.EXPLORING;
            }
        }

        MapInfo standingOn = rc.senseMapInfo(rc.getLocation());
        int retreatThreshold = LOW_PAINT_THRESHOLD - 20 + (rc.getID() % 40);
        // MUCH lower retreat threshold while actively building a tower
        if (state == RobotState.PAINTING_PATTERN) {
            retreatThreshold = 10;
        }
        if (standingOn.getPaint() == PaintType.ENEMY_PRIMARY || standingOn.getPaint() == PaintType.ENEMY_SECONDARY) {
            if (state != RobotState.PAINTING_PATTERN) retreatThreshold += 30;
        }
        if (state != RobotState.RETREAT && rc.getPaint() <= retreatThreshold && !knownTowers.isEmpty()) {
            preRetreatState = state;
            state = RobotState.RETREAT;
        }

        if (state == RobotState.RETREAT) {
            runRetreat(rc);
            return;
        }

        if (state == RobotState.PAINTING_PATTERN) {
            rc.setIndicatorString("im a painter at " + paintingRuinLoc);

            // If we need to deliver a save message, walk to nearest tower first
            if (needsToDeliverSave) {
                deliveryTurns++;
                updateFriendlyTowers(rc);
                // Check if delivery succeeded (isSaving gets cleared on success)
                if (!Shared.isSaving) {
                    needsToDeliverSave = false;
                    deliverTarget = null;
                    deliveryTurns = 0;
                    rc.setIndicatorString("Save delivered! Returning to ruin " + paintingRuinLoc);
                } else if (deliveryTurns > MAX_DELIVERY_TURNS) {
                    // Give up delivery — go back to painting
                    needsToDeliverSave = false;
                    deliverTarget = null;
                    deliveryTurns = 0;
                    Shared.isSaving = false;
                    rc.setIndicatorString("Delivery timeout, back to painting");
                } else {
                    // Still need to deliver — walk toward nearest known tower
                    if (deliverTarget == null || !knownTowers.contains(deliverTarget)) {
                        deliverTarget = Shared.nearestFriendlyTower(rc);
                    }
                    if (deliverTarget != null && rc.isMovementReady()) {
                        bug2(rc, deliverTarget);
                        rc.setIndicatorString("Delivering save msg to tower at " + deliverTarget + " (" + deliveryTurns + "/" + MAX_DELIVERY_TURNS + ")");
                    }
                    paintingTurns++;
                    return;
                }
            }

            runPaintPattern(rc);
            paintingTurns++;
            updateFriendlyTowers(rc);

            // If save was requested but couldn't be delivered, only ONE soldier goes back
            if (Shared.isSaving && !needsToDeliverSave) {
                if (isLowestIdSoldierNearRuin(rc)) {
                    needsToDeliverSave = true;
                    deliverTarget = Shared.nearestFriendlyTower(rc);
                    deliveryTurns = 0;
                    lastSaveRequestTurn = rc.getRoundNum();
                } else {
                    // Someone else will deliver — keep painting
                    Shared.isSaving = false;
                }
            }

            // Periodically re-send save if tower still unbuilt and chips still low
            if (!needsToDeliverSave && paintingRuinLoc != null
                && rc.getChips() < Shared.TOWER_CHIP_COST
                && rc.getRoundNum() - lastSaveRequestTurn >= 8) {
                Shared.isSaving = true;
                Shared.savingForRuinLoc = paintingRuinLoc;
            }

        } else if (state == RobotState.EXPLORING) {
            rc.setIndicatorString("im exploring (" + soldierExploreTurns + ")");
            soldierExploreTurns++;
            MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
            MapInfo curRuin = null;
            int curDist = 999999;

            // First pass: find nearest unclaimed ruin
            for (MapInfo tile : nearbyTiles) {
                if (tile.hasRuin() && rc.senseRobotAtLocation(tile.getMapLocation()) == null) {
                    int dist = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
                    if (dist < curDist) {
                        curRuin = tile;
                        curDist = dist;
                    }
                }
            }

            // Only consider switching to attack if NO ruin found
            if (curRuin == null) {
                for (MapInfo tile : nearbyTiles) {
                    if (rc.canSenseLocation(tile.getMapLocation()) && rc.senseRobotAtLocation(tile.getMapLocation()) != null && rc.senseRobotAtLocation(tile.getMapLocation()).getTeam() != rc.getTeam()) {
                        if (round >= SPLASHER_UNLOCK_TURNS || rc.getNumberTowers() >= 4) {
                            state = RobotState.ATTACKING;
                        }
                        break;
                    }
                }
            }

            if (curRuin != null) {
                soldierExploreTurns = 0;
                if (curDist > 4) bug0(rc, curRuin.getMapLocation());
                else {
                    // Allow claiming if no other soldier is painting this ruin within close range
                    boolean anotherSoldierPainting = false;
                    RobotInfo[] nearbyAllies = rc.senseNearbyRobots(curRuin.getMapLocation(), 4, rc.getTeam());
                    for (RobotInfo ally : nearbyAllies) {
                        if (ally.getType() == UnitType.SOLDIER && ally.getID() != rc.getID()) {
                            anotherSoldierPainting = true;
                            break;
                        }
                    }
                    if (!anotherSoldierPainting) {
                        state = RobotState.PAINTING_PATTERN;
                        paintingRuinType = detectExistingPatternType(rc, curRuin.getMapLocation());
                        turnsWithoutAttack = 0;
                        paintingTurns = 0;
                        paintingRuinLoc = curRuin.getMapLocation();
                    } else {
                        // Another soldier is very close — move away to find other ruins
                        Direction awayFromRuin = curRuin.getMapLocation().directionTo(rc.getLocation());
                        if (rc.canMove(awayFromRuin)) rc.move(awayFromRuin);
                        else if (rc.canMove(awayFromRuin.rotateLeft())) rc.move(awayFromRuin.rotateLeft());
                        else if (rc.canMove(awayFromRuin.rotateRight())) rc.move(awayFromRuin.rotateRight());
                    }
                }
            } else if (rc.getID() % 5 == 0 && rc.getNumberTowers() >= SRP_TOWER_MIN && rc.getPaint() >= 150) {
                if (srpTarget != null) {
                    if (rc.canSenseLocation(srpTarget)) {
                        if (!rc.canMarkResourcePattern(srpTarget)) {
                            srpTarget = null;
                        }
                    }
                }
                if (srpTarget == null) {
                    srpTarget = findBestSRP(rc);
                }
                if (srpTarget != null) {
                    if (rc.getLocation().distanceSquaredTo(srpTarget) > 2) {
                        bug0(rc, srpTarget);
                    }
                    paintSRPTiles(rc, srpTarget);
                    if (rc.getLocation().distanceSquaredTo(srpTarget) <= 2 && rc.canCompleteResourcePattern(srpTarget)) {
                        rc.completeResourcePattern(srpTarget);
                        srpTarget = null;
                    }
                }
            }

            if (soldierExploreTurns >= 60 && (round >= SPLASHER_UNLOCK_TURNS || rc.getNumberTowers() >= 4)) {
                state = RobotState.ATTACKING;
                soldierExploreTurns = 0;
                targetEnemyRuin = null;
            } else if (rc.isMovementReady()) {
                if (exploreDir == null) {
                    initExploreDir(rc);
                }
                boolean needNewDir = rc.getLocation().distanceSquaredTo(exploreLoc) <= 8
                    || (round - exploreSetRound) > 30
                    || Shared.isNearEdge(rc, rc.getLocation());
                if (needNewDir) {
                    if (Shared.isNearEdge(rc, rc.getLocation())) {
                        // Near edge: pick direction biased toward center
                        exploreDir = Shared.pickExploreDir(rc, rc.getLocation());
                    } else {
                        int dirIdx = (rc.getID() + round / 30) % 8;
                        exploreDir = directions[dirIdx];
                    }
                    exploreLoc = extendToEdge(rc, rc.getLocation(), exploreDir);
                    exploreSetRound = round;
                }
                bug0(rc, exploreLoc);
            }

            updateFriendlyTowers(rc);
            checkNearbyRuins(rc);
            updateSymmetryGuess(rc);
            reportEnemyTowers(rc);

        } else if (state == RobotState.ATTACKING) {
            updateSymmetryGuess(rc);
            reportEnemyTowers(rc);

            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
            int allyCombatants = 0;
            for (RobotInfo ally : nearbyAllies) {
                if (ally.getType() == UnitType.SOLDIER || ally.getType() == UnitType.SPLASHER) {
                    allyCombatants++;
                }
            }

            if (targetEnemyRuin == null) {
                MapLocation[] infos = rc.senseNearbyRuins(-1);
                MapLocation ruin;

                for (MapLocation info : infos) {
                    ruin = info;
                    if (ruin != null && rc.senseRobotAtLocation(ruin) != null && rc.senseRobotAtLocation(ruin).getTeam().opponent() == rc.getTeam()) {
                        targetEnemyRuin = ruin;
                        break;
                    }
                }

                if (targetEnemyRuin == null && !knownEnemyTowers.isEmpty()) {
                    MapLocation closest = null;
                    int closestDist = Integer.MAX_VALUE;
                    for (MapLocation et : knownEnemyTowers) {
                        int d = rc.getLocation().distanceSquaredTo(et);
                        if (d < closestDist) { closestDist = d; closest = et; }
                    }
                    targetEnemyRuin = closest;
                }

                if (targetEnemyRuin == null && infos.length > 0) {
                    ruin = infos[0];
                    if (rc.senseRobotAtLocation(ruin) == null) {
                        state = RobotState.EXPLORING;
                    }
                    targetEnemyRuin = guessEnemyLocation(rc, ruin);
                }

                if (targetEnemyRuin == null && !knownTowers.isEmpty()) {
                    targetEnemyRuin = guessEnemyLocation(rc, knownTowers.get(0));
                }
            }

            if (targetEnemyRuin != null) {
                if (rc.canSenseLocation(targetEnemyRuin)) {
                    if (rc.senseRobotAtLocation(targetEnemyRuin) == null || (rc.canSenseRobotAtLocation(targetEnemyRuin) && rc.senseRobotAtLocation(targetEnemyRuin).getTeam() == rc.getTeam())) {
                        state = RobotState.EXPLORING;
                        targetEnemyRuin = null;
                    }
                }

                if (targetEnemyRuin != null) {
                    int dsquared = rc.getLocation().distanceSquaredTo(targetEnemyRuin);

                    if (dsquared <= 8) {
                        if (allyCombatants >= 1 || round >= LATEGAME_OFFENSIVE_TURNS) {
                            if (rc.canAttack(targetEnemyRuin)) {
                                rc.attack(targetEnemyRuin);
                            }
                            Direction away = rc.getLocation().directionTo(targetEnemyRuin).opposite();
                            if (rc.canMove(away)) {
                                rc.move(away);
                            } else if (rc.canMove(away.rotateLeft())) {
                                rc.move(away.rotateLeft());
                            } else if (rc.canMove(away.rotateRight())) {
                                rc.move(away.rotateRight());
                            }
                        } else {
                            rc.setIndicatorString("Waiting for allies near " + targetEnemyRuin);
                            if (rc.canAttack(targetEnemyRuin)) {
                                rc.attack(targetEnemyRuin);
                            }
                        }
                    } else {
                        for (Direction d : directions) {
                            MapLocation newLoc = rc.getLocation().add(d);
                            if (newLoc.isWithinDistanceSquared(targetEnemyRuin, 8)) {
                                if (rc.canMove(d)) {
                                    rc.move(d);
                                    if (rc.canAttack(targetEnemyRuin)) {
                                        rc.attack(targetEnemyRuin);
                                    }
                                    break;
                                }
                            }
                        }
                        bug2(rc, targetEnemyRuin);
                    }

                    rc.setIndicatorDot(targetEnemyRuin, 0, 255, 0);
                    rc.setIndicatorString("Moving to enemy ruin at " + targetEnemyRuin);
                }
            }
        }

        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            // If painting a pattern, use the correct color for this tile
            if (state == RobotState.PAINTING_PATTERN && paintingRuinLoc != null
                && Shared.isWithinPattern(paintingRuinLoc, rc.getLocation())) {
                boolean isSecondary = getIsSecondary(paintingRuinLoc, rc.getLocation(), paintingRuinType);
                rc.attack(rc.getLocation(), isSecondary);
            } else {
                rc.attack(rc.getLocation());
            }
        }

        tryCompletePatterns(rc);
    }

    // ========== PAINTING PATTERN ==========

    public static void runPaintPattern(RobotController rc) throws GameActionException {
        // Validate ruin is still unclaimed
        if (paintingRuinLoc == null) {
            state = RobotState.EXPLORING;
            clearPaintingState();
            return;
        }
        if (rc.canSenseLocation(paintingRuinLoc)) {
            RobotInfo atRuin = rc.senseRobotAtLocation(paintingRuinLoc);
            if (atRuin != null) {
                // Tower already built here — done
                state = RobotState.EXPLORING;
                clearPaintingState();
                return;
            }
        }

        // If we're far from the ruin (e.g. after retreat), navigate back first
        int distToRuin = rc.getLocation().distanceSquaredTo(paintingRuinLoc);
        if (distToRuin > 18) {
            // Too far to paint — walk back
            bug0(rc, paintingRuinLoc);
            rc.setIndicatorString("Returning to ruin at " + paintingRuinLoc);
            // Give up if we've been trying to get back for too long
            if (paintingTurns > 30) {
                state = RobotState.EXPLORING;
                clearPaintingState();
            }
            return;
        }

        if (rc.canCompleteTowerPattern(paintingRuinType, paintingRuinLoc)) {
            rc.completeTowerPattern(paintingRuinType, paintingRuinLoc);
            state = RobotState.EXPLORING;
            clearPaintingState();
            return;
        }

        MapLocation bestUnpainted = null;
        int bestUnpaintedDist = Integer.MAX_VALUE;
        MapLocation bestAttackable = null;
        int bestAttackableDist = Integer.MAX_VALUE;
        boolean paintedSomething = false;
        int tilesNeedingPaint = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation tile = paintingRuinLoc.translate(dx, dy);
                if (tile.equals(paintingRuinLoc)) continue;
                if (!rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (info.isWall() || info.hasRuin()) continue;

                boolean isSecondary = getIsSecondary(paintingRuinLoc, tile, paintingRuinType);
                PaintType current = info.getPaint();
                boolean needsPaint = (current == PaintType.EMPTY)
                    || (current == PaintType.ENEMY_PRIMARY || current == PaintType.ENEMY_SECONDARY)
                    || (current.isAlly() && current.isSecondary() != isSecondary);

                if (needsPaint) {
                    tilesNeedingPaint++;
                    int dist = rc.getLocation().distanceSquaredTo(tile);
                    if (rc.isActionReady() && rc.canAttack(tile)) {
                        // Track closest attackable tile
                        if (dist < bestAttackableDist) {
                            bestAttackableDist = dist;
                            bestAttackable = tile;
                        }
                    }
                    if (dist < bestUnpaintedDist) {
                        bestUnpaintedDist = dist;
                        bestUnpainted = tile;
                    }
                }
            }
        }

        // Paint the closest attackable tile (not just first found)
        if (bestAttackable != null && rc.isActionReady()) {
            boolean isSecondary = getIsSecondary(paintingRuinLoc, bestAttackable, paintingRuinType);
            rc.attack(bestAttackable, isSecondary);
            paintedSomething = true;
            turnsWithoutAttack = 0;
        }

        // Only count as stuck if action ready but NO tiles are reachable AND no movement target
        if (!paintedSomething && rc.isActionReady() && bestUnpainted == null) {
            turnsWithoutAttack++;
        } else if (!paintedSomething && rc.isActionReady() && bestAttackable == null && bestUnpainted != null) {
            // Tiles exist but can't reach them — slow increment
            turnsWithoutAttack++;
        } else if (paintedSomething) {
            turnsWithoutAttack = 0;
        }

        // Request chip saving when pattern is getting close and we can't afford the tower
        // Factor in estimated income: ~2 chips/turn base + ~2 per money tower
        if (tilesNeedingPaint <= 10 && tilesNeedingPaint > 0) {
            int estimatedIncome = 2 + Shared.knownMoneyTowers.size() * 2;
            int projectedChips = rc.getChips() + estimatedIncome * tilesNeedingPaint;
            if (projectedChips < Shared.TOWER_CHIP_COST) {
                Shared.isSaving = true;
                Shared.savingForRuinLoc = paintingRuinLoc;
            }
        }

        rc.setIndicatorString("Painting " + paintingRuinLoc + " (" + tilesNeedingPaint + " left, " + rc.getChips() + " chips)");

        // Movement: stay tethered to the ruin, move toward unpainted tiles
        if (rc.isMovementReady()) {
            if (bestUnpainted != null) {
                // Only move toward unpainted tile if it doesn't take us too far from ruin
                MapLocation myLoc = rc.getLocation();
                Direction toward = myLoc.directionTo(bestUnpainted);
                MapLocation afterMove = myLoc.add(toward);
                if (afterMove.distanceSquaredTo(paintingRuinLoc) <= 18) {
                    if (rc.canMove(toward)) rc.move(toward);
                    else if (rc.canMove(toward.rotateLeft())) rc.move(toward.rotateLeft());
                    else if (rc.canMove(toward.rotateRight())) rc.move(toward.rotateRight());
                } else {
                    // Move closer to ruin instead
                    Direction toRuin = myLoc.directionTo(paintingRuinLoc);
                    if (rc.canMove(toRuin)) rc.move(toRuin);
                    else if (rc.canMove(toRuin.rotateLeft())) rc.move(toRuin.rotateLeft());
                    else if (rc.canMove(toRuin.rotateRight())) rc.move(toRuin.rotateRight());
                }
            } else if (distToRuin > 2) {
                Direction toward = rc.getLocation().directionTo(paintingRuinLoc);
                if (rc.canMove(toward)) rc.move(toward);
                else if (rc.canMove(toward.rotateLeft())) rc.move(toward.rotateLeft());
                else if (rc.canMove(toward.rotateRight())) rc.move(toward.rotateRight());
            }
        }

        if (rc.canCompleteTowerPattern(paintingRuinType, paintingRuinLoc)) {
            rc.completeTowerPattern(paintingRuinType, paintingRuinLoc);
            state = RobotState.EXPLORING;
            clearPaintingState();
            return;
        }

        // Only give up if we've been truly stuck for a long time
        if (turnsWithoutAttack > 30 && tilesNeedingPaint > 4) {
            state = RobotState.EXPLORING;
            clearPaintingState();
        } else if (turnsWithoutAttack > 50) {
            // Absolute max patience
            state = RobotState.EXPLORING;
            clearPaintingState();
        }
    }

    // ========== TOWER TYPE SELECTION ==========

    /**
     * Detect if a ruin already has partial paint matching one tower type.
     * If so, continue that type instead of picking fresh (avoids overwriting progress).
     */
    public static UnitType detectExistingPatternType(RobotController rc, MapLocation ruinLoc) throws GameActionException {
        // Early game: always use the money-first tower selection.
        // Ambient paint from the starting tower can falsely match paint tower patterns.
        if (rc.getNumberTowers() < 3) {
            return getNewTowerType(rc);
        }

        int paintMatchCount = 0;
        int moneyMatchCount = 0;
        int totalCheckable = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation tile = ruinLoc.translate(dx, dy);
                if (tile.equals(ruinLoc)) continue;
                if (!rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (info.isWall() || info.hasRuin()) continue;
                PaintType current = info.getPaint();
                if (!current.isAlly()) continue; // only count already-painted ally tiles

                totalCheckable++;
                boolean isPaintSec = getIsSecondary(ruinLoc, tile, UnitType.LEVEL_ONE_PAINT_TOWER);
                boolean isMoneySec = getIsSecondary(ruinLoc, tile, UnitType.LEVEL_ONE_MONEY_TOWER);
                if (current.isSecondary() == isPaintSec) paintMatchCount++;
                if (current.isSecondary() == isMoneySec) moneyMatchCount++;
            }
        }

        // If there's meaningful existing progress (3+ tiles) and one type clearly matches better, use it
        if (totalCheckable >= 3 && paintMatchCount != moneyMatchCount) {
            return paintMatchCount > moneyMatchCount ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        // No clear existing progress — pick fresh
        return getNewTowerType(rc);
    }

    public static UnitType getNewTowerType(RobotController rc) {
        int numTowers = rc.getNumberTowers();
        // Heavily prioritize money towers early — they fund everything else.
        // First several towers should almost all be money. Only sprinkle in paint
        // towers once we have a solid income base.
        if (mapScale < 1.33) {
            // Small map: first 3 money, then alternate with money bias (2:1)
            if (numTowers < 3) return UnitType.LEVEL_ONE_MONEY_TOWER;
            return numTowers % 3 == 0 ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
        } else if (mapScale < 1.67) {
            // Medium map: first 4 money, then 2:1 money:paint
            if (numTowers < 4) return UnitType.LEVEL_ONE_MONEY_TOWER;
            return numTowers % 3 == 0 ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
        } else {
            // Large map: first 5 money, then 2:1 money:paint
            if (numTowers < 5) return UnitType.LEVEL_ONE_MONEY_TOWER;
            return numTowers % 3 == 0 ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
        }
    }

    // ========== SRP (SPECIAL RESOURCE PATTERN) ==========

    static MapLocation alignSRPCenter(int x, int y) {
        return new MapLocation(((x + 2) / 4) * 4 + 2, ((y + 2) / 4) * 4 + 2);
    }

    static MapLocation findBestSRP(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        for (int dx = -4; dx <= 4; dx += 4) {
            for (int dy = -4; dy <= 4; dy += 4) {
                MapLocation center = alignSRPCenter(me.x + dx, me.y + dy);
                if (!rc.onTheMap(center)) continue;
                if (me.distanceSquaredTo(center) > 20) continue;
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

    static boolean paintSRPTiles(RobotController rc, MapLocation center) throws GameActionException {
        if (!rc.isActionReady()) return false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation tile = center.translate(dx, dy);
                if (!rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (info.isWall() || info.hasRuin()) continue;
                boolean wantSecondary = SRP_PATTERN[dx + 2][dy + 2] == 2;
                PaintType want = wantSecondary ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                PaintType current = info.getPaint();
                if (current != want && !current.isEnemy() && rc.canAttack(tile)) {
                    rc.attack(tile, wantSecondary);
                    return true;
                }
            }
        }
        return false;
    }

    static void tryCompletePatterns(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        for (int dx = -2; dx <= 2; dx += 4) {
            for (int dy = -2; dy <= 2; dy += 4) {
                MapLocation c = alignSRPCenter(me.x + dx, me.y + dy);
                if (me.distanceSquaredTo(c) <= 2 && rc.canCompleteResourcePattern(c)) {
                    rc.completeResourcePattern(c);
                }
            }
        }
    }
}
