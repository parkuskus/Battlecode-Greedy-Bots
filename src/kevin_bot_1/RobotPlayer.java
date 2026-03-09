package kevin_bot_1;

import battlecode.common.*;

/**
 * Entry point for bot_2 — a greedy-approach Battlecode 2025 bot.
 *
 * Every unit makes locally optimal decisions each turn:
 *   - Towers greedily pick the best unit to spawn based on game phase.
 *   - Soldiers greedily build the most complete ruin, paint the best tile, or explore.
 *   - Splashers greedily target the tile cluster that flips the most enemy paint.
 *   - Moppers greedily hunt enemy units with the lowest paint or clean enemy paint near ruins.
 */
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
                    default -> TowerBot.turn(); // all tower types
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
