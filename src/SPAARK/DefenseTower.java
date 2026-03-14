package SPAARK;

public class DefenseTower {

    public static int lastTurnThatWeSawAnEnemyRobot = -1;

    public static void init() throws Exception {
        lastTurnThatWeSawAnEnemyRobot = G.rc.getRoundNum();
    }

    public static void run() throws Exception {
        if (G.opponentRobotsString.length() > 0) {
            lastTurnThatWeSawAnEnemyRobot = G.rc.getRoundNum();
        }
        for (int i = G.nearbyMapInfos.length; --i >= 0;) {
            if (G.nearbyMapInfos[i].getPaint().isEnemy()) {
                lastTurnThatWeSawAnEnemyRobot = G.rc.getRoundNum();
                break;
            }
        }
        if (G.rc.getRoundNum() - lastTurnThatWeSawAnEnemyRobot > 30) {
            // not useful anymore probably
            G.rc.disintegrate();
        }
    }
}
