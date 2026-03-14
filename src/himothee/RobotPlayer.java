package himothee;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.UnitType;
import static himothee.Shared.EARLY_GAME_TURNS;
import static himothee.Shared.LATEGAME_OFFENSIVE_TURNS;
import static himothee.Shared.SPLASHER_UNLOCK_TURNS;
import static himothee.Shared.mapScale;
import static himothee.Shared.moneyTowerPattern;
import static himothee.Shared.paintTowerPattern;
import static himothee.Shared.turnCount;

/**
 * RobotPlayer is the entry point for all robots.
 * Delegates to TowerPlayer, SoldierPlayer, MopperPlayer, or SplasherPlayer.
 */
public class RobotPlayer {

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        System.out.println("I'm alive");
        rc.setIndicatorString("Hello world!");

        paintTowerPattern = rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
        moneyTowerPattern = rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER);

        int mapArea = rc.getMapWidth() * rc.getMapHeight();
        mapScale = Math.max(1.0, Math.sqrt((double) mapArea / 900.0));
        EARLY_GAME_TURNS = (int)(50 * mapScale);
        SPLASHER_UNLOCK_TURNS = (int)(100 * mapScale);
        LATEGAME_OFFENSIVE_TURNS = (int)(200 * mapScale);

        while (true) {
            turnCount += 1;

            try {
                switch (rc.getType()) {
                    case SOLDIER:  SoldierPlayer.runSoldier(rc);   break;
                    case MOPPER:   MopperPlayer.runMopper(rc);     break;
                    case SPLASHER: SplasherPlayer.runSplasher(rc); break;
                    default:       TowerPlayer.runTower(rc);       break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}