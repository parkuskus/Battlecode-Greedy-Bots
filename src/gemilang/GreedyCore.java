package gemilang;

import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/**
 * Verifier-safe greedy utilities (no disallowed java classes).
 */
public class GreedyCore {

    static final boolean TRACE = Boolean.parseBoolean(System.getProperty("bc.testing.greedyTrace", "false"));
    static final boolean ASSERT = Boolean.parseBoolean(System.getProperty("bc.testing.greedyAssert", "false"));

    private GreedyCore() {}

    static double score(
            double paintGain,
            double enemyPaintRemoved,
            double objectiveProgress,
            double resourceTempo,
            double support,
            double risk,
            double paintCost) {
        return 2.6 * paintGain
                + 2.2 * enemyPaintRemoved
                + 3.1 * objectiveProgress
                + 1.8 * resourceTempo
                + 1.1 * support
                - 2.8 * risk
                - 1.0 * paintCost;
    }

    static int tieId(MapLocation loc) {
        if (loc == null) return Integer.MAX_VALUE;
        return (loc.x << 6) + loc.y;
    }

    /**
     * Greedy pick on fixed candidate arrays.
     */
    static int pickBest(
            double[] score,
            boolean[] feasible,
            int[] dist,
            int[] tie,
            int[] rejectedOut,
            double[] secondOut) {
        int best = -1;
        int second = -1;
        int rejected = 0;
        for (int i = 0; i < score.length; i++) {
            if (!feasible[i]) {
                rejected++;
                continue;
            }
            if (best < 0 || better(i, best, score, dist, tie)) {
                second = best;
                best = i;
            } else if (second < 0 || better(i, second, score, dist, tie)) {
                second = i;
            }
        }
        rejectedOut[0] = rejected;
        secondOut[0] = second >= 0 ? score[second] : (best >= 0 ? score[best] : -9999.0);
        return best;
    }

    private static boolean better(int a, int b, double[] score, int[] dist, int[] tie) {
        if (score[a] > score[b] + 1e-9) return true;
        if (score[a] + 1e-9 < score[b]) return false;
        if (dist[a] < dist[b]) return true;
        if (dist[a] > dist[b]) return false;
        return tie[a] < tie[b];
    }

    static void traceDecision(
            RobotController rc,
            String unit,
            String decision,
            double chosenScore,
            double secondScore,
            int rejected,
            String action) {
        if (!TRACE) return;
        int round = rc.getRoundNum();
        if (round % 10 != 0) return;
        String line = "GDEC|pkg=gemilang|u=" + unit
                + "|d=" + decision
                + "|cs=" + fmt3(chosenScore)
                + "|gap=" + fmt3(chosenScore - secondScore)
                + "|rej=" + rejected
                + "|a=" + action;
        rc.setIndicatorString(line);
        System.out.println(line);
    }

    static void traceMetric(RobotController rc, String unit, Metric metric) {
        if (!TRACE) return;
        int round = rc.getRoundNum();
        if (round % 50 != 0) return;
        String line = "GMET|pkg=gemilang|u=" + unit
                + "|r=" + round
                + "|frontier_conversion_rate=" + fmt3(ratio(metric.frontierConverted, metric.frontierAttempts))
                + "|enemy_paint_cleanup_rate=" + fmt3(ratio(metric.enemyPaintCleaned, metric.enemyPaintSeen))
                + "|tower_completion_tempo=" + fmt3(metric.towerCompletions * 50.0 / Math.max(1, round - metric.startRound + 1))
                + "|srp_completion_tempo=" + fmt3(metric.srpCompletions * 50.0 / Math.max(1, round - metric.startRound + 1))
                + "|retreat_recovery_success=" + fmt3(ratio(metric.retreatRecoveries, metric.retreatEntries))
                + "|paint_efficiency=" + fmt3(ratio(metric.effectivePaintActions, metric.paintSpent));
        rc.setIndicatorString(line);
        System.out.println(line);
    }

    static void startTurn(RobotController rc, Metric metric) {
        if (metric.startRound < 0) metric.startRound = rc.getRoundNum();
        metric.turnStartPaint = rc.getPaint();
    }

    static void endTurn(RobotController rc, Metric metric) {
        int spent = metric.turnStartPaint - rc.getPaint();
        if (spent > 0) metric.paintSpent += spent;
    }

    static void check(boolean condition, String message) {
        if (ASSERT && !condition) {
            throw new IllegalStateException(message);
        }
    }

    static String fmt3(double value) {
        int scaled = (int) Math.round(value * 1000.0);
        boolean neg = scaled < 0;
        if (neg) scaled = -scaled;
        int whole = scaled / 1000;
        int frac = scaled % 1000;
        String fracStr = frac < 10 ? "00" + frac : (frac < 100 ? "0" + frac : Integer.toString(frac));
        return (neg ? "-" : "") + whole + "." + fracStr;
    }

    static String locLabel(MapLocation loc) {
        if (loc == null) return "none";
        return loc.x + "," + loc.y;
    }

    private static double ratio(int num, int den) {
        if (den <= 0) return 0.0;
        return (double) num / den;
    }

    static class Metric {
        int startRound = -1;
        int turnStartPaint = 0;
        int frontierAttempts = 0;
        int frontierConverted = 0;
        int enemyPaintSeen = 0;
        int enemyPaintCleaned = 0;
        int towerCompletions = 0;
        int srpCompletions = 0;
        int retreatEntries = 0;
        int retreatRecoveries = 0;
        int paintSpent = 0;
        int effectivePaintActions = 0;
    }
}
