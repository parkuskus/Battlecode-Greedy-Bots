package himothee;

import java.util.ArrayList;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;
import himothee.Shared.RobotState;
import static himothee.Shared.bug0;
import static himothee.Shared.directions;
import static himothee.Shared.guessEnemyLocation;
import static himothee.Shared.knownEnemyTowers;
import static himothee.Shared.knownTowers;
import static himothee.Shared.preRetreatState;
import static himothee.Shared.reportEnemyTowers;
import static himothee.Shared.runRetreat;
import static himothee.Shared.state;
import static himothee.Shared.targetEnemyRuin;
import static himothee.Shared.updateFriendlyTowers;
import static himothee.Shared.updateSymmetryGuess;

/**
 * Splasher logic: pushing into enemy territory, splash-attacking, kiting towers.
 */
public class SplasherPlayer {

    static int splasherTargetIdx = 0;
    static MapLocation pushTarget = null;
    static int pushTargetAge = 0;
    // Remember where enemy towers were — keep pushing that direction even after destroyed
    static ArrayList<MapLocation> enemyTowerMemory = new ArrayList<>();

    public static void runSplasher(RobotController rc) throws GameActionException {
        int splasherRetreatThreshold = (int)(rc.getType().paintCapacity * 0.10);
        if (state != RobotState.RETREAT && rc.getPaint() <= splasherRetreatThreshold && !knownTowers.isEmpty()) {
            preRetreatState = state;
            state = RobotState.RETREAT;
        }

        if (state == RobotState.RETREAT) {
            runRetreat(rc);
            return;
        }

        if (state == RobotState.STARTING) {
            state = RobotState.ATTACKING;
        }

        updateFriendlyTowers(rc);
        updateSymmetryGuess(rc);
        reportEnemyTowers(rc);

        // Memorize enemy tower locations — even after they're destroyed we keep the positions
        for (MapLocation et : knownEnemyTowers) {
            if (!enemyTowerMemory.contains(et)) {
                enemyTowerMemory.add(et);
            }
        }

        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapLocation nearestEnemyTower = null;
        int nearestTowerDist = Integer.MAX_VALUE;
        for (RobotInfo enemy : enemyRobots) {
            int dist = rc.getLocation().distanceSquaredTo(enemy.getLocation());
            if (enemy.getType().isTowerType()) {
                if (dist < nearestTowerDist) {
                    nearestTowerDist = dist;
                    nearestEnemyTower = enemy.getLocation();
                }
            }
        }

        // Count nearby allied splashers for mild spread-out
        RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        int nearbySplashers = 0;
        int cx = 0, cy = 0;
        MapLocation splasherCentroid = rc.getLocation();
        for (RobotInfo ally : allyRobots) {
            if (ally.getType() == UnitType.SPLASHER) {
                nearbySplashers++;
                cx += ally.getLocation().x;
                cy += ally.getLocation().y;
            }
        }
        if (nearbySplashers > 0) {
            splasherCentroid = new MapLocation(cx / nearbySplashers, cy / nearbySplashers);
        }

        // Pick push target — aggressively toward enemy territory
        pushTargetAge++;
        if (pushTarget != null && rc.getLocation().distanceSquaredTo(pushTarget) <= 8) {
            pushTarget = null;
        }
        if (pushTarget == null || pushTargetAge > 30) {
            pushTarget = pickPushTarget(rc);
            pushTargetAge = 0;
        }

        // Visible enemy tower overrides push target
        if (nearestEnemyTower != null) {
            targetEnemyRuin = nearestEnemyTower;
        }

        // Splash attack if worthwhile
        if (rc.isActionReady()) {
            int enemyTilesNearby = 0;
            MapInfo[] depthCheck = rc.senseNearbyMapInfos(8);
            for (MapInfo dc : depthCheck) {
                if (!dc.isWall() && !dc.hasRuin() && dc.getPaint().isEnemy()) enemyTilesNearby++;
            }
            boolean shouldSplash = (nearestEnemyTower != null && nearestTowerDist <= 20)
                || enemyTilesNearby >= 3;
            if (shouldSplash) {
                MapLocation bestTarget = findBestSplashTarget(rc, nearestEnemyTower);
                if (bestTarget != null) {
                    rc.attack(bestTarget);
                }
            }
        }

        // Movement: kite enemy towers, otherwise push hard toward target
        MapLocation moveTarget = (targetEnemyRuin != null) ? targetEnemyRuin : pushTarget;

        if (nearestEnemyTower != null && rc.getLocation().distanceSquaredTo(nearestEnemyTower) <= 16) {
            // Kite away from tower
            Direction away = rc.getLocation().directionTo(nearestEnemyTower).opposite();
            if (rc.canMove(away)) rc.move(away);
            else if (rc.canMove(away.rotateLeft())) rc.move(away.rotateLeft());
            else if (rc.canMove(away.rotateRight())) rc.move(away.rotateRight());
        } else if (moveTarget != null && rc.isMovementReady()) {
            Direction bestDir = null;
            int bestScore = -9999;

            for (Direction d : directions) {
                if (!rc.canMove(d)) continue;
                MapLocation next = rc.getLocation().add(d);
                int score = 0;

                // Prefer enemy or empty paint
                MapInfo nextInfo = rc.senseMapInfo(next);
                PaintType p = nextInfo.getPaint();
                if (p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) score += 4;
                else if (p == PaintType.EMPTY) score += 2;

                // Strong push toward target (higher weight than before)
                if (next.distanceSquaredTo(moveTarget) < rc.getLocation().distanceSquaredTo(moveTarget)) {
                    score += 8;
                }

                // Mild spread-out from nearby splashers (lower weight — don't override target push)
                if (nearbySplashers >= 3) {
                    if (next.distanceSquaredTo(splasherCentroid) > rc.getLocation().distanceSquaredTo(splasherCentroid)) {
                        score += 2;
                    } else {
                        score -= 1;
                    }
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestDir = d;
                }
            }

            if (bestDir != null) {
                rc.move(bestDir);
            } else {
                bug0(rc, moveTarget);
            }
        } else if (rc.isMovementReady()) {
            MapLocation enemySide = guessEnemyLocation(rc, rc.getLocation());
            bug0(rc, enemySide);
        }

        // Clear destroyed enemy tower from active target (but memory keeps it)
        if (targetEnemyRuin != null && rc.canSenseLocation(targetEnemyRuin)) {
            RobotInfo atTarget = rc.senseRobotAtLocation(targetEnemyRuin);
            if (atTarget == null || atTarget.getTeam() == rc.getTeam()) {
                targetEnemyRuin = null;
            }
        }

        if (rc.isActionReady()) {
            MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
            if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
                rc.attack(rc.getLocation());
            }
        }

        String targetStr = (targetEnemyRuin != null) ? "tower@" + targetEnemyRuin
            : (pushTarget != null) ? "push@" + pushTarget : "none";
        rc.setIndicatorString("SPLASHER → " + targetStr
            + " (mem:" + enemyTowerMemory.size() + " known:" + knownEnemyTowers.size() + ")");
    }

    // ========== TARGET SELECTION ==========

    /**
     * Pick a push target. Priority:
     * 1. Known live enemy towers (assigned by ID so splashers fan out)
     * 2. Remembered enemy tower locations (keep pushing even after destroyed)
     * 3. Mirror our tower locations to guess enemy positions
     * 4. Push toward enemy side
     */
    static MapLocation pickPushTarget(RobotController rc) {
        // Priority 1: live enemy towers — assign by ID round-robin
        if (!knownEnemyTowers.isEmpty()) {
            int idx = (rc.getID() + splasherTargetIdx) % knownEnemyTowers.size();
            splasherTargetIdx++;
            int i = 0;
            for (MapLocation et : knownEnemyTowers) {
                if (i == idx) return et;
                i++;
            }
        }

        // Priority 2: remembered enemy tower positions — push past where they were
        if (!enemyTowerMemory.isEmpty()) {
            int idx = (rc.getID() + splasherTargetIdx) % enemyTowerMemory.size();
            splasherTargetIdx++;
            MapLocation remembered = enemyTowerMemory.get(idx);
            // Push PAST the old tower location toward enemy base
            MapLocation deeper = guessEnemyLocation(rc, rc.getLocation());
            // Weighted midpoint — 2/3 toward the deeper enemy side
            int tx = (remembered.x + deeper.x * 2) / 3;
            int ty = (remembered.y + deeper.y * 2) / 3;
            tx = Math.max(0, Math.min(rc.getMapWidth() - 1, tx));
            ty = Math.max(0, Math.min(rc.getMapHeight() - 1, ty));
            return new MapLocation(tx, ty);
        }

        // Priority 3: mirror our towers to guess enemy positions — diversify by ID
        if (!knownTowers.isEmpty()) {
            int idx = (rc.getID() + splasherTargetIdx) % knownTowers.size();
            splasherTargetIdx++;
            return guessEnemyLocation(rc, knownTowers.get(idx));
        }

        // Priority 4: push toward enemy side (not center!)
        return guessEnemyLocation(rc, rc.getLocation());
    }

    // ========== SPLASH TARGETING ==========

    static MapLocation findBestSplashTarget(RobotController rc, MapLocation enemyTowerLoc) throws GameActionException {
        MapLocation[] nearbyRuins = rc.senseNearbyRuins(-1);
        ArrayList<MapLocation> friendlyPatternRuins = new ArrayList<>();
        for (MapLocation ruin : nearbyRuins) {
            RobotInfo atRuin = rc.senseRobotAtLocation(ruin);
            if (atRuin == null || atRuin.getTeam() == rc.getTeam()) {
                friendlyPatternRuins.add(ruin);
            }
        }

        MapLocation bestTarget = null;
        int bestScore = 0;
        MapInfo[] attackableTiles = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        for (MapInfo tile : attackableTiles) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc)) continue;

            boolean hitsPattern = false;
            for (MapLocation ruin : friendlyPatternRuins) {
                if (loc.isWithinDistanceSquared(ruin, 12)) {
                    hitsPattern = true;
                    break;
                }
            }
            if (hitsPattern) continue;

            int score = 0;
            for (int dx2 = -1; dx2 <= 1; dx2++) {
                for (int dy2 = -1; dy2 <= 1; dy2++) {
                    MapLocation t = loc.translate(dx2, dy2);
                    if (!rc.canSenseLocation(t)) continue;
                    MapInfo s = rc.senseMapInfo(t);
                    if (s.isWall() || s.hasRuin()) continue;
                    PaintType p = s.getPaint();
                    if (p == PaintType.ENEMY_PRIMARY || p == PaintType.ENEMY_SECONDARY) score += 4;
                    else if (p == PaintType.EMPTY) score += 2;
                }
            }
            int[][] outerRing = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}};
            for (int[] d2 : outerRing) {
                MapLocation t = loc.translate(d2[0], d2[1]);
                if (!rc.canSenseLocation(t)) continue;
                MapInfo s = rc.senseMapInfo(t);
                if (s.isWall() || s.hasRuin()) continue;
                if (s.getPaint() == PaintType.EMPTY) score += 1;
            }
            if (enemyTowerLoc != null && loc.isWithinDistanceSquared(enemyTowerLoc, 9)) {
                score += 8;
            }
            RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(loc, 4, rc.getTeam().opponent());
            score += nearbyEnemies.length * 3;

            if (score > bestScore) {
                bestScore = score;
                bestTarget = loc;
            }
        }
        return bestScore >= 2 ? bestTarget : null;
    }
}
