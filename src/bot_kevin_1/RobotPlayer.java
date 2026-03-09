package bot_2;

import battlecode.common.*;

/*
Tiap unit memutuskan action dan movesnya sesuai dengan jenisnya, tetapi semuanya menggunakan pendekatan greedy:
 - Towers mengambil unit terbaik yang diambil berdasarkan rounds.
 - Soldiers membangun ruins yang hampir complete, mewarnai tile, atau explore.
 - Splashers menarget daerah yang memiliki paling banyak warna musuh.
 - Moppers menarget musuh dengan banyak cat paling rendah atau membersihkan warna musuh yang dekat dengan reruntuhan yang ia ketahui
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
                    //case SOLDIER -> SoldierBot.turn();
                    //case SPLASHER -> SplasherBot.turn();
                    //case MOPPER -> MopperBot.turn();
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
