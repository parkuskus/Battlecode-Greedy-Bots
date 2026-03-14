package alternative_bots_2;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/*
 Lightweight messaging with bit-packed 32-bit integers.

 Format: [31..29] code | [28..23] x | [22..17] y | [16..15] payload | [14..0] unused
 Codes: 0=TOWER_BUILT, 1=NEED_MOPPER, 2=ENEMY_TOWER
 
 Maintains a shared tower registry for all units.
 Messaging constraint: robots can only sendMessage to towers,
 towers can sendMessage to robots or broadcastMessage.
 */
public class Comms {

    static final int CODE_TOWER_BUILT = 0;
    static final int CODE_NEED_MOPPER = 1;
    static final int CODE_ENEMY_TOWER = 2;

    // Shared tower registry
    static MapLocation[] knownTowers = new MapLocation[30];
    static UnitType[]    knownTypes  = new UnitType[30];
    static int           towerCount  = 0;

    static MapLocation needMopperAt = null;

    static void init() {
        towerCount = 0;
        needMopperAt = null;
    }

    // Encoding

    static int encode(int code, MapLocation loc, int payload) {
        return ((code & 0x7) << 29)
             | ((loc.x & 0x3F) << 23)
             | ((loc.y & 0x3F) << 17)
             | ((payload & 0x3) << 15);
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

    // Decoding 

    static void readMessages() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        int round = rc.getRoundNum();
        int from = Math.max(0, round - 2);
        for (Message msg : rc.readMessages(from)) {
            int raw = msg.getBytes();
            int code = (raw >>> 29) & 0x7;
            int x    = (raw >>> 23) & 0x3F;
            int y    = (raw >>> 17) & 0x3F;
            int pay  = (raw >>> 15) & 0x3;
            MapLocation loc = new MapLocation(x, y);

            switch (code) {
                case CODE_TOWER_BUILT -> registerTower(loc, payloadToType(pay));
                case CODE_NEED_MOPPER -> needMopperAt = loc;
                case CODE_ENEMY_TOWER -> { /* reserved */ }
            }
        }
    }

    // Tower Registry 

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
                knownTypes[i]  = knownTypes[towerCount - 1];
                towerCount--;
                return;
            }
        }
    }

    static MapLocation nearestPaintTower(MapLocation from) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < towerCount; i++) {
            if (knownTypes[i] != null
                && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                int d = from.distanceSquaredTo(knownTowers[i]);
                if (d < bestDist) { bestDist = d; best = knownTowers[i]; }
            }
        }
        return best;
    }

    static MapLocation nearestAnyTower(MapLocation from) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < towerCount; i++) {
            int d = from.distanceSquaredTo(knownTowers[i]);
            if (d < bestDist) { bestDist = d; best = knownTowers[i]; }
        }
        return best;
    }

    static int countPaintTowers() {
        int c = 0;
        for (int i = 0; i < towerCount; i++)
            if (knownTypes[i] != null
                && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) c++;
        return c;
    }

    static int countMoneyTowers() {
        int c = 0;
        for (int i = 0; i < towerCount; i++)
            if (knownTypes[i] != null
                && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER) c++;
        return c;
    }

    static UnitType payloadToType(int p) {
        return switch (p) {
            case 0 -> UnitType.LEVEL_ONE_PAINT_TOWER;
            case 1 -> UnitType.LEVEL_ONE_MONEY_TOWER;
            default -> UnitType.LEVEL_ONE_DEFENSE_TOWER;
        };
    }

    /** Robot relays known tower list to a nearby ally tower. */
    static void relayToNearbyTower(RobotInfo[] allies) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        // Robots can only sendMessage to towers
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType() && rc.canSendMessage(ally.location)) {
                for (int i = 0; i < Math.min(towerCount, 4); i++) {
                    if (rc.canSendMessage(ally.location)) {
                        rc.sendMessage(ally.location,
                            encodeTowerBuilt(knownTowers[i], knownTypes[i]));
                    }
                }
                break; // only relay to one tower per turn
            }
        }
    }

    /** Tower relays known tower list to nearby non-tower units. */
    static void towerRelayToUnits(RobotInfo[] allies) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        for (RobotInfo ally : allies) {
            if (!ally.type.isTowerType() && rc.canSendMessage(ally.location)) {
                for (int i = 0; i < Math.min(towerCount, 3); i++) {
                    if (rc.canSendMessage(ally.location)) {
                        rc.sendMessage(ally.location,
                            encodeTowerBuilt(knownTowers[i], knownTypes[i]));
                    }
                }
            }
        }
    }

    /** Update tower registry from visible allies. */
    static void updateFromVisible(RobotInfo[] allies) {
        for (RobotInfo a : allies)
            if (a.type.isTowerType())
                registerTower(a.location, a.type);
    }
}
