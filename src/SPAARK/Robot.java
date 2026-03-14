package SPAARK;

import battlecode.common.*;

public class Robot {
    public static boolean[][] resourcePattern;
    public static boolean[][][] towerPatterns;
    public static UnitType[] towers = new UnitType[] {
            UnitType.LEVEL_ONE_DEFENSE_TOWER,
            UnitType.LEVEL_ONE_MONEY_TOWER,
            UnitType.LEVEL_ONE_PAINT_TOWER
    };

    public static final double RETREAT_PAINT_RATIO = 0.75; // OPTNET_PARAM

    public static void init() throws Exception {
        resourcePattern = G.rc.getResourcePattern();
        towerPatterns = new boolean[][][] {
                G.rc.getTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER),
                G.rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER),
                G.rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER)
        };
        Motion.paintNeededToStopRetreating = (int) (G.rc.getType().paintCapacity * RETREAT_PAINT_RATIO);
        // if (G.rc.getRoundNum() < 3 && G.rc.getType() == UnitType.SOLDIER) {
        // boolean foundPaintTower = false;
        // boolean foundMoneyTower = false;
        // for (int i = G.nearbyRuins.length; --i >= 0;) {
        // if (G.rc.canSenseRobotAtLocation(G.nearbyRuins[i])) {
        // RobotInfo tower = G.rc.senseRobotAtLocation(G.nearbyRuins[i]);
        // if (tower.team == G.team && tower.type.getBaseType() ==
        // UnitType.LEVEL_ONE_PAINT_TOWER) {
        // foundPaintTower = true;
        // }
        // if (tower.team == G.team && tower.type.getBaseType() ==
        // UnitType.LEVEL_ONE_MONEY_TOWER) {
        // foundMoneyTower = true;
        // }
        // }
        // }
        // if (foundPaintTower&&!foundMoneyTower) {
        // Soldier.mode = Soldier.MESSING_UP;
        // }
        // }
        switch (G.rc.getType()) {
            case SOLDIER -> Soldier.init();
            case SPLASHER -> Splasher.init();
            case MOPPER -> Mopper.init();
        }
    }

    public static void run() throws Exception {
        Motion.paintLost += Math.max(Motion.lastPaint - G.rc.getPaint(), 0);
        if (G.rc.getPaint() == 0 && G.rc.getChips() > 5000) {
            for (int i = G.allyRobots.length; --i >= 0;) {
                if (G.allyRobots[i].location.distanceSquaredTo(G.me) <= 8 && G.allyRobots[i].getType().isRobotType()) {
                    G.rc.disintegrate();
                }
            }
        }
        for (int i = G.nearbyRuins.length; --i >= 0;) {
            if (G.rc.canCompleteTowerPattern(Robot.towers[0], G.nearbyRuins[i]) && G.rc.senseMapInfo(G.nearbyRuins[i].add(Direction.SOUTH)).getMark() != PaintType.EMPTY) {
                G.rc.completeTowerPattern(Robot.towers[0], G.nearbyRuins[i]);
                POI.addTower(-1, G.nearbyRuins[i], G.team, Robot.towers[0]);
            }
            if (G.rc.canCompleteTowerPattern(Robot.towers[1], G.nearbyRuins[i]) && G.rc.senseMapInfo(G.nearbyRuins[i].add(Direction.WEST)).getMark() != PaintType.EMPTY) {
                G.rc.completeTowerPattern(Robot.towers[1], G.nearbyRuins[i]);
                POI.addTower(-1, G.nearbyRuins[i], G.team, Robot.towers[1]);
            }
            if (G.rc.canCompleteTowerPattern(Robot.towers[2], G.nearbyRuins[i]) && G.rc.senseMapInfo(G.nearbyRuins[i].add(Direction.EAST)).getMark() != PaintType.EMPTY) {
                G.rc.completeTowerPattern(Robot.towers[2], G.nearbyRuins[i]);
                POI.addTower(-1, G.nearbyRuins[i], G.team, Robot.towers[2]);
            }
        }
        for (int i = 9; --i >= 0;) {
            if (G.rc.canCompleteResourcePattern(G.me.translate(G.range20X[i], G.range20Y[i]))) {
                G.rc.completeResourcePattern(G.me.translate(G.range20X[i], G.range20Y[i]));
            }
        }
        Motion.tryTransferPaint();
        switch (G.rc.getType()) {
            case MOPPER -> Mopper.run();
            case SOLDIER -> Soldier.run();
            case SPLASHER -> Splasher.run();
            default -> throw new Exception("Challenge Complete! How Did We Get Here?");
        }
        for (int i = G.nearbyRuins.length; --i >= 0;) {
            if (G.rc.canCompleteTowerPattern(Robot.towers[0], G.nearbyRuins[i]) && G.rc.senseMapInfo(G.nearbyRuins[i].add(Direction.SOUTH)).getMark() != PaintType.EMPTY) {
                G.rc.completeTowerPattern(Robot.towers[0], G.nearbyRuins[i]);
                POI.addTower(-1, G.nearbyRuins[i], G.team, Robot.towers[0]);
            }
            if (G.rc.canCompleteTowerPattern(Robot.towers[1], G.nearbyRuins[i]) && G.rc.senseMapInfo(G.nearbyRuins[i].add(Direction.WEST)).getMark() != PaintType.EMPTY) {
                G.rc.completeTowerPattern(Robot.towers[1], G.nearbyRuins[i]);
                POI.addTower(-1, G.nearbyRuins[i], G.team, Robot.towers[1]);
            }
            if (G.rc.canCompleteTowerPattern(Robot.towers[2], G.nearbyRuins[i]) && G.rc.senseMapInfo(G.nearbyRuins[i].add(Direction.EAST)).getMark() != PaintType.EMPTY) {
                G.rc.completeTowerPattern(Robot.towers[2], G.nearbyRuins[i]);
                POI.addTower(-1, G.nearbyRuins[i], G.team, Robot.towers[2]);
            }
        }
        for (int i = 9; --i >= 0;) {
            if (G.rc.canCompleteResourcePattern(G.me.translate(G.range20X[i], G.range20Y[i]))) {
                G.rc.completeResourcePattern(G.me.translate(G.range20X[i], G.range20Y[i]));
            }
        }
        Motion.tryTransferPaint();
        Motion.lastPaint = G.rc.getPaint();
        G.indicatorString.append("SYM="
                + (POI.symmetry[0] ? "1" : "0") + (POI.symmetry[1] ? "1" : "0") + (POI.symmetry[2] ? "1 " : "0 "));
        POI.drawIndicators();
    }
}
