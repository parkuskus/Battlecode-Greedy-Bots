package SPAARK;

import battlecode.common.*;

public class POI {
    public static final boolean ENABLE_INDICATORS = false;

    // 144 towers (including ruins)
    // filled in backwards cuz for loop bytecode optimization

    // each tower contains a location and tower type
    // bits 0-11 store location
    // bits 12-15 store tower type
    // - 0 for neutral (ruin)
    // - 1-3 for paint, money, defense (team a)
    // - 4-6 for paint, money, defense (team b)
    public static int numberOfTowers = 0;
    public static MapLocation[] towerLocs = new MapLocation[144];
    public static Team[] towerTeams = new Team[144];
    public static UnitType[] towerTypes = new UnitType[144];
    public static int[][] towerGrid = new int[][] {
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
    };
    // public static int[] towers = new int[] {
    // -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1,
    // -1, -1, -1, -1, -1, -1, -1, -1, -1
    // };

    // symmetry detection
    // set bit if its a wall, ruin, or we explored it, and use bit operators to
    // check symmetry
    public static long[] wall = new long[60]; // wall[xy.y] |= 1L << xy.x;
    public static long[] ruin = new long[60];
    public static long[] explored = new long[64];
    public static long[] explored2 = new long[64];
    public static boolean[] symmetry = new boolean[] { true, true, true };
    public static boolean criticalSymmetry = false;
    // 0: horz (the line of symmetry is horizontal and parallel to the x axis)
    // 1: vert
    // 2: rot

    public static int maxOpponentPaintSeen = 0;

    public static final int BROADCAST_FREQUENCY = 100;
    public static int[] lastBroadcastRounds = new int[144];

    // stores all tower and ruin data

    // upto 144 robots
    // 145 is symmetry
    public static StringBuilder[] robotsThatKnowInformation = new StringBuilder[] {
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
            new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(),
    };
    // basically critical array means this robot found this information, not
    // received through message
    // robot prioritizes critical informatoin to be sent first
    // public static boolean[] critical = new boolean[144];

    public static int paintTowers = 0;
    public static int moneyTowers = 0;
    public static int defenseTowers = 0;

    public static void addTower(int source, MapLocation loc, Team team, UnitType type) {
        // IMPORTANT: make sure to call addTower right after tower is built
        if (towerGrid[loc.y / 5][loc.x / 5] != -1) {
            int i = towerGrid[loc.y / 5][loc.x / 5];
            if (team != towerTeams[i] || type != towerTypes[i]) {
                Motion.exploreTowerCheck = true;
                if (towerTeams[i] == G.team) {
                    switch (towerTypes[i]) {
                        case LEVEL_ONE_PAINT_TOWER:
                            paintTowers--;
                            break;
                        case LEVEL_ONE_MONEY_TOWER:
                            moneyTowers--;
                            break;
                        case LEVEL_ONE_DEFENSE_TOWER:
                            defenseTowers--;
                            break;
                        default:
                            // throw new Exception("what are you building??? defense tower???");
                            // G.rc.resign();
                            break;
                    }
                }
                if (team == G.team) {
                    switch (type) {
                        case LEVEL_ONE_PAINT_TOWER:
                            paintTowers++;
                            break;
                        case LEVEL_ONE_MONEY_TOWER:
                            moneyTowers++;
                            break;
                        case LEVEL_ONE_DEFENSE_TOWER:
                            defenseTowers++;
                            break;
                        default:
                            break;
                    }
                }
                towerTeams[i] = team;
                towerTypes[i] = type;
                robotsThatKnowInformation[i] = new StringBuilder(":" + source);
                // if (source == -1) {
                // critical[i] = true;
                // } else {
                // critical[i] = false;
                // }
            } else {
                robotsThatKnowInformation[i].append(":" + source);
                // critical[i] = false;
            }
        } else {
            towerGrid[loc.y / 5][loc.x / 5] = numberOfTowers;
            towerLocs[numberOfTowers] = loc;
            numberOfTowers++;
            // TODO: optimize by copy paste
            addTower(source, loc, team, type);
        }
        // for (int i = 144; --i >= 0;) {
        // if (((towers[i] ^ data) & 0b111111111111) == 0 || towers[i] == -1) {
        // if (towers[i] != data) {
        // if (towers[i] != -1 && parseTowerTeam(towers[i]) == G.team) {
        // switch (parseTowerType(towers[i])) {
        // case LEVEL_ONE_PAINT_TOWER:
        // paintTowers--;
        // break;
        // case LEVEL_ONE_MONEY_TOWER:
        // moneyTowers--;
        // break;
        // }
        // }
        // towers[i] = data;
        // if (parseTowerTeam(data) == G.team) {
        // switch (parseTowerType(data)) {
        // case LEVEL_ONE_PAINT_TOWER:
        // paintTowers++;
        // break;
        // case LEVEL_ONE_MONEY_TOWER:
        // moneyTowers++;
        // break;
        // }
        // }
        // robotsThatKnowInformation[i] = new StringBuilder(":" + source);
        // if (source == -1) {
        // critical[i] = true;
        // } else {
        // critical[i] = false;
        // }
        // } else if (source != -1) {
        // robotsThatKnowInformation[i].append(":" + source);
        // critical[i] = false;
        // }
        // break;
        // }
        // }
    };

    public static void removeValidSymmetry(int source, int index) {
        if (symmetry[index]) {
            symmetry[index] = false;
            robotsThatKnowInformation[144] = new StringBuilder(":" + source);
            if (source == -1) {
                criticalSymmetry = true;
            } else {
                criticalSymmetry = false;
            }
        } else if (source != -1) {
            robotsThatKnowInformation[144].append(":" + source);
        }
    };

    // basically it takes tons of bytecode to update all map infos
    // so we only update the ones on the edge
    public static boolean firstUpdate = true;

    public static void updateRound() throws Exception {
        int a = Clock.getBytecodeNum();
        totalMessages = 0;
        readMessages();
        if (ENABLE_INDICATORS)
            G.indicatorString.append("READ=" + (Clock.getBytecodeNum() - a) + " ");

        // a = Clock.getBytecodeNum();
        for (int i = G.nearbyRuins.length; --i >= 0;) {
            if (G.rc.canSenseRobotAtLocation(G.nearbyRuins[i])) {
                RobotInfo info = G.rc.senseRobotAtLocation(G.nearbyRuins[i]);
                // addTower(-1, intifyTower(info.getTeam(), info.getType()) |
                // intifyLocation(nearbyRuins[i]));
                addTower(-1, G.nearbyRuins[i], info.getTeam(), info.getType().getBaseType());
            } else {
                // addTower(-1, intifyTower(Team.NEUTRAL, UnitType.LEVEL_ONE_DEFENSE_TOWER) |
                // intifyLocation(nearbyRuins[i]));
                addTower(-1, G.nearbyRuins[i], Team.NEUTRAL, UnitType.LEVEL_ONE_DEFENSE_TOWER);
            }
        }
        if (ENABLE_INDICATORS)
            G.indicatorString.append("TOWER=" + (Clock.getBytecodeNum() - a) + " ");
        a = Clock.getBytecodeNum();

        // drawIndicators(); // uses 5000 bytecode somehow

        // update symmetry array
        if (G.rc.getType().isRobotType()) {
            for (int i = G.nearbyRuins.length; --i >= 0;) {
                MapLocation xy = G.nearbyRuins[i];
                ruin[xy.y] |= 1L << xy.x;
            }
            switch (Math.min(G.me.y, 4)) {
                case 4:
                    explored[G.me.y - 4] |= 0b11111L << G.me.x - 2;
                case 3:
                    explored[G.me.y - 3] |= 0b1111111L << G.me.x - 3;
                case 2:
                    explored[G.me.y - 2] |= 0b111111111L << G.me.x - 4;
                case 1:
                    explored[G.me.y - 1] |= 0b111111111L << G.me.x - 4;
                default:
                    explored[G.me.y] |= 0b111111111L << G.me.x - 4;
                    explored[G.me.y + 1] |= 0b111111111L << G.me.x - 4;
                    explored[G.me.y + 2] |= 0b111111111L << G.me.x - 4;
                    explored[G.me.y + 3] |= 0b1111111L << G.me.x - 3;
                    explored[G.me.y + 4] |= 0b11111L << G.me.x - 2;
            }
            switch (G.me.x) {
                case 3:
                    explored[G.me.y] |= 0b111L << G.me.x - 3;
                    explored[(G.me.y + 63) % 64] |= 0b111L << G.me.x - 3;
                    explored[G.me.y + 1] |= 0b111L << G.me.x - 3;
                    explored[(G.me.y + 62) % 64] |= 0b111L << G.me.x - 3;
                    explored[G.me.y + 2] |= 0b111L << G.me.x - 3;
                    explored[(G.me.y + 61) % 64] |= 0b111L << G.me.x - 3;
                    explored[G.me.y + 3] |= 0b111L << G.me.x - 3;
                    explored[(G.me.y + 60) % 64] |= 3L << G.me.x - 2;
                    explored[G.me.y + 4] |= 3L << G.me.x - 2;
                    break;
                case 2:
                    explored[G.me.y] |= 3L << G.me.x - 2;
                    explored[(G.me.y + 63) % 64] |= 3L << G.me.x - 2;
                    explored[G.me.y + 1] |= 3L << G.me.x - 2;
                    explored[(G.me.y + 62) % 64] |= 3L << G.me.x - 2;
                    explored[G.me.y + 2] |= 3L << G.me.x - 2;
                    explored[(G.me.y + 61) % 64] |= 3L << G.me.x - 2;
                    explored[G.me.y + 3] |= 3L << G.me.x - 2;
                    explored[(G.me.y + 60) % 64] |= 3L << G.me.x - 2;
                    explored[G.me.y + 4] |= 3L << G.me.x - 2;
                    break;
                case 1:
                    explored[G.me.y] |= 1L << G.me.x - 1;
                    explored[(G.me.y + 63) % 64] |= 1L << G.me.x - 1;
                    explored[G.me.y + 1] |= 1L << G.me.x - 1;
                    explored[(G.me.y + 62) % 64] |= 1L << G.me.x - 1;
                    explored[G.me.y + 2] |= 1L << G.me.x - 1;
                    explored[(G.me.y + 61) % 64] |= 1L << G.me.x - 1;
                    explored[G.me.y + 3] |= 1L << G.me.x - 1;
                    explored[(G.me.y + 60) % 64] |= 1L << G.me.x - 1;
                    explored[G.me.y + 4] |= 1L << G.me.x - 1;
                    break;
                default:
                    break;
            }
            if (firstUpdate) {
                for (int i = G.nearbyMapInfos.length; --i >= 0;) {
                    MapLocation xy = G.nearbyMapInfos[i].getMapLocation();
                    if (G.nearbyMapInfos[i].isWall()) {
                        wall[xy.y] |= 1L << xy.x;
                    }
                }
                firstUpdate = false;
            } else {
                for (int i = 69; --i >= 37;) {
                    MapLocation xy = G.me.translate(G.range20X[i], G.range20Y[i]);
                    if (G.rc.onTheMap(xy) && G.rc.senseMapInfo(xy).isWall()) {
                        wall[xy.y] |= 1L << xy.x;
                    }
                }
            }
        }
        if (ENABLE_INDICATORS)
            G.indicatorString.append("INFO-BT " + (Clock.getBytecodeNum() - a) + " ");
        a = Clock.getBytecodeNum();
        // int opponentPaintSeen = 0;
        // for (int i = G.nearbyMapInfos.length; --i >= 0;) {
        // if (G.nearbyMapInfos[i].getPaint().isEnemy()) {
        // opponentPaintSeen++;
        // }
        // }
        // if (opponentPaintSeen > maxOpponentPaintSeen) {
        // maxOpponentPaintSeen = opponentPaintSeen;
        // }
        // G.indicatorString.append("PAINT-BT " + (Clock.getBytecodeNum() - a) + " ");
        int numValidSymmetries = (symmetry[0] ? 1 : 0) + (symmetry[1] ? 1 : 0) + (symmetry[2] ? 1 : 0);
        if (numValidSymmetries > 1) {
            if (symmetry[0] && !symmetryValid(0)) {
                removeValidSymmetry(-1, 0);
            }
            if (symmetry[1] && !symmetryValid(1)) {
                removeValidSymmetry(-1, 1);
            }
            if (symmetry[2] && !symmetryValid(2)) {
                removeValidSymmetry(-1, 2);
            }
        }
        if (ENABLE_INDICATORS)
            G.indicatorString.append("SYM=" + (Clock.getBytecodeNum() - a) + " ");
        a = Clock.getBytecodeNum();
        sendMessages();
        if (ENABLE_INDICATORS)
            G.indicatorString.append("SEND=" + (Clock.getBytecodeNum() - a) + " ");
    };

    public static boolean symmetryValid(int sym) throws Exception {
        int w = G.mapWidth;
        int h = G.mapHeight;
        switch (sym) {
            // only consider bits where we explored both it and its rotation
            case 0: // horz
                for (int i = Math.min(G.me.y + 5, h); --i >= Math.max(G.me.y - 4, 0);) {
                    long exploredRow = explored[i] & explored[h - i - 1];
                    if (((wall[i] ^ wall[h - i - 1]) & exploredRow) != 0)
                        return false;
                    if (((ruin[i] ^ ruin[h - i - 1]) & exploredRow) != 0) {
                        return false;
                    }
                }
                return true;
            case 1: // vert
                for (int i = Math.min(G.me.y + 5, h); --i >= Math.max(G.me.y - 4, 0);) {
                    long exploredRow = (Long.reverse(explored[i]) >> 64 - w) & explored[i];
                    if ((((Long.reverse(wall[i]) >> 64 - w) ^ wall[i]) & exploredRow) != 0)
                        return false;
                    if ((((Long.reverse(ruin[i]) >> 64 - w) ^ ruin[i]) & exploredRow) != 0)
                        return false;
                }
                return true;
            case 2: // rot
                for (int i = Math.min(G.me.y + 5, h); --i >= Math.max(G.me.y - 4, 0);) {
                    long exploredRow = (Long.reverse(explored[i]) >> 64 - w) & explored[h - i - 1];
                    if ((((Long.reverse(wall[i]) >> 64 - w) ^ wall[h - i - 1]) & exploredRow) != 0)
                        return false;
                    if ((((Long.reverse(ruin[i]) >> 64 - w) ^ ruin[h - i - 1]) & exploredRow) != 0)
                        return false;
                }
                return true;
            default:
                throw new Exception("invalid symmetry argument");
        }
    }

    public static MapLocation getOppositeMapLocation(MapLocation m, int sym) throws Exception {
        // get the opposite map location according to this symmetry
        switch (sym) {
            case 0:
                return new MapLocation(m.x, G.mapHeight - m.y - 1);
            case 1:
                return new MapLocation(G.mapWidth - m.x - 1, m.y);
            case 2:
                return new MapLocation(G.mapWidth - m.x - 1, G.mapHeight - m.y - 1);
            default:
                throw new Exception("invalid symmetry argument");
        }
    }

    // each message contains 2 towers/symmetries
    // because its 32 bit integer so it gets split into 2 16 bit integers
    public static int totalMessages = 0;

    public static void sendMessages() throws Exception {
        if (G.rc.getType().isTowerType()) {
            // we just send all info that the robots dont have
            for (int j = G.allyRobots.length; --j >= 0;) {
                RobotInfo r = G.allyRobots[Random.rand() % G.allyRobots.length];
                if (!G.rc.canSendMessage(r.getLocation())) {
                    continue;
                }
                String id = ":" + r.getID();
                int message = -1;
                int messages = 0;
                if (robotsThatKnowInformation[144].indexOf(id) == -1) {
                    message = intifySymmetry();
                    messages++;
                    robotsThatKnowInformation[144].append(id);
                }
                // for (int i = 144; --i >= 0;) {
                for (int i = numberOfTowers; --i >= 0;) {
                    if (robotsThatKnowInformation[i].indexOf(id) == -1) {
                        // message = appendToMessage(message, towers[i]);
                        message = appendToMessage(message,
                                intifyTower(towerTeams[i], towerTypes[i]) | intifyLocation(towerLocs[i]));
                        messages++;
                        robotsThatKnowInformation[i].append(id);
                        if (messages == 2) {
                            G.rc.sendMessage(r.getLocation(), message);
                            totalMessages++;
                            if (totalMessages == 20) {
                                return;
                            }
                            message = -1;
                            messages = 0;
                        }
                    }
                }
                if (messages != 0) {
                    G.rc.sendMessage(r.getLocation(), message);
                    totalMessages++;
                    if (totalMessages == 20) {
                        return;
                    }
                }
                if (Clock.getBytecodesLeft() < 3000)
                    return;
            }
            // if (totalMessages < 20) {
            // int offset = Random.rand();
            // int message = -1;
            // int messages = 0;
            // for (int i = numberOfTowers; --i >= 0;) {
            // int j = (i + offset) % numberOfTowers;
            // message = appendToMessage(message, intifyTower(towerTeams[j], towerTypes[j])
            // | intifyLocation(towerLocs[j]));
            // messages++;
            // if (messages == 2) {
            // G.rc.broadcastMessage(message);
            // totalMessages++;
            // if (totalMessages == 20) {
            // return;
            // }
            // message = -1;
            // messages = 0;
            // }
            // }
            // if (messages != 0) {
            // G.rc.broadcastMessage(message);
            // totalMessages++;
            // if (totalMessages == 20) {
            // return;
            // }
            // }
            // }
        } else {
            int message = -1;
            int messages = 0;
            if (criticalSymmetry) {
                message = intifySymmetry();
                messages += 1;
            }
            for (int j = G.allyRobots.length; --j >= 0;) {
                if (G.allyRobots[j].getType().isTowerType()) {
                    RobotInfo r = G.allyRobots[j];
                    if (G.rc.canSendMessage(r.getLocation())) {
                        if (messages < 2) {
                            String id = ":" + r.getID();
                            // for (int i = numberOfTowers; --i >= 0;) {
                            // if (critical[i] && !towerLocs[i].equals(r.getLocation())) {
                            // message = appendToMessage(message,
                            // intifyTower(towerTeams[i], towerTypes[i]) | intifyLocation(towerLocs[i]));
                            // messages++;
                            // critical[i] = false;
                            // robotsThatKnowInformation[i].append(id);
                            // if (messages == 2) {
                            // break;
                            // }
                            // }
                            // }
                            // for (int i = 144; --i >= 0;) {
                            // if (towers[i] == -1) {
                            // break;
                            // }
                            // if (critical[i]
                            // && ((intifyLocation(r.getLocation()) ^ towers[i]) & 0b111111111111) != 0) {
                            // message = appendToMessage(message, towers[i]);
                            // messages++;
                            // critical[i] = false;
                            // if (messages == 2) {
                            // break;
                            // }
                            // }
                            // }
                            if (messages < 2) {
                                if (robotsThatKnowInformation[144].indexOf(id) == -1) {
                                    message = appendToMessage(message, intifySymmetry());
                                    messages += 1;
                                    robotsThatKnowInformation[144].append(id);
                                }
                            }
                            if (messages < 2) {
                                for (int i = numberOfTowers; --i >= 0;) {
                                    if (robotsThatKnowInformation[i].indexOf(id) == -1) {
                                        message = appendToMessage(message, intifyTower(towerTeams[i], towerTypes[i])
                                                | intifyLocation(towerLocs[i]));
                                        messages++;
                                        robotsThatKnowInformation[i].append(id);
                                        if (messages == 2) {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (messages != 0) {
                            criticalSymmetry = false;
                            G.rc.sendMessage(r.getLocation(), message);
                            return;
                        }
                    }
                }
                if (Clock.getBytecodesLeft() < 2000)
                    break;
            }
        }
    };

    public static int relayMessage = -1;
    public static int relayMessages = 0;

    public static void readMessages() throws Exception {
        // what hapepns if message is sent in same round?? oof oof oof
        Message[] messages = G.rc.readMessages(G.round - 1);
        relayMessage = -1;
        relayMessages = 0;
        if (G.rc.getType().isTowerType()
                && G.round + 2000 >= lastBroadcastRounds[towerGrid[G.me.y / 5][G.me.x / 5]] + BROADCAST_FREQUENCY) {
            lastBroadcastRounds[towerGrid[G.me.y / 5][G.me.x / 5]] = G.round + 2000;
            relayMessage = intifyTower(G.rc.getTeam(), G.rc.getType()) | intifyLocation(G.me) | (1 << 15);
            relayMessages++;
            if (ENABLE_INDICATORS) {
                G.indicatorString.append("SENT-MESSAGE=" + G.me);
                for (Direction d : G.DIRECTIONS) {
                    G.rc.setIndicatorDot(G.me.add(d), 255, 255, 0);
                }
            }
        }
        int minBytecode = 10000;
        switch (G.rc.getType()) {
            case MOPPER:
                minBytecode = 11000;
        }
        for (Message m : messages) {
            if (Clock.getBytecodesLeft() < minBytecode)
                break;
            read16BitMessage(m.getSenderID(), m.getBytes() & 0b1111111111111111);
            if ((m.getBytes() >> 16) != 0) {
                read16BitMessage(m.getSenderID(), (m.getBytes() >> 16) & 0b1111111111111111);
            }
        }
        if (relayMessages != 0) {
            G.rc.broadcastMessage(relayMessage);
            totalMessages++;
        }
    };

    // basically bit 15 is used for relay messages between towers
    // dont want message spam
    public static void read16BitMessage(int id, int n) throws Exception {
        if ((n >> 12) == 7) {
            int n2 = n % 8;
            if (n2 % 2 == 0) {
                removeValidSymmetry(id, 0);
            }
            if ((n2 >> 1) % 2 == 0) {
                removeValidSymmetry(id, 1);
            }
            if ((n2 >> 2) % 2 == 0) {
                removeValidSymmetry(id, 2);
            }
        } else {
            addTower(id, parseLocation(n), parseTowerTeam(n), parseTowerType(n));
            if (G.rc.getType().isTowerType() && (n >> 15) == 1) {
                G.indicatorString.append("REC-MESSAGE=" + parseLocation(n));
            }
            if (G.rc.getType().isTowerType() && totalMessages < 20 && (n >> 15) == 1) {
                MapLocation loc = parseLocation(n);
                if (G.round + 2000 >= lastBroadcastRounds[towerGrid[loc.y / 5][loc.x / 5]] + BROADCAST_FREQUENCY) {
                    lastBroadcastRounds[towerGrid[loc.y / 5][loc.x / 5]] = G.round + 2000;
                    relayMessage = appendToMessage(relayMessage, n);
                    relayMessages++;
                    if (ENABLE_INDICATORS) {
                        G.indicatorString.append("SENT-MESSAGE=" + parseLocation(n));
                        G.rc.setIndicatorLine(G.me, loc, 255, 255, 0);
                    }
                    if (relayMessages == 2) {
                        G.rc.broadcastMessage(relayMessage);
                        totalMessages++;
                        relayMessage = -1;
                        relayMessages = 0;
                    }
                }
            }
        }
    };

    public static MapLocation parseLocation(int n) {
        // n -= 1;
        return new MapLocation((n & 0b111111), (n >> 6) & 0b111111);
    }

    public static int intifyLocation(MapLocation loc) {
        // return ((loc.y << 6) | loc.x) + 1;
        return ((loc.y << 6) | loc.x);
    }

    // team 0 for ally
    // team 1 for opp
    // team 2 for neutral
    public static Team parseTowerTeam(int n) {
        int t = (n >> 12) & 0b111;
        if (t == 0) {
            return Team.NEUTRAL;
        }
        if (t <= 3) {
            return Team.A;
        }
        return Team.B;
    }

    // 0: neutral
    // 1: paint
    // 2: chip
    // 3: defense
    public static UnitType parseTowerType(int n) {
        int t = (n >> 12) & 0b111;
        if (t == 0) {
            return UnitType.LEVEL_TWO_PAINT_TOWER;
        }
        if (t % 3 == 1) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        if (t % 3 == 2) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        return UnitType.LEVEL_ONE_DEFENSE_TOWER;
    }

    public static int intifyTower(Team team, UnitType type) {
        if (team == Team.NEUTRAL) {
            return 0;
        }
        if (type == UnitType.LEVEL_ONE_PAINT_TOWER || type == UnitType.LEVEL_TWO_PAINT_TOWER
                || type == UnitType.LEVEL_THREE_PAINT_TOWER) {
            return (1 + team.ordinal() * 3) << 12;
        }
        if (type == UnitType.LEVEL_ONE_MONEY_TOWER || type == UnitType.LEVEL_TWO_MONEY_TOWER
                || type == UnitType.LEVEL_THREE_MONEY_TOWER) {
            return (2 + team.ordinal() * 3) << 12;
        }
        if (type == UnitType.LEVEL_ONE_DEFENSE_TOWER || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
                || type == UnitType.LEVEL_THREE_DEFENSE_TOWER) {
            return (3 + team.ordinal() * 3) << 12;
        }
        return 0;
    }

    public static int intifySymmetry() {
        return (symmetry[0] ? 1 : 0) + (symmetry[1] ? 1 : 0) * 2 + (symmetry[2] ? 1 : 0) * 4 + (7 << 12);
    }

    public static int appendToMessage(int message, int a) {
        if (message == -1) {
            return a;
        }
        return (message << 16) | a;
    }

    public static void drawIndicators() {
        if (ENABLE_INDICATORS) {
            for (int i = numberOfTowers; --i >= 0;) {
                // for (int i = 144; --i >= 0;) {
                // if (towers[i] == -1) {
                // break;
                // }
                // System.out.println(parseLocation(towers[i]));
                // G.indicatorString.append(i + " ");
                try {
                    // if (parseTowerTeam(towers[i]) == G.team) {
                    if (towerTeams[i] == G.team) {
                        if (towerTypes[i] == UnitType.LEVEL_ONE_PAINT_TOWER) {
                            G.rc.setIndicatorLine(G.me, towerLocs[i], 0, 100, 0);
                            MapLocation loc = towerLocs[i];
                            for (int j = 8; --j >= 0;) {
                                G.rc.setIndicatorLine(loc, loc.add(G.DIRECTIONS[j]), 0, 255, 0);
                            }
                        } else {
                            G.rc.setIndicatorLine(G.me, towerLocs[i], 0, 150, 0);
                            MapLocation loc = towerLocs[i];
                            for (int j = 8; --j >= 0;) {
                                G.rc.setIndicatorLine(loc, loc.add(G.DIRECTIONS[j]), 0, 100, 0);
                            }
                            // MapLocation loc = parseLocation(towers[i]);
                            // for (int j = 8; --j >= 0;) {
                            // G.rc.setIndicatorDot(loc.add(G.DIRECTIONS[j]), 0, 255, 255);
                            // }
                        }
                    } else if (towerTeams[i] == G.opponentTeam) {
                        G.rc.setIndicatorLine(G.me, towerLocs[i], 150, 0, 0);
                        MapLocation loc = towerLocs[i];
                        for (int j = 8; --j >= 0;) {
                            G.rc.setIndicatorLine(loc, loc.add(G.DIRECTIONS[j]), 255, 0, 0);
                        }
                        // MapLocation loc = parseLocation(towers[i]);
                        // for (int j = 8; --j >= 0;) {
                        // G.rc.setIndicatorDot(loc.add(G.DIRECTIONS[j]), 255, 0, 0);
                        // }
                    } else {
                        G.rc.setIndicatorLine(G.me, towerLocs[i], 0, 0, 150);
                        MapLocation loc = towerLocs[i];
                        for (int j = 8; --j >= 0;) {
                            G.rc.setIndicatorLine(loc, loc.add(G.DIRECTIONS[j]), 0, 0, 255);
                        }
                        // MapLocation loc = parseLocation(towers[i]);
                        // for (int j = 8; --j >= 0;) {
                        // G.rc.setIndicatorDot(loc.add(G.DIRECTIONS[j]), 0, 0, 255);
                        // }
                    }
                } catch (Exception e) {
                }
            }
        }
    }
}
