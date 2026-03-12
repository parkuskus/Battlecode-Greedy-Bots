package bot_kevin_1;

import battlecode.common.*;

/*
    - Format pesan, inspired by Just Woke Up team:
    [31..29] 3-bit code
    [28..23] x coordinate (6 bits)
    [22..17] y coordinate (6 bits)
    [16..15] payload (2 bits)
    [14..0]  unused / future

    - Marking:
    0 = TOWER_BUILT  (location + tower base type)
    1 = NEED_MOPPER  (location of enemy paint)
    2 = ENEMY_TOWER  (location of enemy tower)
 */
public class Messaging {
    // Kode marking
    static final int CODE_TOWER_BUILT = 0;
    static final int CODE_NEED_MOPPER = 1;
    static final int CODE_ENEMY_TOWER = 2;

    // informasi yang dimiliki robot soal environment sekitar
    static MapLocation[] knownTowers = new MapLocation[25];
    static UnitType[]    knownTypes  = new UnitType[25];
    static int           towerCount  = 0;

    static MapLocation needMopperAt = null;
    static void init() {
        towerCount = 0;
        needMopperAt = null;
    }

    // encoding untuk format message
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

    static int encodeNeedMopper(MapLocation loc) { return encode(CODE_NEED_MOPPER, loc, 0);}

    static int encodeEnemyTower(MapLocation loc) { return encode(CODE_ENEMY_TOWER, loc, 0);}

    // decoding pesan yang diterima
    static void readMessages() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        int round = rc.getRoundNum();
        int from = Math.max(0, round - 2);
        for(Message msg : rc.readMessages(from)) {
            int raw = msg.getBytes();
            int code = (raw >>> 29) & 0x7;
            int x = (raw >>> 23) & 0x3F;
            int y = (raw >>> 17) & 0x3F;
            int pay = (raw >>> 15) & 0x3;
            MapLocation loc = new MapLocation(x, y);

            switch(code) {
                case CODE_TOWER_BUILT -> registerTower(loc, payloadToType(pay));
                case CODE_NEED_MOPPER -> needMopperAt = loc;
                case CODE_ENEMY_TOWER -> {}
            }
        }
    }

    static void registerTower(MapLocation loc, UnitType type) {
        for(int i = 0; i < towerCount; i++) {
            if(knownTowers[i].equals(loc)) {
                knownTypes[i] = type;
                return;
            }
        }
        if(towerCount < knownTowers.length) {
            knownTowers[towerCount] = loc;
            knownTypes[towerCount] = type;
            towerCount++;
        }
    }

    static void removeTower(MapLocation loc) {
        for(int i = 0; i < towerCount; i++) {
            if(knownTowers[i].equals(loc)) {
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
        for(int i = 0; i < towerCount; i++) {
            if(knownTypes[i] != null
                && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                int d = from.distanceSquaredTo(knownTowers[i]);
                if(d < bestDist) {
                    bestDist = d;
                    best = knownTowers[i];
                }
            }
        }
        return best;
    }

    static MapLocation nearestAnyTower(MapLocation from) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for(int i = 0; i < towerCount; i++) {
            int d = from.distanceSquaredTo(knownTowers[i]);
            if(d < bestDist) {
                bestDist = d;
                best = knownTowers[i];
            }
        }
        return best;
    }

    static int countPaintTowers() {
        int c = 0;
        for(int i = 0; i < towerCount; i++)
            if(knownTypes[i] != null && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER) c++;
        return c;
    }

    static int countMoneyTowers() {
        int c = 0;
        for(int i = 0; i < towerCount; i++)
            if(knownTypes[i] != null && knownTypes[i].getBaseType() == UnitType.LEVEL_ONE_MONEY_TOWER) c++;
        return c;
    }

    static UnitType payloadToType(int p) {
        return switch(p){
            case 0 -> UnitType.LEVEL_ONE_PAINT_TOWER;
            case 1 -> UnitType.LEVEL_ONE_MONEY_TOWER;
            default -> UnitType.LEVEL_ONE_DEFENSE_TOWER;
        };
    }

    // infokan tower yang diketahui ke tower terdekat
    static void relayToNearbyTower(RobotInfo[] allies) throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        for(RobotInfo ally : allies) {
            if(ally.type.isTowerType() && rc.canSendMessage(ally.location)) {
                for(int i = 0; i < towerCount; i++) {
                    if(rc.canSendMessage(ally.location)) rc.sendMessage(ally.location, encodeTowerBuilt(knownTowers[i], knownTypes[i]));
                }
                break;
            }
        }
    }
}
