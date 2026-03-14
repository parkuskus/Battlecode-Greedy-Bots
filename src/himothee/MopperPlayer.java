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
import static himothee.Shared.MOPPER_LOW_PAINT_THRESHOLD;
import himothee.Shared.RobotState;
import static himothee.Shared.bug0;
import static himothee.Shared.bug2;
import static himothee.Shared.directions;
import static himothee.Shared.guessEnemyLocation;
import static himothee.Shared.isWithinPattern;
import static himothee.Shared.knownEnemyTowers;
import static himothee.Shared.knownTowers;
import static himothee.Shared.preRetreatState;
import static himothee.Shared.reportEnemyTowers;
import static himothee.Shared.rng;
import static himothee.Shared.runRetreat;
import static himothee.Shared.state;
import static himothee.Shared.targetEnemyRuin;
import static himothee.Shared.updateFriendlyTowers;
import static himothee.Shared.updateSymmetryGuess;

/**
 * Mopper logic: attack-first lifesteal, paint transfer healer, help build at ruins.
 */
public class MopperPlayer {

    static MapLocation mopperHelpRuin = null;

    public static void runMopper(RobotController rc) throws GameActionException {
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        boolean enemiesNearby = enemyRobots.length > 0;

        // Don't retreat if enemies are nearby — free attacks + lifesteal can sustain us
        if (state != RobotState.RETREAT && rc.getPaint() <= MOPPER_LOW_PAINT_THRESHOLD
            && !knownTowers.isEmpty() && !enemiesNearby) {
            preRetreatState = state;
            state = RobotState.RETREAT;
        }

        if (state == RobotState.RETREAT) {
            // Break out of retreat if enemies appear — stay and fight on frontline
            if (enemiesNearby && rc.getPaint() > 5) {
                state = preRetreatState != null ? preRetreatState : RobotState.ATTACKING;
                preRetreatState = null;
            } else {
                runRetreat(rc);
                return;
            }
        }

        updateFriendlyTowers(rc);
        updateSymmetryGuess(rc);
        reportEnemyTowers(rc);

        int round = rc.getRoundNum();

        if (state == RobotState.STARTING) {
            int roll = rc.getID() % 10;
            if (roll < 7) state = RobotState.ATTACKING;
            else state = RobotState.HELPING_BUILD;
        }

        // Late game: everyone attacks
        if (round >= LATEGAME_OFFENSIVE_TURNS && state != RobotState.RETREAT) {
            state = RobotState.ATTACKING;
            mopperHelpRuin = null;
        }

        RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearestEnemy = null;
        int nearestEnemyDist = Integer.MAX_VALUE;
        for (RobotInfo enemy : enemyRobots) {
            int dist = rc.getLocation().distanceSquaredTo(enemy.getLocation());
            if (dist < nearestEnemyDist) {
                nearestEnemyDist = dist;
                nearestEnemy = enemy.getLocation();
            }
        }

        // === PRIORITY 1: Combat — lifesteal from enemies (free action, always do first) ===
        if (nearestEnemy != null) {
            mopperCombat(rc, enemyRobots, nearestEnemy);
        }

        // === PRIORITY 2: Mop enemy paint nearby ===
        if (rc.isActionReady()) {
            mopBestEnemyPaint(rc);
        }

        // === PRIORITY 3: Transfer paint to low-paint allies (healer) ===
        if (rc.isActionReady()) {
            transferPaintToAlly(rc, allyRobots);
        }

        // === Auto-detect nearby ruin needing mop — ANY mopper helps instantly ===
        MapLocation ruinNeedingHelp = findRuinNeedingMop(rc);
        if (ruinNeedingHelp != null) {
            mopperHelpRuin = ruinNeedingHelp;
            state = RobotState.HELPING_BUILD;
        }

        // ==================== HELPING BUILD ====================
        if (state == RobotState.HELPING_BUILD) {
            if (mopperHelpRuin == null) {
                mopperHelpRuin = findRuinNeedingMop(rc);
            }

            if (mopperHelpRuin != null) {
                // Check if tower already built
                if (rc.canSenseLocation(mopperHelpRuin) && rc.senseRobotAtLocation(mopperHelpRuin) != null) {
                    mopperHelpRuin = null;
                    state = RobotState.ATTACKING;
                } else {
                    int distToRuin = rc.getLocation().distanceSquaredTo(mopperHelpRuin);
                    if (distToRuin > 8) {
                        bug2(rc, mopperHelpRuin);
                    } else {
                        if (rc.isActionReady()) {
                            mopPatternArea(rc, mopperHelpRuin);
                        }
                        // Circle around the ruin to mop all sides
                        Direction toRuin = rc.getLocation().directionTo(mopperHelpRuin);
                        Direction tangent = toRuin.rotateRight().rotateRight();
                        if (rc.canMove(tangent)) rc.move(tangent);
                        else if (rc.canMove(toRuin)) rc.move(toRuin);
                    }
                    rc.setIndicatorString("MOPPER helping build at " + mopperHelpRuin);
                }
            } else {
                // No ruin needs help — go attack
                state = RobotState.ATTACKING;
            }

        // ==================== ATTACKING ====================
        } else if (state == RobotState.ATTACKING) {

            // Pick attack target
            if (targetEnemyRuin == null) {
                // Visible enemy tower
                MapLocation[] ruinInfos = rc.senseNearbyRuins(-1);
                for (MapLocation ruin : ruinInfos) {
                    if (rc.canSenseRobotAtLocation(ruin)) {
                        RobotInfo r = rc.senseRobotAtLocation(ruin);
                        if (r != null && r.getTeam() != rc.getTeam()) {
                            targetEnemyRuin = ruin;
                            break;
                        }
                    }
                }
                // Known enemy towers
                if (targetEnemyRuin == null && !knownEnemyTowers.isEmpty()) {
                    MapLocation closest = null;
                    int closestDist = Integer.MAX_VALUE;
                    for (MapLocation et : knownEnemyTowers) {
                        int d = rc.getLocation().distanceSquaredTo(et);
                        if (d < closestDist) { closestDist = d; closest = et; }
                    }
                    targetEnemyRuin = closest;
                }
                // Guess enemy positions
                if (targetEnemyRuin == null && !knownTowers.isEmpty()) {
                    targetEnemyRuin = guessEnemyLocation(rc, knownTowers.get(rng.nextInt(knownTowers.size())));
                }
                if (targetEnemyRuin == null) {
                    targetEnemyRuin = guessEnemyLocation(rc, rc.getLocation());
                }
            }

            // Clear destroyed target
            if (targetEnemyRuin != null && rc.canSenseLocation(targetEnemyRuin)) {
                RobotInfo atTarget = rc.senseRobotAtLocation(targetEnemyRuin);
                if (atTarget == null || atTarget.getTeam() == rc.getTeam()) {
                    targetEnemyRuin = null;
                }
            }

            // Movement: chase enemies, push toward target, seek enemy paint
            if (nearestEnemy != null && nearestEnemyDist <= 8 && rc.isMovementReady()) {
                Direction toward = rc.getLocation().directionTo(nearestEnemy);
                if (rc.canMove(toward)) rc.move(toward);
                else if (rc.canMove(toward.rotateLeft())) rc.move(toward.rotateLeft());
                else if (rc.canMove(toward.rotateRight())) rc.move(toward.rotateRight());
            } else if (rc.isMovementReady()) {
                // Score movement: bias toward enemy paint (our bread and butter) and target
                Direction bestDir = null;
                int bestScore = -1;
                MapLocation enemySide = guessEnemyLocation(rc, rc.getLocation());

                for (Direction d : directions) {
                    if (!rc.canMove(d)) continue;
                    MapLocation newLoc = rc.getLocation().add(d);
                    int score = 0;
                    MapInfo[] nearby = rc.senseNearbyMapInfos(newLoc, 5);
                    for (MapInfo info : nearby) {
                        PaintType p = info.getPaint();
                        if (p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) score += 4;
                        else if (p == PaintType.EMPTY) score += 1;
                    }
                    // Push toward target
                    if (targetEnemyRuin != null &&
                        newLoc.distanceSquaredTo(targetEnemyRuin) < rc.getLocation().distanceSquaredTo(targetEnemyRuin)) {
                        score += 5;
                    }
                    // Bias toward enemy side
                    if (newLoc.distanceSquaredTo(enemySide) < rc.getLocation().distanceSquaredTo(enemySide)) {
                        score += 3;
                    }
                    if (score > bestScore) { bestScore = score; bestDir = d; }
                }
                if (bestDir != null && bestScore > 3) rc.move(bestDir);
                else if (targetEnemyRuin != null) bug2(rc, targetEnemyRuin);
                else bug0(rc, enemySide);
            }

            // Swing at enemies after moving
            if (rc.isActionReady() && nearestEnemy != null) {
                Direction swingDir = rc.getLocation().directionTo(nearestEnemy);
                if (rc.canMopSwing(swingDir)) {
                    rc.mopSwing(swingDir);
                }
            }

            // Mop enemy paint after moving
            if (rc.isActionReady()) {
                mopBestEnemyPaint(rc);
            }

            // Transfer paint after all attacks
            if (rc.isActionReady()) {
                transferPaintToAlly(rc, allyRobots);
            }

            String targetStr = (targetEnemyRuin != null) ? targetEnemyRuin.toString() : "roaming";
            rc.setIndicatorString("MOPPER atk → " + targetStr + " (paint:" + rc.getPaint() + ")");
        }
    }

    // ========== PAINT TRANSFER (HEALER) ==========

    /**
     * Transfer paint to a nearby low-paint ally. Prioritizes allies with the
     * lowest paint ratio. Only gives if we have enough to spare (>40%).
     */
    static void transferPaintToAlly(RobotController rc, RobotInfo[] allies) throws GameActionException {
        if (!rc.isActionReady()) return;
        int myPaint = rc.getPaint();
        int myMax = rc.getType().paintCapacity;
        // Only give paint if we have more than 40%
        if (myPaint < myMax * 0.4) return;

        RobotInfo bestTarget = null;
        double lowestRatio = 0.5; // only help allies below 50% paint

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) continue;
            if (ally.getType() == UnitType.MOPPER) continue; // moppers get their own paint via lifesteal
            if (rc.getLocation().distanceSquaredTo(ally.getLocation()) > 2) continue;
            double ratio = (double) ally.getPaintAmount() / ally.getType().paintCapacity;
            if (ratio < lowestRatio) {
                lowestRatio = ratio;
                bestTarget = ally;
            }
        }

        if (bestTarget != null) {
            int give = Math.min(myPaint / 3,
                bestTarget.getType().paintCapacity - bestTarget.getPaintAmount());
            if (give > 0 && rc.canTransferPaint(bestTarget.getLocation(), give)) {
                rc.transferPaint(bestTarget.getLocation(), give);
            }
        }
    }

    // ========== MOPPER COMBAT ==========

    static void mopBestEnemyPaint(RobotController rc) throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        for (MapInfo tile : nearby) {
            PaintType p = tile.getPaint();
            if ((p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) && rc.canAttack(tile.getMapLocation())) {
                rc.attack(tile.getMapLocation());
                return;
            }
        }
    }

    static void mopperCombat(RobotController rc, RobotInfo[] enemies, MapLocation nearestEnemy) throws GameActionException {
        if (!rc.isActionReady()) return;

        // Try mop swing first if it hits 2+ enemies
        Direction bestSwing = null;
        int bestSwingHits = 0;
        for (Direction cardinal : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (!rc.canMopSwing(cardinal)) continue;
            int hits = countEnemiesInSwing(rc, cardinal, enemies);
            if (hits > bestSwingHits) {
                bestSwingHits = hits;
                bestSwing = cardinal;
            }
        }

        if (bestSwingHits >= 2 && bestSwing != null) {
            rc.mopSwing(bestSwing);
            return;
        }

        // Single target attack — prioritize highest paint enemies (more to steal)
        RobotInfo bestTarget = null;
        int bestTargetPaint = -1;
        for (RobotInfo enemy : enemies) {
            if (enemy.getType().isTowerType()) continue;
            if (rc.canAttack(enemy.getLocation())) {
                if (enemy.getPaintAmount() > bestTargetPaint) {
                    bestTargetPaint = enemy.getPaintAmount();
                    bestTarget = enemy;
                }
            }
        }
        if (bestTarget != null) {
            rc.attack(bestTarget.getLocation());
            return;
        }

        // Even 1-hit swing is free value
        if (bestSwingHits >= 1 && bestSwing != null) {
            rc.mopSwing(bestSwing);
        }
    }

    static int countEnemiesInSwing(RobotController rc, Direction swingDir, RobotInfo[] enemies) {
        MapLocation loc = rc.getLocation();
        MapLocation step1 = loc.add(swingDir);
        MapLocation step2 = step1.add(swingDir);
        Direction perp = swingDir.rotateRight().rotateRight();
        MapLocation[] swingTiles = {
            step1, step1.add(perp), step1.add(perp.opposite()),
            step2, step2.add(perp), step2.add(perp.opposite())
        };
        int count = 0;
        for (RobotInfo enemy : enemies) {
            MapLocation eLoc = enemy.getLocation();
            for (MapLocation t : swingTiles) {
                if (eLoc.equals(t)) { count++; break; }
            }
        }
        return count;
    }

    // ========== BUILD HELPERS ==========

    static MapLocation findRuinNeedingMop(RobotController rc) throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        MapLocation bestRuin = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapLocation ruin : ruins) {
            if (rc.senseRobotAtLocation(ruin) != null) continue;
            MapInfo[] patternTiles = rc.senseNearbyMapInfos(ruin, 8);
            boolean hasEnemyPaint = false;
            for (MapInfo tile : patternTiles) {
                PaintType p = tile.getPaint();
                if (p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) {
                    hasEnemyPaint = true;
                    break;
                }
            }
            if (hasEnemyPaint) {
                int dist = rc.getLocation().distanceSquaredTo(ruin);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestRuin = ruin;
                }
            }
        }
        return bestRuin;
    }

    static void mopPatternArea(RobotController rc, MapLocation ruinLoc) throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(ruinLoc, 8);
        for (MapInfo tile : tiles) {
            PaintType p = tile.getPaint();
            if ((p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY)
                && isWithinPattern(ruinLoc, tile.getMapLocation())
                && rc.canAttack(tile.getMapLocation())) {
                rc.attack(tile.getMapLocation());
                return;
            }
        }
    }
}
