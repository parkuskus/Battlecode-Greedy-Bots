package botathilla5_1;

import battlecode.common.*;

public final class GreedyCore {

    static final boolean TRACE = Boolean.parseBoolean(System.getProperty("bc.testing.bt5trace", "false"));

    private GreedyCore() {}

    static double score(double paintGain, double enemyPaintRemoved, double objective, double tempo,
                        double support, double risk, double paintCost) {
        return 2.6 * paintGain
                + 2.2 * enemyPaintRemoved
                + 3.1 * objective
                + 1.8 * tempo
                + 1.1 * support
                - 2.8 * risk
                - 1.0 * paintCost;
    }

    static int pickBest(double[] score, boolean[] feasible, int[] dist, int[] tie,
                        int[] rejectedOut, double[] secondOut) {
        int best = -1;
        double bestScore = -1e18;
        double second = -1e18;
        int rejected = 0;
        for (int i = 0; i < score.length; i++) {
            if (!feasible[i]) {
                rejected++;
                continue;
            }
            double s = score[i];
            if (best < 0 || s > bestScore + 1e-9
                    || (Math.abs(s - bestScore) <= 1e-9 && dist[i] < dist[best])
                    || (Math.abs(s - bestScore) <= 1e-9 && dist[i] == dist[best] && tie[i] < tie[best])) {
                second = bestScore;
                bestScore = s;
                best = i;
            } else if (s > second) {
                second = s;
            }
        }
        if (rejectedOut != null && rejectedOut.length > 0) rejectedOut[0] = rejected;
        if (secondOut != null && secondOut.length > 0) secondOut[0] = second;
        return best;
    }

    static int tieId(MapLocation loc) {
        if (loc == null) return Integer.MAX_VALUE;
        return (loc.x << 6) ^ loc.y;
    }

    static void traceDecision(RobotController rc, String unit, String decision, String action,
                              double chosenScore, double secondBest, int rejected) {
        if (!TRACE) return;
        int r = rc.getRoundNum();
        if (!("SPAWN".equals(decision) || "STATE".equals(decision) || r % 10 == 0)) return;
        String msg = "BT5TRACE|pkg=botathilla5_1|u=" + unit
                + "|d=" + decision
                + "|r=" + r
                + "|a=" + action
                + "|cs=" + ((int) (chosenScore * 1000))
                + "|sb=" + ((int) (secondBest * 1000))
                + "|rej=" + rejected;
        if (msg.length() > 240) msg = msg.substring(0, 240);
        rc.setIndicatorString(msg);
        System.out.println(msg);
    }

    static void traceMetric(RobotController rc, String unit,
                            int firstSplasherRound, int soldierSpawns, int splasherSpawns, int mopperSpawns) {
        if (!TRACE) return;
        int r = rc.getRoundNum();
        if (r % 50 != 0) return;
        String msg = "BT5MET|pkg=botathilla5_1|u=" + unit
                + "|r=" + r
                + "|first_spl=" + firstSplasherRound
                + "|spawn_s=" + soldierSpawns
                + "|spawn_sp=" + splasherSpawns
                + "|spawn_m=" + mopperSpawns;
        if (msg.length() > 240) msg = msg.substring(0, 240);
        rc.setIndicatorString(msg);
        System.out.println(msg);
    }
}
