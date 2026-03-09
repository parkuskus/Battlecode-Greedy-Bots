package athillabot;

import battlecode.common.*;

/**
 * Helper komunikasi antar robot.
 * 
 * Encoding pesan: 1 integer 32-bit.
 * Format: [type (4 bit)] [x (6 bit)] [y (6 bit)] [extra (16 bit)]
 * 
 * Tipe pesan:
 * - RUIN_FOUND (1): ada reruntuhan kosong di (x, y)
 * - ENEMY_TOWER (2): ada tower musuh di (x, y)
 * - NEED_PAINT (3): robot butuh cat, posisi di (x, y)
 * - ENEMY_CLUSTER (4): banyak musuh di sekitar (x, y), extra = jumlah
 */
public class Communication {

    // Message type constants
    public static final int MSG_RUIN_FOUND = 1;
    public static final int MSG_ENEMY_TOWER = 2;
    public static final int MSG_NEED_PAINT = 3;
    public static final int MSG_ENEMY_CLUSTER = 4;

    /**
     * Encode pesan jadi 1 integer.
     * type: 4 bit (0-15), x: 6 bit (0-63), y: 6 bit (0-63), extra: 16 bit
     */
    public static int encode(int type, MapLocation loc, int extra) {
        return (type << 28) | (loc.x << 22) | (loc.y << 16) | (extra & 0xFFFF);
    }

    public static int encode(int type, MapLocation loc) {
        return encode(type, loc, 0);
    }

    /** Decode tipe pesan dari integer. */
    public static int decodeType(int msg) {
        return (msg >>> 28) & 0xF;
    }

    /** Decode lokasi dari integer. */
    public static MapLocation decodeLoc(int msg) {
        int x = (msg >>> 22) & 0x3F;
        int y = (msg >>> 16) & 0x3F;
        return new MapLocation(x, y);
    }

    /** Decode extra data dari integer. */
    public static int decodeExtra(int msg) {
        return msg & 0xFFFF;
    }

    /**
     * Broadcast pesan ke semua robot sekutu terdekat.
     * Robot bisa kirim 1 pesan/ronde, tower 20 pesan/ronde.
     */
    public static void broadcastToNearbyAllies(RobotController rc, int encodedMsg) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (rc.canSendMessage(ally.location, encodedMsg)) {
                rc.sendMessage(ally.location, encodedMsg);
                return; // Robot hanya bisa kirim 1 pesan per ronde
            }
        }
    }

    /**
     * Baca semua pesan masuk dan return array pesan yang sudah di-decode.
     * Setiap elemen = int[] {type, x, y, extra}
     */
    public static void processMessages(RobotController rc, MessageHandler handler) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            int encoded = m.getBytes();
            int type = decodeType(encoded);
            MapLocation loc = decodeLoc(encoded);
            int extra = decodeExtra(encoded);
            handler.handle(type, loc, extra, m.getSenderID(), m.getRound());
        }
    }

    /** Interface untuk handle pesan yang di-decode. */
    public interface MessageHandler {
        void handle(int type, MapLocation loc, int extra, int senderID, int round) throws GameActionException;
    }
}
