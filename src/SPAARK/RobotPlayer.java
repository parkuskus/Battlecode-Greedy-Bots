package SPAARK;

import battlecode.common.*;

public class RobotPlayer {
    public static void updateInfo() throws Exception {
        // every time we move, and every round
        G.me = G.rc.getLocation();
        G.allyRobots = G.rc.senseNearbyRobots(-1, G.team);
        G.opponentRobots = G.rc.senseNearbyRobots(-1, G.opponentTeam);
        G.allyRobotsString = new StringBuilder();
        for (int i = G.allyRobots.length; --i >= 0;) {
            if (G.allyRobots[i].type.isRobotType()) {
                G.allyRobotsString.append(G.allyRobots[i].location.toString());
            }
        }
        G.opponentRobotsString = new StringBuilder();
        for (int i = G.opponentRobots.length; --i >= 0;) {
            if (G.opponentRobots[i].type.isRobotType()) {
                G.opponentRobotsString.append(G.opponentRobots[i].location.toString());
            } else if (G.opponentRobots[i].type.getBaseType() == UnitType.LEVEL_ONE_DEFENSE_TOWER) {
                G.lastDefenseTower = G.opponentRobots[i].location;
                G.lastDefenseTowerRound = G.rc.getRoundNum();
            }
        }
        G.nearbyMapInfos = G.rc.senseNearbyMapInfos();
        G.nearbyRuins = G.rc.senseNearbyRuins(-1);
    }

    public static void updateMove() throws Exception {
        // every time we move
        updateInfo();
        Motion.lastVisitedLocations.append(G.me.toString());
        switch (Motion.lastVisitedLocations.length() % 8) {
            case 6:
                Motion.lastVisitedLocations.append("  ");
                break;
            case 7:
                Motion.lastVisitedLocations.append(" ");
                break;
        }
    }

    public static void updateRound() throws Exception {
        // every round
        G.maxChips = Math.max(G.maxChips, G.rc.getChips());
        Motion.movementCooldown -= GameConstants.COOLDOWNS_PER_TURN * (G.rc.getRoundNum() - G.round);
        Motion.movementCooldown = Math.max(Motion.movementCooldown, 0);
        G.round = G.rc.getRoundNum();
        updateInfo();
        POI.updateRound();
    }

    public static void run(RobotController rc) throws Exception {
        try {
            G.rc = rc;
            Random.state = G.rc.getID() * 0x2bda6bc + 0x9734e9;
            G.mapWidth = G.rc.getMapWidth();
            G.mapHeight = G.rc.getMapHeight();
            G.mapCenter = new MapLocation(G.mapWidth / 2, G.mapHeight / 2);
            G.mapArea = G.mapWidth * G.mapHeight;
            G.team = G.rc.getTeam();
            G.opponentTeam = G.team.opponent();
            G.roundSpawned = G.rc.getRoundNum();
            G.indicatorString = new StringBuilder();
            updateInfo();
            switch (G.rc.getType()) {
                case MOPPER, SOLDIER, SPLASHER -> Robot.init();
                default -> Tower.init();
            }
            // init bytecode count
            G.indicatorString.append("INIT " + Clock.getBytecodeNum() + " ");
            while (true) {
                int r = G.rc.getRoundNum();
                try {
                    updateRound();
                    switch (G.rc.getType()) {
                        case MOPPER, SOLDIER, SPLASHER -> Robot.run();
                        default -> Tower.run();
                    }
                    G.rc.setIndicatorString(G.indicatorString.toString());
                    G.indicatorString = new StringBuilder();
                } catch (GameActionException e) {
                    System.out.println("Unexpected GameActionException");
                    G.indicatorString.append(" GAErr!");
                    G.rc.setIndicatorString(G.indicatorString.toString());
                    G.indicatorString = new StringBuilder();
                    e.printStackTrace();
                } catch (Exception e) {
                    System.out.println("Unexpected Exception");
                    G.indicatorString.append(" Err!");
                    G.rc.setIndicatorString(G.indicatorString.toString());
                    G.indicatorString = new StringBuilder();
                    e.printStackTrace();
                }
                if (G.rc.getRoundNum() != r) {
                    System.err.println(
                            "Bytecode overflow! (Round " + r + ", " + G.rc.getType() + ", " + G.rc.getLocation() + ")");
                    G.indicatorString.append("BYTE=" + r + " ");
                }
                // for (int i = 0; i <= 50; i++) {
                // int
                // a=Random.rand()%G.mapHeight,b=Random.rand()%G.mapWidth,c=Random.rand()%G.mapHeight,d=Random.rand()%G.mapWidth;
                // G.rc.setIndicatorLine(new MapLocation(b, a), new MapLocation(d, c),
                // Random.rand()%256, Random.rand()%256, Random.rand()%256);
                // }
                G.lastChips = G.rc.getChips();
                G.lastNumberTowers = G.rc.getNumberTowers();
                Clock.yield();
            }
        } catch (GameActionException e) {
            System.out.println("Unexpected GameActionException");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Unexpected Exception");
            e.printStackTrace();
        }
    }
}