package alternative_bots_1;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.Team;

public class RobotPlayer {
    static RobotController rc;
    static Team myTeam;
    static Team enemyTeam;
    static int mapW, mapH;
    static int myID;

    static final Direction[] DIRS = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    @SuppressWarnings("unused")
    public static void run(RobotController robot) throws GameActionException {
        rc = robot;
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();
        mapW = rc.getMapWidth();
        mapH = rc.getMapHeight();
        myID = rc.getID();

        Messaging.init();

        while (true) {
            try {
                switch (rc.getType()) {
                    case SOLDIER -> SoldierBot.turn();
                    case SPLASHER -> SplasherBot.turn();
                    case MOPPER -> MopperBot.turn();
                    default -> TowerBot.turn();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
