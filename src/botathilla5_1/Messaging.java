package botathilla5_1;

import battlecode.common.*;

/**
 * Bit-packed 32-bit messaging.
 * [31..29] code | [28..23] x | [22..17] y | [16..15] payload | [14..0] unused
 */
public class Messaging {

    static final int CODE_TOWER_BUILT = 0;
    static final int CODE_NEED_MOPPER = 1;
    static final int CODE_ENEMY_TOWER = 2;
    static final int CODE_CONTESTED_RUIN = 3;
    static final int CODE_ENEMY_BLOB = 4;
    static final int CODE_RETREAT_BEACON = 5;

    static MapLocation[] knownTowers = new MapLocation[25];
    static UnitType[] knownTypes = new UnitType[25];
    static int towerCount = 0;

    static MapLocation needMopperAt = null;
    static MapLocation contestedRuinAt = null;
    static int contestedRuinSeverity = 0;
    static int contestedRuinRound = -999;

    static MapLocation enemyBlobAt = null;
    static int enemyBlobSeverity = 0;
    static int enemyBlobRound = -999;

    static MapLocation retreatBeaconAt = null;
    static int retreatBeaconSeverity = 0;
    static int retreatBeaconRound = -999;

    private static int messageBudgetRound = -1;
    private static int messageSentThisRound = 0;

    static void init() {
        towerCount = 0;
        needMopperAt = null;
        contestedRuinAt = null;
        contestedRuinSeverity = 0;
        contestedRuinRound = -999;
        enemyBlobAt = null;
        enemyBlobSeverity = 0;
        enemyBlobRound = -999;
        retreatBeaconAt = null;
        retreatBeaconSeverity = 0;
        retreatBeaconRound = -999;
        messageBudgetRound = -1;
        messageSentThisRound = 0;
    }

    static int encode(int code, MapLocation loc, int payload) {
        return ((code & 0x7) << 29) | ((loc.x & 0x3F) << 23)
                | ((loc.y & 0x3F) << 17) | ((payload & 0x3) << 15);
    }

    static int encodeTowerBuilt(MapLocation loc, UnitType type) {
        int t = type.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER ? 0
                : type.getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER ? 1 : 2;
        return encode(CODE_TOWER_BUILT, loc, t);
    }

    static int encodeNeedMopper(MapLocation loc) {
        return encode(CODE_NEED_MOPPER, loc, 0);
    }

    static int encodeEnemyTower(MapLocation loc) {
        return encode(CODE_ENEMY_TOWER, loc, 0);
    }

    static int encodeContestedRuin(MapLocation loc, int severity) {
        return encode(CODE_CONTESTED_RUIN, loc, severity);
    }

    static int encodeEnemyBlob(MapLocation loc, int severity) {
        return encode(CODE_ENEMY_BLOB, loc, severity);
    }

    static int encodeRetreatBeacon(MapLocation loc, int severity) {
        return encode(CODE_RETREAT_BEACON, loc, severity);
    }

    static void readMessages() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        int round = rc.getRoundNum();
        int from = Math.max(0, round - 4);
        for (Message msg : rc.readMessages(from)) {
            int raw = msg.getBytes();
            int code = (raw >>> 29) & 0x7;
            int x = (raw >>> 23) & 0x3F;
            int y = (raw >>> 17) & 0x3F;
            int pay = (raw >>> 15) & 0x3;
            MapLocation loc = new MapLocation(x, y);
            switch (code) {
                case CODE_TOWER_BUILT -> registerTower(loc, payloadToType(pay));
                case CODE_NEED_MOPPER -> needMopperAt = loc;
                case CODE_ENEMY_TOWER -> {
                }
                case CODE_CONTESTED_RUIN -> {
                    contestedRuinAt = loc;
                    contestedRuinSeverity = pay;
                    contestedRuinRound = round;
                }
                case CODE_ENEMY_BLOB -> {
                    enemyBlobAt = loc;
                    enemyBlobSeverity = pay;
                    enemyBlobRound = round;
                }
                case CODE_RETREAT_BEACON -> {
                    retreatBeaconAt = loc;
                    retreatBeaconSeverity = pay;
                    retreatBeaconRound = round;
                }
                default -> {
                }
            }
        }
        clearStaleSignals(round);
    }

    static void registerTower(MapLocation loc, UnitType type) {
        for (int i = 0; i < towerCount; i++) {
            if (knownTowers[i].equals(loc)) {
                knownTypes[i] = type;
                return;
            }
        }
        if (towerCount < knownTowers.length) {
            knownTowers[towerCount] = loc;
            knownTypes[towerCount] = type;
            towerCount++;
        }
    }

    static void removeTower(MapLocation loc) {
        for (int i = 0; i < towerCount; i++) {
            if (knownTowers[i].equals(loc)) {
                knownTowers[i] = knownTowers[towerCount - 1];
                knownTypes[i] = knownTypes[towerCount - 1];
                towerCount--;
                return;
            }
        }
    }

    /** Prefer paint tower, but accept any tower for refill. */
    static MapLocation nearestRefillTower(MapLocation from) {
        MapLocation bestPaint = null;
        int bestPaintDist = Integer.MAX_VALUE;
        MapLocation bestAny = null;
        int bestAnyDist = Integer.MAX_VALUE;
        for (int i = 0; i < towerCount; i++) {
            int d = from.distanceSquaredTo(knownTowers[i]);
            if (knownTypes[i] != null && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                if (d < bestPaintDist) {
                    bestPaintDist = d;
                    bestPaint = knownTowers[i];
                }
            }
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = knownTowers[i];
            }
        }
        return bestPaint != null ? bestPaint : bestAny;
    }

    static MapLocation nearestPaintTower(MapLocation from) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (int i = 0; i < towerCount; i++) {
            if (knownTypes[i] != null && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                int d = from.distanceSquaredTo(knownTowers[i]);
                if (d < bd) {
                    bd = d;
                    best = knownTowers[i];
                }
            }
        }
        return best;
    }

    static MapLocation nearestAnyTower(MapLocation from) {
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (int i = 0; i < towerCount; i++) {
            int d = from.distanceSquaredTo(knownTowers[i]);
            if (d < bd) {
                bd = d;
                best = knownTowers[i];
            }
        }
        return best;
    }

    static int countPaintTowers() {
        int c = 0;
        for (int i = 0; i < towerCount; i++) {
            if (knownTypes[i] != null && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) c++;
        }
        return c;
    }

    static int countMoneyTowers() {
        int c = 0;
        for (int i = 0; i < towerCount; i++) {
            if (knownTypes[i] != null && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER) c++;
        }
        return c;
    }

    static int countDefenseTowers() {
        int c = 0;
        for (int i = 0; i < towerCount; i++) {
            if (knownTypes[i] != null && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_DEFENSE_TOWER) c++;
        }
        return c;
    }

    static UnitType payloadToType(int p) {
        return switch (p) {
            case 0 -> UnitType.LEVEL_ONE_PAINT_TOWER;
            case 1 -> UnitType.LEVEL_ONE_MONEY_TOWER;
            default -> UnitType.LEVEL_ONE_DEFENSE_TOWER;
        };
    }

    /**
     * Relay tower info to a nearby tower. Budget is automatically enforced:
     * robot 1 message/round, tower 20 messages/round.
     */
    static void relayToNearbyTower(RobotInfo[] allies) throws GameActionException {
        for (RobotInfo ally : allies) {
            if (!ally.type.isTowerType()) continue;
            int sent = 0;
            for (int i = 0; i < towerCount && sent < 3; i++) {
                if (trySendMessage(ally.location, encodeTowerBuilt(knownTowers[i], knownTypes[i]))) sent++;
                else break;
            }
            break;
        }
    }

    static boolean trySendMessage(MapLocation target, int msgBytes) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        resetMessageBudgetIfNeeded();
        int budget = rc.getType().isTowerType() ? 20 : 1;
        if (messageSentThisRound >= budget) return false;
        if (!rc.canSendMessage(target)) return false;
        rc.sendMessage(target, msgBytes);
        messageSentThisRound++;
        return true;
    }

    static boolean tryBroadcastMessage(int msgBytes) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        if (!rc.getType().isTowerType()) return false;
        resetMessageBudgetIfNeeded();
        if (messageSentThisRound >= 20) return false;
        if (!rc.canBroadcastMessage()) return false;
        rc.broadcastMessage(msgBytes);
        messageSentThisRound++;
        return true;
    }

    private static void resetMessageBudgetIfNeeded() {
        int round = RobotPlayer.rc.getRoundNum();
        if (messageBudgetRound != round) {
            messageBudgetRound = round;
            messageSentThisRound = 0;
        }
    }

    private static void clearStaleSignals(int round) {
        if (contestedRuinRound + 6 < round) {
            contestedRuinAt = null;
            contestedRuinSeverity = 0;
        }
        if (enemyBlobRound + 6 < round) {
            enemyBlobAt = null;
            enemyBlobSeverity = 0;
        }
        if (retreatBeaconRound + 6 < round) {
            retreatBeaconAt = null;
            retreatBeaconSeverity = 0;
        }
    }
}


