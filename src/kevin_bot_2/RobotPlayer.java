<<<<<<<< HEAD:src/kevin_bot_2/RobotPlayer.java
package kevin_bot_2;
========
package bot_kevin_1;

>>>>>>>> 994546f2518a7cb1e4ad052c155d5e72845ed538:src/bot_kevin_1/RobotPlayer.java
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
