package athillabot2;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Communication {
    public static final int MSG_RUIN_CLAIM = 1;
    public static final int MSG_ENEMY_TOWER_SEEN = 2;
    public static final int MSG_URGENT_MOPPER = 3;

    public static int encode(int type, MapLocation loc, int extra) {
        return (type << 28) | (loc.x << 22) | (loc.y << 16) | (extra & 0xFFFF);
    }

    public static int encode(int type, MapLocation loc) {
        return encode(type, loc, 0);
    }

    public static int decodeType(int msg) {
        return (msg >>> 28) & 0xF;
    }

    public static MapLocation decodeLoc(int msg) {
        int x = (msg >>> 22) & 0x3F;
        int y = (msg >>> 16) & 0x3F;
        return new MapLocation(x, y);
    }

    public static int decodeExtra(int msg) {
        return msg & 0xFFFF;
    }

    /**
     * Sends one message to the nearest reachable ally. Kept intentionally minimal
     * to save bytecode and align with limited message utility this season.
     */
    public static boolean sendHighValueMessage(RobotController rc, int encodedMsg) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation myLoc = rc.getLocation();

        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            MapLocation allyLoc = ally.getLocation();
            if (!rc.canSendMessage(allyLoc, encodedMsg)) {
                continue;
            }
            int dist = myLoc.distanceSquaredTo(allyLoc);
            if (dist < bestDist) {
                bestDist = dist;
                best = ally;
            }
        }

        if (best != null) {
            rc.sendMessage(best.getLocation(), encodedMsg);
            return true;
        }
        return false;
    }

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

    public interface MessageHandler {
        void handle(int type, MapLocation loc, int extra, int senderID, int round) throws GameActionException;
    }
}
