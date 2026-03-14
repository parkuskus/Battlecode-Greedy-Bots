package himothee;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Shared state, constants, enums, message encoding, pathfinding, symmetry detection,
 * and utility methods used across all robot types.
 */
public class Shared {

    // ========== ENUMS ==========

    enum MessageType {
        SAVE_CHIPS,
        SYM_UPDATE,
        ENEMY_TOWER
    }

    enum RobotState {
        STARTING,
        PAINTING_PATTERN,
        EXPLORING,
        ATTACKING,
        RETREAT,
        DEFENDING,
        HELPING_BUILD
    }

    // ========== CONSTANTS ==========

    static final int SYM_MSG_OFFSET = 8;
    static final int SYM_H_BIT = 1 << SYM_MSG_OFFSET;
    static final int SYM_V_BIT = 1 << (SYM_MSG_OFFSET + 1);
    static final int SYM_R_BIT = 1 << (SYM_MSG_OFFSET + 2);

    static final int LOW_PAINT_THRESHOLD = 50;
    static final int MOPPER_LOW_PAINT_THRESHOLD = 15;

    static final Random rng = new Random(6147);

    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    // SRP (Special Resource Pattern) state
    static final int SRP_TOWER_MIN = 3;
    static final int[][] SRP_PATTERN = {
        {2,2,1,2,2},{2,1,1,1,2},{1,1,2,1,1},{2,1,1,1,2},{2,2,1,2,2}
    };

    // ========== SHARED MUTABLE STATE ==========

    static int turnCount = 0;
    static boolean isMessenger = false;
    static boolean isSaving = false;
    static int savingTurns = 0;        // safety-cap countdown (max turns to save)
    static int saveChipTarget = 0;     // stop saving once chips >= this
    static MapLocation savingForRuinLoc = null; // ruin location we're saving chips for
    static final int TOWER_CHIP_COST = 1000; // chip cost to complete one tower
    static ArrayList<MapLocation> knownTowers = new ArrayList<>();
    static HashSet<MapLocation> knownMoneyTowers = new HashSet<>();

    static RobotState state = RobotState.STARTING;
    static RobotState preRetreatState = null;
    static MapLocation targetEnemyRuin = null;

    static ArrayList<MapLocation> knownEnemyTowers = new ArrayList<>();

    static Direction exploreDir = null;
    static MapLocation exploreLoc = null;
    static int exploreSetRound = 0;

    // Symmetry detection
    static boolean symHorizontal = true;
    static boolean symVertical = true;
    static boolean symRotational = true;
    static ArrayList<MapLocation> knownRuins = new ArrayList<>();

    // Game stage thresholds — scaled by map size in run()
    static int EARLY_GAME_TURNS = 50;
    static int SPLASHER_UNLOCK_TURNS = 100;
    static int LATEGAME_OFFENSIVE_TURNS = 200;
    static double mapScale = 1.0;

    static boolean[][] paintTowerPattern = null;
    static boolean[][] moneyTowerPattern = null;

    static int spawnCount = 0;

    // Pathfinding variables
    static boolean isTracing = false;
    static int smallestDistance = 1000000;
    static MapLocation closestLocation = null;
    static Direction tracingDir = null;
    static MapLocation prevDest = null;
    static HashSet<MapLocation> line = null;
    static int obstacleStartDist = 0;

    // ========== MESSAGE ENCODING/DECODING ==========

    static int encodeSymMessage() {
        int msg = MessageType.SYM_UPDATE.ordinal();
        if (!symHorizontal) msg |= SYM_H_BIT;
        if (!symVertical) msg |= SYM_V_BIT;
        if (!symRotational) msg |= SYM_R_BIT;
        return msg;
    }

    static void decodeSymMessage(int msg) {
        if ((msg & SYM_H_BIT) != 0) symHorizontal = false;
        if ((msg & SYM_V_BIT) != 0) symVertical = false;
        if ((msg & SYM_R_BIT) != 0) symRotational = false;
    }

    static boolean isSymMessage(int msg) {
        return (msg & 0xFF) == MessageType.SYM_UPDATE.ordinal();
    }

    static int encodeSaveChipsMessage(MapLocation loc) {
        int msg = MessageType.SAVE_CHIPS.ordinal();
        if (loc != null) {
            msg |= (loc.x & 0x3F) << 8;
            msg |= (loc.y & 0x3F) << 14;
        }
        return msg;
    }

    static MapLocation decodeSaveChipsMessage(int msg) {
        int x = (msg >> 8) & 0x3F;
        int y = (msg >> 14) & 0x3F;
        if (x == 0 && y == 0) return null;
        return new MapLocation(x, y);
    }

    static boolean isSaveChipsMessage(int msg) {
        return (msg & 0xFF) == MessageType.SAVE_CHIPS.ordinal();
    }

    static int encodeEnemyTowerMessage(MapLocation loc) {
        int msg = MessageType.ENEMY_TOWER.ordinal();
        msg |= (loc.x & 0x3F) << 8;
        msg |= (loc.y & 0x3F) << 14;
        return msg;
    }

    static MapLocation decodeEnemyTowerMessage(int msg) {
        int x = (msg >> 8) & 0x3F;
        int y = (msg >> 14) & 0x3F;
        return new MapLocation(x, y);
    }

    static boolean isEnemyTowerMessage(int msg) {
        return (msg & 0xFF) == MessageType.ENEMY_TOWER.ordinal();
    }

    // ========== UTILITY METHODS ==========

    static boolean isMoneyTower(UnitType t) {
        return t == UnitType.LEVEL_ONE_MONEY_TOWER
            || t == UnitType.LEVEL_TWO_MONEY_TOWER
            || t == UnitType.LEVEL_THREE_MONEY_TOWER;
    }

    static boolean isWithinPattern(MapLocation ruinLoc, MapLocation paintLoc) {
        return Math.abs(paintLoc.x - ruinLoc.x) <= 2 && Math.abs(paintLoc.y - ruinLoc.y) <= 2 && !ruinLoc.equals(paintLoc);
    }

    static boolean getIsSecondary(MapLocation ruinLoc, MapLocation paintLoc, UnitType towerType) {
        if (!isWithinPattern(ruinLoc, paintLoc)) return false;
        int col = paintLoc.x - ruinLoc.x + 2;
        int row = paintLoc.y - ruinLoc.y + 2;
        return towerType == UnitType.LEVEL_ONE_PAINT_TOWER ? paintTowerPattern[row][col] : moneyTowerPattern[row][col];
    }

    static MapLocation nearestFriendlyTower(RobotController rc) {
        MapLocation nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapLocation tower : knownTowers) {
            int dist = rc.getLocation().distanceSquaredTo(tower);
            if (dist < bestDist) {
                bestDist = dist;
                nearest = tower;
            }
        }
        return nearest;
    }

    // ========== COMMON ROBOT ACTIONS ==========

    public static void updateFriendlyTowers(RobotController rc) throws GameActionException {
        RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allyRobots) {
            if (!ally.getType().isTowerType()) continue;

            MapLocation allyLoc = ally.location;

            if (isMoneyTower(ally.getType())) {
                knownMoneyTowers.add(allyLoc);
            } else {
                knownMoneyTowers.remove(allyLoc);
            }

            // Try to deliver save message to any visible tower
            if (isSaving && rc.canSendMessage(allyLoc)) {
                rc.sendMessage(allyLoc, encodeSaveChipsMessage(savingForRuinLoc));
                isSaving = false;
            }

            if (knownTowers.contains(allyLoc)) {
                continue;
            }

            knownTowers.add(allyLoc);
        }
    }

    public static void checkNearbyRuins(RobotController rc) throws GameActionException {
        // Only used for general ruin awareness now — saving is handled by PAINTING_PATTERN state
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException {
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0) {
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++) {
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        }
    }

    // ========== RETREAT ==========

    public static void runRetreat(RobotController rc) throws GameActionException {
        rc.setIndicatorString("RETREATING - paint: " + rc.getPaint());

        updateFriendlyTowers(rc);

        knownTowers.removeIf(tower -> {
            try {
                if (rc.canSenseLocation(tower)) {
                    RobotInfo r = rc.senseRobotAtLocation(tower);
                    return r == null || !r.getType().isTowerType() || r.getTeam() != rc.getTeam();
                }
            } catch (GameActionException e) { /* ignore */ }
            return false;
        });

        MapLocation bestTower = null;
        int bestDist = Integer.MAX_VALUE;
        RobotInfo[] visibleAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : visibleAllies) {
            if (ally.getType().isTowerType() && ally.getPaintAmount() > 5) {
                if (isMoneyTower(ally.getType())) continue;
                int dist = rc.getLocation().distanceSquaredTo(ally.getLocation());
                if (dist < bestDist) {
                    bestDist = dist;
                    bestTower = ally.getLocation();
                }
            }
        }

        if (bestTower == null) {
            for (MapLocation tower : knownTowers) {
                if (knownMoneyTowers.contains(tower)) continue;
                int dist = rc.getLocation().distanceSquaredTo(tower);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestTower = tower;
                }
            }
        }

        if (bestTower == null) {
            state = preRetreatState != null ? preRetreatState : RobotState.EXPLORING;
            preRetreatState = null;
            return;
        }

        if (rc.getLocation().isAdjacentTo(bestTower) || rc.getLocation().equals(bestTower)) {
            boolean refilled = false;
            if (rc.canSenseLocation(bestTower)) {
                RobotInfo towerBot = rc.senseRobotAtLocation(bestTower);
                if (towerBot != null && towerBot.getType().isTowerType() && towerBot.getTeam() == rc.getTeam()) {
                    if (isMoneyTower(towerBot.getType())) {
                        knownMoneyTowers.add(bestTower);
                        Direction away = rc.getLocation().directionTo(bestTower).opposite();
                        if (rc.canMove(away)) rc.move(away);
                        return;
                    }
                    int space = rc.getType().paintCapacity - rc.getPaint();
                    int avail = towerBot.getPaintAmount();
                    int amt = Math.min(space, avail);
                    if (amt > 0 && rc.canTransferPaint(bestTower, -amt)) {
                        rc.transferPaint(bestTower, -amt);
                        refilled = true;
                    }
                } else {
                    knownTowers.remove(bestTower);
                    return;
                }
            }
            int exitThreshold = (rc.getType() == UnitType.MOPPER) ? MOPPER_LOW_PAINT_THRESHOLD
                : (rc.getType() == UnitType.SPLASHER) ? (int)(rc.getType().paintCapacity * 0.3)
                : LOW_PAINT_THRESHOLD;
            if (rc.getPaint() > exitThreshold) {
                state = preRetreatState != null ? preRetreatState : RobotState.EXPLORING;
                preRetreatState = null;
            } else if (!refilled) {
                knownTowers.remove(bestTower);
            }
        } else {
            bug0(rc, bestTower);
        }
    }

    // ========== SYMMETRY DETECTION ==========

    public static MapLocation mirrorHorizontal(RobotController rc, MapLocation loc) {
        return new MapLocation(loc.x, rc.getMapHeight() - 1 - loc.y);
    }

    public static MapLocation mirrorVertical(RobotController rc, MapLocation loc) {
        return new MapLocation(rc.getMapWidth() - 1 - loc.x, loc.y);
    }

    public static MapLocation mirrorRotational(RobotController rc, MapLocation loc) {
        return new MapLocation(rc.getMapWidth() - 1 - loc.x, rc.getMapHeight() - 1 - loc.y);
    }

    public static void readSymmetryMessages(RobotController rc) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            if (isSymMessage(m.getBytes())) {
                decodeSymMessage(m.getBytes());
            } else if (isEnemyTowerMessage(m.getBytes())) {
                MapLocation loc = decodeEnemyTowerMessage(m.getBytes());
                if (!knownEnemyTowers.contains(loc)) {
                    knownEnemyTowers.add(loc);
                }
            }
        }
    }

    public static void sendSymmetryToTowers(RobotController rc) throws GameActionException {
        if (symHorizontal && symVertical && symRotational) return;
        for (MapLocation tower : knownTowers) {
            if (rc.canSendMessage(tower)) {
                rc.sendMessage(tower, encodeSymMessage());
                break;
            }
        }
    }

    public static void reportEnemyTowers(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (!enemy.getType().isTowerType()) continue;
            MapLocation loc = enemy.getLocation();
            if (knownEnemyTowers.contains(loc)) continue;
            knownEnemyTowers.add(loc);
            for (MapLocation tower : knownTowers) {
                if (rc.canSendMessage(tower)) {
                    rc.sendMessage(tower, encodeEnemyTowerMessage(loc));
                    break;
                }
            }
        }
        knownEnemyTowers.removeIf(loc -> {
            try {
                if (rc.canSenseLocation(loc)) {
                    RobotInfo r = rc.senseRobotAtLocation(loc);
                    return r == null || r.getTeam() == rc.getTeam();
                }
            } catch (GameActionException e) { /* ignore */ }
            return false;
        });
    }

    public static MapLocation extendToEdge(RobotController rc, MapLocation from, Direction dir) {
        MapLocation loc = from;
        for (int i = 0; i < 60; i++) {
            MapLocation next = loc.add(dir);
            if (next.x < 0 || next.y < 0
                || next.x >= rc.getMapWidth() || next.y >= rc.getMapHeight()) break;
            loc = next;
        }
        return loc;
    }

    /** Check if location is near a map edge (within 3 tiles). */
    static boolean isNearEdge(RobotController rc, MapLocation loc) {
        return loc.x <= 3 || loc.y <= 3
            || loc.x >= rc.getMapWidth() - 4 || loc.y >= rc.getMapHeight() - 4;
    }

    /** Pick an explore direction biased inward when near edges. */
    static Direction pickExploreDir(RobotController rc, MapLocation loc) {
        int cx = rc.getMapWidth() / 2;
        int cy = rc.getMapHeight() / 2;
        MapLocation center = new MapLocation(cx, cy);
        Direction toCenter = loc.directionTo(center);
        if (toCenter == Direction.CENTER) {
            return directions[rng.nextInt(directions.length)];
        }
        // Randomize slightly: center, or +-1 rotation
        int r = rng.nextInt(3);
        if (r == 0) return toCenter.rotateLeft();
        if (r == 1) return toCenter.rotateRight();
        return toCenter;
    }

    public static void initExploreDir(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation spawnTower = me;
        int best = Integer.MAX_VALUE;
        RobotInfo[] nearby = rc.senseNearbyRobots(4, rc.getTeam());
        for (RobotInfo r : nearby) {
            if (r.getType().isTowerType()) {
                int d = me.distanceSquaredTo(r.getLocation());
                if (d < best) { best = d; spawnTower = r.getLocation(); }
            }
        }
        exploreDir = spawnTower.directionTo(me);
        if (exploreDir == Direction.CENTER) {
            exploreDir = directions[rc.getID() % 8];
        }
        exploreLoc = extendToEdge(rc, me, exploreDir);
        exploreSetRound = rc.getRoundNum();
    }

    public static void updateSymmetryGuess(RobotController rc) throws GameActionException {
        readSymmetryMessages(rc);

        boolean oldH = symHorizontal, oldV = symVertical, oldR = symRotational;

        MapLocation[] nearbyRuins = rc.senseNearbyRuins(-1);
        for (MapLocation ruin : nearbyRuins) {
            if (!knownRuins.contains(ruin)) {
                knownRuins.add(ruin);
            }
        }

        ArrayList<MapLocation> landmarks = new ArrayList<>();
        landmarks.addAll(knownRuins);
        landmarks.addAll(knownTowers);

        for (MapLocation loc : landmarks) {
            if (symHorizontal) {
                MapLocation mirrored = mirrorHorizontal(rc, loc);
                if (rc.canSenseLocation(mirrored)) {
                    MapInfo info = rc.senseMapInfo(mirrored);
                    if (!info.hasRuin() && rc.senseRobotAtLocation(mirrored) == null) {
                        symHorizontal = false;
                    } else if (rc.senseRobotAtLocation(mirrored) != null && rc.senseRobotAtLocation(mirrored).getTeam() == rc.getTeam()) {
                        if (!mirrored.equals(loc)) symHorizontal = false;
                    }
                }
            }
            if (symVertical) {
                MapLocation mirrored = mirrorVertical(rc, loc);
                if (rc.canSenseLocation(mirrored)) {
                    MapInfo info = rc.senseMapInfo(mirrored);
                    if (!info.hasRuin() && rc.senseRobotAtLocation(mirrored) == null) {
                        symVertical = false;
                    } else if (rc.senseRobotAtLocation(mirrored) != null && rc.senseRobotAtLocation(mirrored).getTeam() == rc.getTeam()) {
                        if (!mirrored.equals(loc)) symVertical = false;
                    }
                }
            }
            if (symRotational) {
                MapLocation mirrored = mirrorRotational(rc, loc);
                if (rc.canSenseLocation(mirrored)) {
                    MapInfo info = rc.senseMapInfo(mirrored);
                    if (!info.hasRuin() && rc.senseRobotAtLocation(mirrored) == null) {
                        symRotational = false;
                    } else if (rc.senseRobotAtLocation(mirrored) != null && rc.senseRobotAtLocation(mirrored).getTeam() == rc.getTeam()) {
                        if (!mirrored.equals(loc)) symRotational = false;
                    }
                }
            }
        }

        if (oldH != symHorizontal || oldV != symVertical || oldR != symRotational) {
            sendSymmetryToTowers(rc);
        }
    }

    public static MapLocation guessEnemyLocation(RobotController rc, MapLocation friendlyLoc) {
        if (symRotational) return mirrorRotational(rc, friendlyLoc);
        else if (symVertical) return mirrorVertical(rc, friendlyLoc);
        else if (symHorizontal) return mirrorHorizontal(rc, friendlyLoc);
        return mirrorRotational(rc, friendlyLoc);
    }

    // ========== PATHFINDING ==========

    static int moveTileScore(RobotController rc, MapLocation loc) throws GameActionException {
        if (!rc.onTheMap(loc)) return -9999;
        MapInfo info = rc.senseMapInfo(loc);
        if (info.isWall()) return -9999;
        PaintType p = info.getPaint();
        int score;
        if (p == PaintType.EMPTY) score = 5;
        else if (p.isAlly()) score = 2;
        else score = -6;
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(loc, 2, rc.getTeam());
        for (RobotInfo ally : nearbyAllies) {
            if (!ally.getType().isTowerType()) score -= 2;
        }
        return score;
    }

    public static void bug0(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction dir = rc.getLocation().directionTo(target);
        Direction[] candidates = {dir, dir.rotateLeft(), dir.rotateRight(),
            dir.rotateLeft().rotateLeft(), dir.rotateRight().rotateRight()};
        Direction best = null;
        int bestScore = -9999;
        for (Direction d : candidates) {
            if (rc.canMove(d)) {
                int score = moveTileScore(rc, rc.getLocation().add(d));
                if (d == dir) score += 3;
                else if (d == dir.rotateLeft() || d == dir.rotateRight()) score += 1;
                if (score > bestScore) {
                    bestScore = score;
                    best = d;
                }
            }
        }
        if (best != null && bestScore > -6) {
            rc.move(best);
        } else {
            for (Direction d : candidates) {
                if (rc.canMove(d)) { rc.move(d); return; }
            }
        }
    }

    public static void bug2(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;
        if (!target.equals(prevDest)) {
            prevDest = target;
            line = createLine(target, rc.getLocation());
        }

        if (!isTracing) {
            Direction dir = rc.getLocation().directionTo(target);
            Direction[] tries = {dir, dir.rotateLeft(), dir.rotateRight()};
            Direction best = null;
            int bestScore = -9999;
            for (Direction d : tries) {
                if (rc.canMove(d)) {
                    int score = moveTileScore(rc, rc.getLocation().add(d));
                    if (d == dir) score += 3;
                    if (score > bestScore) { bestScore = score; best = d; }
                }
            }
            if (best != null && bestScore > -6) {
                rc.move(best);
            } else if (rc.canMove(dir)) {
                rc.move(dir);
            } else {
                isTracing = true;
                obstacleStartDist = rc.getLocation().distanceSquaredTo(target);
                tracingDir = dir;
            }
        } else {
            if (line.contains(rc.getLocation()) && rc.getLocation().distanceSquaredTo(target) < obstacleStartDist) {
                isTracing = false;
            }

            if (rc.canMove(tracingDir)) {
                rc.move(tracingDir);
                tracingDir = tracingDir.rotateLeft();
                tracingDir = tracingDir.rotateRight();
            } else {
                for (int i = 0; i < 8; i++) {
                    tracingDir = tracingDir.rotateLeft();
                    if (rc.canMove(tracingDir)) {
                        rc.move(tracingDir);
                        tracingDir = tracingDir.rotateRight();
                        tracingDir = tracingDir.rotateRight();
                        break;
                    }
                }
            }
        }
    }

    public static HashSet<MapLocation> createLine(MapLocation a, MapLocation b) {
        HashSet<MapLocation> locs = new HashSet<>();
        int x = a.x, y = a.y;
        int dx = b.x - a.x;
        int dy = b.y - a.y;
        int sx = (int) Math.signum(dx);
        int sy = (int) Math.signum(dy);
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        int d = Math.max(dx, dy);
        int r = d / 2;
        if (dx > dy) {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                x += sx;
                r += dy;
                if (r >= dx) {
                    locs.add(new MapLocation(x, y));
                    y += sy;
                    r -= dx;
                }
            }
        } else {
            for (int i = 0; i < d; i++) {
                locs.add(new MapLocation(x, y));
                y += sy;
                r += dx;
                if (r >= dy) {
                    locs.add(new MapLocation(x, y));
                    x += sx;
                    r -= dy;
                }
            }
        }
        locs.add(new MapLocation(x, y));
        return locs;
    }
}
