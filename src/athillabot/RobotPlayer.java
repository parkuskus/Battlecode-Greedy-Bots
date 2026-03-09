package athillabot;

import battlecode.common.*;

import java.util.Random;

/**
 * Entry point untuk bot athillabot.
 * Setiap robot memanggil run() saat spawn — method ini TIDAK BOLEH return (robot mati kalau return).
 * 
 * Arsitektur:
 * - run() loop infinite, setiap iterasi = 1 giliran
 * - Switch berdasarkan tipe robot → dispatch ke class handler masing-masing
 * - Clock.yield() di akhir setiap giliran (wajib, kalau gak dipanggil = buang bytecode)
 */
public class RobotPlayer {

    // Hitung berapa ronde robot ini sudah hidup
    static int turnCount = 0;

    // RNG dengan seed tetap — berguna untuk debugging (hasil konsisten)
    static final Random rng = new Random(6147);

    // 8 arah gerakan yang mungkin
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    /**
     * Entry point utama. Dipanggil sekali saat robot spawn.
     * JANGAN PERNAH return dari method ini — robot langsung mati.
     */
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        while (true) {
            turnCount += 1;

            try {
                // Dispatch ke handler berdasarkan tipe robot
                // Semua tower type (LEVEL_ONE_PAINT_TOWER, etc.) masuk ke default → TowerBot
                switch (rc.getType()) {
                    case SOLDIER:  SoldierBot.run(rc);  break;
                    case SPLASHER: SplasherBot.run(rc); break;
                    case MOPPER:   MopperBot.run(rc);   break;
                    default:       TowerBot.run(rc);    break;
                }

            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                // WAJIB: akhiri giliran. Tanpa ini, robot buang bytecode sia-sia.
                Clock.yield();
            }
        }
    }
}
