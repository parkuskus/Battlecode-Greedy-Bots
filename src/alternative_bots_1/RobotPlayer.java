<<<<<<< HEAD:src/alternative_bots_1/RobotPlayer.java
package alternative_bots_1;
=======
package bot_kevin_1;

>>>>>>> 994546f (fix: update package name from bot_2 to bot_kevin_1 in Messaging, RobotPlayer, and TowerBot classes):src/bot_kevin_1/RobotPlayer.java
import battlecode.common.*;

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
