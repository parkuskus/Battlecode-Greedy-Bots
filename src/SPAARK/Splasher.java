package SPAARK;

import battlecode.common.*;

public class Splasher {
    public static final int EXPLORE = 0;
    public static final int RETREAT = 2;
    public static int mode = EXPLORE;
    // controls round between visiting ruins
    public static final int VISIT_TIMEOUT = 75;

    public static final int SPL_INITIAL_ATK_MULT = 3;

    public static int[] moveScores = new int[9];
    public static int[] attackScores = new int[37]; // score for attacking this square

    public static void init() throws Exception {
    }

    /**
     * Always:
     * If low on paint, retreat
     * Default to explore mode
     * If found targets to paint in POI storage, switch to attack mode
     * 
     * Explore:
     * Run around randomly painting stuff with some microstrategy
     * TODO: AVOID PAINTING OVER SRP, TOWER PATTERNS, PAINT NEAR OWN PAINT TO AVOID
     * TODO: DETACHED TERRITORY
     * 
     */
    public static void run() throws Exception {
        // occasionally clear ruins to not oof forever
        // if (G.rc.getPaint() < Motion.getRetreatPaint() && G.maxChips < 6000 &&
        // G.allyRobots.length < 9) {
        if (G.rc.getPaint() < Motion.getRetreatPaint() && G.maxChips < 6000 && G.allyRobots.length < 9) {
            Motion.setRetreatLoc();
            if (Motion.retreatTower != -1 && G.me.distanceSquaredTo(Motion.retreatLoc) < 9) {
                mode = RETREAT;
            } else if (mode == RETREAT) {
                mode = EXPLORE;
                Motion.retreatTower = -1;
            }
        } else if (mode == RETREAT) {
            mode = EXPLORE;
            Motion.retreatTower = -1;
        }
        // if (mode == RETREAT) {
        // Motion.setRetreatLoc();
        // if (Motion.retreatTower == -1 || G.me.distanceSquaredTo(Motion.retreatLoc) >=
        // 9) {
        // mode = EXPLORE;
        // }
        // }
        // int a = Clock.getBytecodeNum();
        // switch (mode) {
        // ADD CASES HERE FOR SWITCHING MODES
        // }
        int b = Clock.getBytecodeNum();
        // G.indicatorString.append((b - a) + " ");
        attackScores[0] = attackScores[1] = attackScores[2] = attackScores[3] = attackScores[4] = attackScores[5] = attackScores[6] = attackScores[7] = attackScores[8] = attackScores[9] = attackScores[10] = attackScores[11] = attackScores[12] = attackScores[13] = attackScores[14] = attackScores[15] = attackScores[16] = attackScores[17] = attackScores[18] = attackScores[19] = attackScores[20] = attackScores[21] = attackScores[22] = attackScores[23] = attackScores[24] = attackScores[25] = attackScores[26] = attackScores[27] = attackScores[28] = attackScores[29] = attackScores[30] = attackScores[31] = attackScores[32] = attackScores[33] = attackScores[34] = attackScores[35] = attackScores[36] = moveScores[0] = moveScores[1] = moveScores[2] = moveScores[3] = moveScores[4] = moveScores[5] = moveScores[6] = moveScores[7] = moveScores[8] = 0;
        switch (mode) {
            case EXPLORE -> {
                G.indicatorString.append("EXPLORE ");
                if (G.rc.isMovementReady()) {
                    exploreMoveScores();
                }
                if (G.rc.isActionReady()) {
                    // org.objectweb.asm.MethodTooLargeException: Method too large:
                    // SPAARK/Splasher.exploreAttackScores
                    exploreAttackScores1();
                    exploreAttackScores2();
                }
            }
            case RETREAT -> {
                G.indicatorString.append("RETREAT ");
                if (G.rc.isMovementReady()) {
                    retreatMoveScores();
                }
                // TODO: paint under yourself
            }
        }
        int cmax = attackScores[0];
        int cx = 0;
        int cy = 0;
        if (attackScores[1] > cmax) {
            cmax = attackScores[1];
            cx = -1;
            cy = 0;
        }
        if (attackScores[2] > cmax) {
            cmax = attackScores[2];
            cx = 0;
            cy = -1;
        }
        if (attackScores[3] > cmax) {
            cmax = attackScores[3];
            cx = 0;
            cy = 1;
        }
        if (attackScores[4] > cmax) {
            cmax = attackScores[4];
            cx = 1;
            cy = 0;
        }
        if (attackScores[5] > cmax) {
            cmax = attackScores[5];
            cx = -1;
            cy = -1;
        }
        if (attackScores[6] > cmax) {
            cmax = attackScores[6];
            cx = -1;
            cy = 1;
        }
        if (attackScores[7] > cmax) {
            cmax = attackScores[7];
            cx = 1;
            cy = -1;
        }
        if (attackScores[8] > cmax) {
            cmax = attackScores[8];
            cx = 1;
            cy = 1;
        }
        // store total score and best attack location for each direction (incl
        // Direction.CENTER)
        int[] allmax = new int[] {
                cmax, cmax, cmax, cmax, cmax, cmax, cmax, cmax, cmax
        };
        int[] allx = new int[] {
                cx, cx, cx, cx, cx, cx, cx, cx, cx
        };
        int[] ally = new int[] {
                cy, cy, cy, cy, cy, cy, cy, cy, cy
        };
        if (attackScores[13] > allmax[0]) {
            allmax[0] = attackScores[13];
            allx[0] = -2;
            ally[0] = -1;
        }
        if (attackScores[15] > allmax[0]) {
            allmax[0] = attackScores[15];
            allx[0] = -1;
            ally[0] = -2;
        }
        if (attackScores[21] > allmax[0]) {
            allmax[0] = attackScores[21];
            allx[0] = -2;
            ally[0] = -2;
        }
        if (attackScores[29] > allmax[0]) {
            allmax[0] = attackScores[29];
            allx[0] = -3;
            ally[0] = -1;
        }
        if (attackScores[31] > allmax[0]) {
            allmax[0] = attackScores[31];
            allx[0] = -1;
            ally[0] = -3;
        }
        if (attackScores[15] > allmax[1]) {
            allmax[1] = attackScores[15];
            allx[1] = -1;
            ally[1] = -2;
        }
        if (attackScores[17] > allmax[1]) {
            allmax[1] = attackScores[17];
            allx[1] = 1;
            ally[1] = -2;
        }
        if (attackScores[13] > allmax[1]) {
            allmax[1] = attackScores[13];
            allx[1] = -2;
            ally[1] = -1;
        }
        if (attackScores[26] > allmax[1]) {
            allmax[1] = attackScores[26];
            allx[1] = 0;
            ally[1] = -3;
        }
        if (attackScores[19] > allmax[1]) {
            allmax[1] = attackScores[19];
            allx[1] = 2;
            ally[1] = -1;
        }
        if (attackScores[17] > allmax[2]) {
            allmax[2] = attackScores[17];
            allx[2] = 1;
            ally[2] = -2;
        }
        if (attackScores[19] > allmax[2]) {
            allmax[2] = attackScores[19];
            allx[2] = 2;
            ally[2] = -1;
        }
        if (attackScores[23] > allmax[2]) {
            allmax[2] = attackScores[23];
            allx[2] = 2;
            ally[2] = -2;
        }
        if (attackScores[33] > allmax[2]) {
            allmax[2] = attackScores[33];
            allx[2] = 1;
            ally[2] = -3;
        }
        if (attackScores[35] > allmax[2]) {
            allmax[2] = attackScores[35];
            allx[2] = 3;
            ally[2] = -1;
        }
        if (attackScores[19] > allmax[3]) {
            allmax[3] = attackScores[19];
            allx[3] = 2;
            ally[3] = -1;
        }
        if (attackScores[20] > allmax[3]) {
            allmax[3] = attackScores[20];
            allx[3] = 2;
            ally[3] = 1;
        }
        if (attackScores[17] > allmax[3]) {
            allmax[3] = attackScores[17];
            allx[3] = 1;
            ally[3] = -2;
        }
        if (attackScores[18] > allmax[3]) {
            allmax[3] = attackScores[18];
            allx[3] = 1;
            ally[3] = 2;
        }
        if (attackScores[28] > allmax[3]) {
            allmax[3] = attackScores[28];
            allx[3] = 3;
            ally[3] = 0;
        }
        if (attackScores[18] > allmax[4]) {
            allmax[4] = attackScores[18];
            allx[4] = 1;
            ally[4] = 2;
        }
        if (attackScores[20] > allmax[4]) {
            allmax[4] = attackScores[20];
            allx[4] = 2;
            ally[4] = 1;
        }
        if (attackScores[24] > allmax[4]) {
            allmax[4] = attackScores[24];
            allx[4] = 2;
            ally[4] = 2;
        }
        if (attackScores[34] > allmax[4]) {
            allmax[4] = attackScores[34];
            allx[4] = 1;
            ally[4] = 3;
        }
        if (attackScores[36] > allmax[4]) {
            allmax[4] = attackScores[36];
            allx[4] = 3;
            ally[4] = 1;
        }
        if (attackScores[16] > allmax[5]) {
            allmax[5] = attackScores[16];
            allx[5] = -1;
            ally[5] = 2;
        }
        if (attackScores[18] > allmax[5]) {
            allmax[5] = attackScores[18];
            allx[5] = 1;
            ally[5] = 2;
        }
        if (attackScores[14] > allmax[5]) {
            allmax[5] = attackScores[14];
            allx[5] = -2;
            ally[5] = 1;
        }
        if (attackScores[27] > allmax[5]) {
            allmax[5] = attackScores[27];
            allx[5] = 0;
            ally[5] = 3;
        }
        if (attackScores[20] > allmax[5]) {
            allmax[5] = attackScores[20];
            allx[5] = 2;
            ally[5] = 1;
        }
        if (attackScores[14] > allmax[6]) {
            allmax[6] = attackScores[14];
            allx[6] = -2;
            ally[6] = 1;
        }
        if (attackScores[16] > allmax[6]) {
            allmax[6] = attackScores[16];
            allx[6] = -1;
            ally[6] = 2;
        }
        if (attackScores[22] > allmax[6]) {
            allmax[6] = attackScores[22];
            allx[6] = -2;
            ally[6] = 2;
        }
        if (attackScores[30] > allmax[6]) {
            allmax[6] = attackScores[30];
            allx[6] = -3;
            ally[6] = 1;
        }
        if (attackScores[32] > allmax[6]) {
            allmax[6] = attackScores[32];
            allx[6] = -1;
            ally[6] = 3;
        }
        if (attackScores[13] > allmax[7]) {
            allmax[7] = attackScores[13];
            allx[7] = -2;
            ally[7] = -1;
        }
        if (attackScores[14] > allmax[7]) {
            allmax[7] = attackScores[14];
            allx[7] = -2;
            ally[7] = 1;
        }
        if (attackScores[25] > allmax[7]) {
            allmax[7] = attackScores[25];
            allx[7] = -3;
            ally[7] = 0;
        }
        if (attackScores[15] > allmax[7]) {
            allmax[7] = attackScores[15];
            allx[7] = -1;
            ally[7] = -2;
        }
        if (attackScores[16] > allmax[7]) {
            allmax[7] = attackScores[16];
            allx[7] = -1;
            ally[7] = 2;
        }
        // copyspaghetti from Motion.microMove and Mopper but whatever
        // spaghetti hardcoding 50 paint
        if (G.rc.isActionReady() && G.rc.getPaint() > 50) {
            int best = 8;
            int numBest = 1;
            for (int i = 8; --i >= 0;) {
                if (allmax[i] + moveScores[i] > allmax[best] + moveScores[best]) {
                    best = i;
                    numBest = 1;
                } else if (allmax[i] + moveScores[i] == allmax[best] + moveScores[best]
                        && Random.rand() % ++numBest == 0) {
                    best = i;
                }
            }
            int needed = G.mapArea * SPL_INITIAL_ATK_MULT / G.rc.getRoundNum() + 300;
            // if (mode == EXPLORE && G.me.distanceSquaredTo(Motion.exploreLoc) < 81) {
            // needed -= 100;
            // }
            if (allmax[best] > needed) {
                MapLocation attackLoc = G.me.translate(allx[best], ally[best]);
                // try to move before attacking if possible so the paint we used in the attack
                // isn't factored into movement cooldown
                if (attackLoc.isWithinDistanceSquared(G.me.add(G.ALL_DIRECTIONS[best]), 4)) {
                    Motion.move(G.ALL_DIRECTIONS[best]);
                    if (G.rc.canAttack(attackLoc)) {
                        G.rc.attack(attackLoc);
                    }
                } else {
                    if (G.rc.canAttack(attackLoc)) {
                        G.rc.attack(attackLoc);
                    }
                    Motion.move(G.ALL_DIRECTIONS[best]);
                }
            }
        }
        if (G.rc.isMovementReady()) {
            int best = 8;
            int numBest = 1;
            for (int i = 8; --i >= 0;) {
                if (moveScores[i] > moveScores[best]) {
                    best = i;
                    numBest = 1;
                } else if (moveScores[i] == moveScores[best] && Random.rand() % ++numBest == 0) {
                    best = i;
                }
            }
            Motion.move(G.ALL_DIRECTIONS[best]);
        }
        switch (mode) {
            case EXPLORE -> {
                G.rc.setIndicatorDot(G.me, 0, 255, 0);
            }
            case RETREAT -> {
                G.rc.setIndicatorDot(G.me, 255, 0, 255);
            }
        }
        G.indicatorString.append((Clock.getBytecodeNum() - b) + " ");
    }

    public static void exploreAttackScores1() throws Exception {
        MapLocation loc = G.me;
        loc = G.me.translate(0, 0);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                        } else {
                            attackScores[12] += 100;
                            attackScores[11] += 100;
                            attackScores[9] += 100;
                            attackScores[10] += 100;
                            attackScores[5] += 100;
                            attackScores[2] += 100;
                            attackScores[7] += 100;
                            attackScores[4] += 100;
                            attackScores[8] += 100;
                            attackScores[3] += 100;
                            attackScores[6] += 100;
                            attackScores[1] += 100;
                            attackScores[0] += 100;
                        }
                    } else {
                        attackScores[12] += 25;
                        attackScores[11] += 25;
                        attackScores[9] += 25;
                        attackScores[10] += 25;
                        attackScores[5] += 25;
                        attackScores[2] += 25;
                        attackScores[7] += 25;
                        attackScores[4] += 25;
                        attackScores[8] += 25;
                        attackScores[3] += 25;
                        attackScores[6] += 25;
                        attackScores[1] += 25;
                        attackScores[0] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[5] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[5] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[5] += 50;
                    attackScores[2] += 50;
                    attackScores[7] += 50;
                    attackScores[4] += 50;
                    attackScores[8] += 50;
                    attackScores[3] += 50;
                    attackScores[6] += 50;
                    attackScores[1] += 50;
                    attackScores[0] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[5] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[5] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-1, 0);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                        } else {
                            attackScores[4] += 100;
                            attackScores[16] += 100;
                            attackScores[25] += 100;
                            attackScores[15] += 100;
                            attackScores[13] += 100;
                            attackScores[5] += 100;
                            attackScores[2] += 100;
                            attackScores[0] += 100;
                            attackScores[3] += 100;
                            attackScores[6] += 100;
                            attackScores[14] += 100;
                            attackScores[9] += 100;
                            attackScores[1] += 100;
                        }
                    } else {
                        attackScores[4] += 25;
                        attackScores[16] += 25;
                        attackScores[25] += 25;
                        attackScores[15] += 25;
                        attackScores[13] += 25;
                        attackScores[5] += 25;
                        attackScores[2] += 25;
                        attackScores[0] += 25;
                        attackScores[3] += 25;
                        attackScores[6] += 25;
                        attackScores[14] += 25;
                        attackScores[9] += 25;
                        attackScores[1] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[13] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[13] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[13] += 50;
                    attackScores[5] += 50;
                    attackScores[2] += 50;
                    attackScores[0] += 50;
                    attackScores[3] += 50;
                    attackScores[6] += 50;
                    attackScores[14] += 50;
                    attackScores[9] += 50;
                    attackScores[1] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[13] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[13] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(0, -1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                        } else {
                            attackScores[19] += 100;
                            attackScores[3] += 100;
                            attackScores[13] += 100;
                            attackScores[26] += 100;
                            attackScores[15] += 100;
                            attackScores[10] += 100;
                            attackScores[17] += 100;
                            attackScores[7] += 100;
                            attackScores[4] += 100;
                            attackScores[0] += 100;
                            attackScores[1] += 100;
                            attackScores[5] += 100;
                            attackScores[2] += 100;
                        }
                    } else {
                        attackScores[19] += 25;
                        attackScores[3] += 25;
                        attackScores[13] += 25;
                        attackScores[26] += 25;
                        attackScores[15] += 25;
                        attackScores[10] += 25;
                        attackScores[17] += 25;
                        attackScores[7] += 25;
                        attackScores[4] += 25;
                        attackScores[0] += 25;
                        attackScores[1] += 25;
                        attackScores[5] += 25;
                        attackScores[2] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[15] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[15] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[15] += 50;
                    attackScores[10] += 50;
                    attackScores[17] += 50;
                    attackScores[7] += 50;
                    attackScores[4] += 50;
                    attackScores[0] += 50;
                    attackScores[1] += 50;
                    attackScores[5] += 50;
                    attackScores[2] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[15] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[15] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(0, 1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                        } else {
                            attackScores[20] += 100;
                            attackScores[27] += 100;
                            attackScores[14] += 100;
                            attackScores[2] += 100;
                            attackScores[1] += 100;
                            attackScores[0] += 100;
                            attackScores[4] += 100;
                            attackScores[8] += 100;
                            attackScores[18] += 100;
                            attackScores[11] += 100;
                            attackScores[16] += 100;
                            attackScores[6] += 100;
                            attackScores[3] += 100;
                        }
                    } else {
                        attackScores[20] += 25;
                        attackScores[27] += 25;
                        attackScores[14] += 25;
                        attackScores[2] += 25;
                        attackScores[1] += 25;
                        attackScores[0] += 25;
                        attackScores[4] += 25;
                        attackScores[8] += 25;
                        attackScores[18] += 25;
                        attackScores[11] += 25;
                        attackScores[16] += 25;
                        attackScores[6] += 25;
                        attackScores[3] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[1] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[1] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[1] += 50;
                    attackScores[0] += 50;
                    attackScores[4] += 50;
                    attackScores[8] += 50;
                    attackScores[18] += 50;
                    attackScores[11] += 50;
                    attackScores[16] += 50;
                    attackScores[6] += 50;
                    attackScores[3] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[1] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[1] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(1, 0);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                        } else {
                            attackScores[28] += 100;
                            attackScores[18] += 100;
                            attackScores[1] += 100;
                            attackScores[17] += 100;
                            attackScores[2] += 100;
                            attackScores[7] += 100;
                            attackScores[19] += 100;
                            attackScores[12] += 100;
                            attackScores[20] += 100;
                            attackScores[8] += 100;
                            attackScores[3] += 100;
                            attackScores[0] += 100;
                            attackScores[4] += 100;
                        }
                    } else {
                        attackScores[28] += 25;
                        attackScores[18] += 25;
                        attackScores[1] += 25;
                        attackScores[17] += 25;
                        attackScores[2] += 25;
                        attackScores[7] += 25;
                        attackScores[19] += 25;
                        attackScores[12] += 25;
                        attackScores[20] += 25;
                        attackScores[8] += 25;
                        attackScores[3] += 25;
                        attackScores[0] += 25;
                        attackScores[4] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[2] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[2] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[2] += 50;
                    attackScores[7] += 50;
                    attackScores[19] += 50;
                    attackScores[12] += 50;
                    attackScores[20] += 50;
                    attackScores[8] += 50;
                    attackScores[3] += 50;
                    attackScores[0] += 50;
                    attackScores[4] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[2] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[2] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-1, -1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                        } else {
                            attackScores[7] += 100;
                            attackScores[6] += 100;
                            attackScores[29] += 100;
                            attackScores[31] += 100;
                            attackScores[21] += 100;
                            attackScores[15] += 100;
                            attackScores[10] += 100;
                            attackScores[2] += 100;
                            attackScores[0] += 100;
                            attackScores[1] += 100;
                            attackScores[9] += 100;
                            attackScores[13] += 100;
                            attackScores[5] += 100;
                        }
                    } else {
                        attackScores[7] += 25;
                        attackScores[6] += 25;
                        attackScores[29] += 25;
                        attackScores[31] += 25;
                        attackScores[21] += 25;
                        attackScores[15] += 25;
                        attackScores[10] += 25;
                        attackScores[2] += 25;
                        attackScores[0] += 25;
                        attackScores[1] += 25;
                        attackScores[9] += 25;
                        attackScores[13] += 25;
                        attackScores[5] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[21] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[21] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[21] += 50;
                    attackScores[15] += 50;
                    attackScores[10] += 50;
                    attackScores[2] += 50;
                    attackScores[0] += 50;
                    attackScores[1] += 50;
                    attackScores[9] += 50;
                    attackScores[13] += 50;
                    attackScores[5] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[21] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[21] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-1, 1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                        } else {
                            attackScores[8] += 100;
                            attackScores[32] += 100;
                            attackScores[30] += 100;
                            attackScores[5] += 100;
                            attackScores[9] += 100;
                            attackScores[1] += 100;
                            attackScores[0] += 100;
                            attackScores[3] += 100;
                            attackScores[11] += 100;
                            attackScores[16] += 100;
                            attackScores[22] += 100;
                            attackScores[14] += 100;
                            attackScores[6] += 100;
                        }
                    } else {
                        attackScores[8] += 25;
                        attackScores[32] += 25;
                        attackScores[30] += 25;
                        attackScores[5] += 25;
                        attackScores[9] += 25;
                        attackScores[1] += 25;
                        attackScores[0] += 25;
                        attackScores[3] += 25;
                        attackScores[11] += 25;
                        attackScores[16] += 25;
                        attackScores[22] += 25;
                        attackScores[14] += 25;
                        attackScores[6] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[9] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[9] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[9] += 50;
                    attackScores[1] += 50;
                    attackScores[0] += 50;
                    attackScores[3] += 50;
                    attackScores[11] += 50;
                    attackScores[16] += 50;
                    attackScores[22] += 50;
                    attackScores[14] += 50;
                    attackScores[6] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[9] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[9] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(1, -1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[35] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                        } else {
                            attackScores[35] += 100;
                            attackScores[8] += 100;
                            attackScores[5] += 100;
                            attackScores[33] += 100;
                            attackScores[10] += 100;
                            attackScores[17] += 100;
                            attackScores[23] += 100;
                            attackScores[19] += 100;
                            attackScores[12] += 100;
                            attackScores[4] += 100;
                            attackScores[0] += 100;
                            attackScores[2] += 100;
                            attackScores[7] += 100;
                        }
                    } else {
                        attackScores[35] += 25;
                        attackScores[8] += 25;
                        attackScores[5] += 25;
                        attackScores[33] += 25;
                        attackScores[10] += 25;
                        attackScores[17] += 25;
                        attackScores[23] += 25;
                        attackScores[19] += 25;
                        attackScores[12] += 25;
                        attackScores[4] += 25;
                        attackScores[0] += 25;
                        attackScores[2] += 25;
                        attackScores[7] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[10] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[10] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[10] += 50;
                    attackScores[17] += 50;
                    attackScores[23] += 50;
                    attackScores[19] += 50;
                    attackScores[12] += 50;
                    attackScores[4] += 50;
                    attackScores[0] += 50;
                    attackScores[2] += 50;
                    attackScores[7] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[10] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[10] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(1, 1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[36] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                        } else {
                            attackScores[36] += 100;
                            attackScores[34] += 100;
                            attackScores[6] += 100;
                            attackScores[7] += 100;
                            attackScores[0] += 100;
                            attackScores[4] += 100;
                            attackScores[12] += 100;
                            attackScores[20] += 100;
                            attackScores[24] += 100;
                            attackScores[18] += 100;
                            attackScores[11] += 100;
                            attackScores[3] += 100;
                            attackScores[8] += 100;
                        }
                    } else {
                        attackScores[36] += 25;
                        attackScores[34] += 25;
                        attackScores[6] += 25;
                        attackScores[7] += 25;
                        attackScores[0] += 25;
                        attackScores[4] += 25;
                        attackScores[12] += 25;
                        attackScores[20] += 25;
                        attackScores[24] += 25;
                        attackScores[18] += 25;
                        attackScores[11] += 25;
                        attackScores[3] += 25;
                        attackScores[8] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[0] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[0] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[0] += 50;
                    attackScores[4] += 50;
                    attackScores[12] += 50;
                    attackScores[20] += 50;
                    attackScores[24] += 50;
                    attackScores[18] += 50;
                    attackScores[11] += 50;
                    attackScores[3] += 50;
                    attackScores[8] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[0] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[0] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-2, 0);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                        } else {
                            attackScores[0] += 100;
                            attackScores[22] += 100;
                            attackScores[21] += 100;
                            attackScores[29] += 100;
                            attackScores[13] += 100;
                            attackScores[5] += 100;
                            attackScores[1] += 100;
                            attackScores[6] += 100;
                            attackScores[14] += 100;
                            attackScores[30] += 100;
                            attackScores[25] += 100;
                            attackScores[9] += 100;
                        }
                    } else {
                        attackScores[0] += 25;
                        attackScores[22] += 25;
                        attackScores[21] += 25;
                        attackScores[29] += 25;
                        attackScores[13] += 25;
                        attackScores[5] += 25;
                        attackScores[1] += 25;
                        attackScores[6] += 25;
                        attackScores[14] += 25;
                        attackScores[30] += 25;
                        attackScores[25] += 25;
                        attackScores[9] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[29] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[29] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[29] += 50;
                    attackScores[13] += 50;
                    attackScores[5] += 50;
                    attackScores[1] += 50;
                    attackScores[6] += 50;
                    attackScores[14] += 50;
                    attackScores[30] += 50;
                    attackScores[25] += 50;
                    attackScores[9] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[29] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[29] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(0, -2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                        } else {
                            attackScores[23] += 100;
                            attackScores[0] += 100;
                            attackScores[21] += 100;
                            attackScores[31] += 100;
                            attackScores[26] += 100;
                            attackScores[33] += 100;
                            attackScores[17] += 100;
                            attackScores[7] += 100;
                            attackScores[2] += 100;
                            attackScores[5] += 100;
                            attackScores[15] += 100;
                            attackScores[10] += 100;
                        }
                    } else {
                        attackScores[23] += 25;
                        attackScores[0] += 25;
                        attackScores[21] += 25;
                        attackScores[31] += 25;
                        attackScores[26] += 25;
                        attackScores[33] += 25;
                        attackScores[17] += 25;
                        attackScores[7] += 25;
                        attackScores[2] += 25;
                        attackScores[5] += 25;
                        attackScores[15] += 25;
                        attackScores[10] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[31] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[31] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[31] += 50;
                    attackScores[26] += 50;
                    attackScores[33] += 50;
                    attackScores[17] += 50;
                    attackScores[7] += 50;
                    attackScores[2] += 50;
                    attackScores[5] += 50;
                    attackScores[15] += 50;
                    attackScores[10] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[31] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[31] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(0, 2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                        } else {
                            attackScores[24] += 100;
                            attackScores[22] += 100;
                            attackScores[0] += 100;
                            attackScores[6] += 100;
                            attackScores[3] += 100;
                            attackScores[8] += 100;
                            attackScores[18] += 100;
                            attackScores[34] += 100;
                            attackScores[27] += 100;
                            attackScores[32] += 100;
                            attackScores[16] += 100;
                            attackScores[11] += 100;
                        }
                    } else {
                        attackScores[24] += 25;
                        attackScores[22] += 25;
                        attackScores[0] += 25;
                        attackScores[6] += 25;
                        attackScores[3] += 25;
                        attackScores[8] += 25;
                        attackScores[18] += 25;
                        attackScores[34] += 25;
                        attackScores[27] += 25;
                        attackScores[32] += 25;
                        attackScores[16] += 25;
                        attackScores[11] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[6] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[6] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[6] += 50;
                    attackScores[3] += 50;
                    attackScores[8] += 50;
                    attackScores[18] += 50;
                    attackScores[34] += 50;
                    attackScores[27] += 50;
                    attackScores[32] += 50;
                    attackScores[16] += 50;
                    attackScores[11] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[6] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[6] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(2, 0);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[0] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                        } else {
                            attackScores[24] += 100;
                            attackScores[0] += 100;
                            attackScores[23] += 100;
                            attackScores[7] += 100;
                            attackScores[19] += 100;
                            attackScores[35] += 100;
                            attackScores[28] += 100;
                            attackScores[36] += 100;
                            attackScores[20] += 100;
                            attackScores[8] += 100;
                            attackScores[4] += 100;
                            attackScores[12] += 100;
                        }
                    } else {
                        attackScores[24] += 25;
                        attackScores[0] += 25;
                        attackScores[23] += 25;
                        attackScores[7] += 25;
                        attackScores[19] += 25;
                        attackScores[35] += 25;
                        attackScores[28] += 25;
                        attackScores[36] += 25;
                        attackScores[20] += 25;
                        attackScores[8] += 25;
                        attackScores[4] += 25;
                        attackScores[12] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[7] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[7] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[7] += 50;
                    attackScores[19] += 50;
                    attackScores[35] += 50;
                    attackScores[28] += 50;
                    attackScores[36] += 50;
                    attackScores[20] += 50;
                    attackScores[8] += 50;
                    attackScores[4] += 50;
                    attackScores[12] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[7] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[7] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-2, -1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                        } else {
                            attackScores[2] += 100;
                            attackScores[14] += 100;
                            attackScores[21] += 100;
                            attackScores[15] += 100;
                            attackScores[5] += 100;
                            attackScores[1] += 100;
                            attackScores[9] += 100;
                            attackScores[25] += 100;
                            attackScores[29] += 100;
                            attackScores[13] += 100;
                        }
                    } else {
                        attackScores[2] += 25;
                        attackScores[14] += 25;
                        attackScores[21] += 25;
                        attackScores[15] += 25;
                        attackScores[5] += 25;
                        attackScores[1] += 25;
                        attackScores[9] += 25;
                        attackScores[25] += 25;
                        attackScores[29] += 25;
                        attackScores[13] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[21] += 50;
                    attackScores[15] += 50;
                    attackScores[5] += 50;
                    attackScores[1] += 50;
                    attackScores[9] += 50;
                    attackScores[25] += 50;
                    attackScores[29] += 50;
                    attackScores[13] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-2, 1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                        } else {
                            attackScores[3] += 100;
                            attackScores[13] += 100;
                            attackScores[25] += 100;
                            attackScores[9] += 100;
                            attackScores[1] += 100;
                            attackScores[6] += 100;
                            attackScores[16] += 100;
                            attackScores[22] += 100;
                            attackScores[30] += 100;
                            attackScores[14] += 100;
                        }
                    } else {
                        attackScores[3] += 25;
                        attackScores[13] += 25;
                        attackScores[25] += 25;
                        attackScores[9] += 25;
                        attackScores[1] += 25;
                        attackScores[6] += 25;
                        attackScores[16] += 25;
                        attackScores[22] += 25;
                        attackScores[30] += 25;
                        attackScores[14] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[25] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[25] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[25] += 50;
                    attackScores[9] += 50;
                    attackScores[1] += 50;
                    attackScores[6] += 50;
                    attackScores[16] += 50;
                    attackScores[22] += 50;
                    attackScores[30] += 50;
                    attackScores[14] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[25] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[25] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-1, -2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                        } else {
                            attackScores[17] += 100;
                            attackScores[1] += 100;
                            attackScores[31] += 100;
                            attackScores[26] += 100;
                            attackScores[10] += 100;
                            attackScores[2] += 100;
                            attackScores[5] += 100;
                            attackScores[13] += 100;
                            attackScores[21] += 100;
                            attackScores[15] += 100;
                        }
                    } else {
                        attackScores[17] += 25;
                        attackScores[1] += 25;
                        attackScores[31] += 25;
                        attackScores[26] += 25;
                        attackScores[10] += 25;
                        attackScores[2] += 25;
                        attackScores[5] += 25;
                        attackScores[13] += 25;
                        attackScores[21] += 25;
                        attackScores[15] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[31] += 50;
                    attackScores[26] += 50;
                    attackScores[10] += 50;
                    attackScores[2] += 50;
                    attackScores[5] += 50;
                    attackScores[13] += 50;
                    attackScores[21] += 50;
                    attackScores[15] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-1, 2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                        } else {
                            attackScores[18] += 100;
                            attackScores[1] += 100;
                            attackScores[14] += 100;
                            attackScores[6] += 100;
                            attackScores[3] += 100;
                            attackScores[11] += 100;
                            attackScores[27] += 100;
                            attackScores[32] += 100;
                            attackScores[22] += 100;
                            attackScores[16] += 100;
                        }
                    } else {
                        attackScores[18] += 25;
                        attackScores[1] += 25;
                        attackScores[14] += 25;
                        attackScores[6] += 25;
                        attackScores[3] += 25;
                        attackScores[11] += 25;
                        attackScores[27] += 25;
                        attackScores[32] += 25;
                        attackScores[22] += 25;
                        attackScores[16] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[14] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[14] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[14] += 50;
                    attackScores[6] += 50;
                    attackScores[3] += 50;
                    attackScores[11] += 50;
                    attackScores[27] += 50;
                    attackScores[32] += 50;
                    attackScores[22] += 50;
                    attackScores[16] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[14] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[14] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(1, -2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                        } else {
                            attackScores[4] += 100;
                            attackScores[15] += 100;
                            attackScores[26] += 100;
                            attackScores[33] += 100;
                            attackScores[23] += 100;
                            attackScores[19] += 100;
                            attackScores[7] += 100;
                            attackScores[2] += 100;
                            attackScores[10] += 100;
                            attackScores[17] += 100;
                        }
                    } else {
                        attackScores[4] += 25;
                        attackScores[15] += 25;
                        attackScores[26] += 25;
                        attackScores[33] += 25;
                        attackScores[23] += 25;
                        attackScores[19] += 25;
                        attackScores[7] += 25;
                        attackScores[2] += 25;
                        attackScores[10] += 25;
                        attackScores[17] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[26] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[26] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[26] += 50;
                    attackScores[33] += 50;
                    attackScores[23] += 50;
                    attackScores[19] += 50;
                    attackScores[7] += 50;
                    attackScores[2] += 50;
                    attackScores[10] += 50;
                    attackScores[17] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[26] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[26] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(1, 2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                        } else {
                            attackScores[16] += 100;
                            attackScores[4] += 100;
                            attackScores[3] += 100;
                            attackScores[8] += 100;
                            attackScores[20] += 100;
                            attackScores[24] += 100;
                            attackScores[34] += 100;
                            attackScores[27] += 100;
                            attackScores[11] += 100;
                            attackScores[18] += 100;
                        }
                    } else {
                        attackScores[16] += 25;
                        attackScores[4] += 25;
                        attackScores[3] += 25;
                        attackScores[8] += 25;
                        attackScores[20] += 25;
                        attackScores[24] += 25;
                        attackScores[34] += 25;
                        attackScores[27] += 25;
                        attackScores[11] += 25;
                        attackScores[18] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[3] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[3] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[3] += 50;
                    attackScores[8] += 50;
                    attackScores[20] += 50;
                    attackScores[24] += 50;
                    attackScores[34] += 50;
                    attackScores[27] += 50;
                    attackScores[11] += 50;
                    attackScores[18] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[3] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[3] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(2, -1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                        } else {
                            attackScores[20] += 100;
                            attackScores[2] += 100;
                            attackScores[17] += 100;
                            attackScores[23] += 100;
                            attackScores[35] += 100;
                            attackScores[28] += 100;
                            attackScores[12] += 100;
                            attackScores[4] += 100;
                            attackScores[7] += 100;
                            attackScores[19] += 100;
                        }
                    } else {
                        attackScores[20] += 25;
                        attackScores[2] += 25;
                        attackScores[17] += 25;
                        attackScores[23] += 25;
                        attackScores[35] += 25;
                        attackScores[28] += 25;
                        attackScores[12] += 25;
                        attackScores[4] += 25;
                        attackScores[7] += 25;
                        attackScores[19] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[17] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[17] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[17] += 50;
                    attackScores[23] += 50;
                    attackScores[35] += 50;
                    attackScores[28] += 50;
                    attackScores[12] += 50;
                    attackScores[4] += 50;
                    attackScores[7] += 50;
                    attackScores[19] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[17] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[17] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(2, 1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                        } else {
                            attackScores[3] += 100;
                            attackScores[19] += 100;
                            attackScores[4] += 100;
                            attackScores[12] += 100;
                            attackScores[28] += 100;
                            attackScores[36] += 100;
                            attackScores[24] += 100;
                            attackScores[18] += 100;
                            attackScores[8] += 100;
                            attackScores[20] += 100;
                        }
                    } else {
                        attackScores[3] += 25;
                        attackScores[19] += 25;
                        attackScores[4] += 25;
                        attackScores[12] += 25;
                        attackScores[28] += 25;
                        attackScores[36] += 25;
                        attackScores[24] += 25;
                        attackScores[18] += 25;
                        attackScores[8] += 25;
                        attackScores[20] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[4] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[4] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[4] += 50;
                    attackScores[12] += 50;
                    attackScores[28] += 50;
                    attackScores[36] += 50;
                    attackScores[24] += 50;
                    attackScores[18] += 50;
                    attackScores[8] += 50;
                    attackScores[20] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[4] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[4] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-2, -2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                        } else {
                            attackScores[10] += 100;
                            attackScores[9] += 100;
                            attackScores[31] += 100;
                            attackScores[15] += 100;
                            attackScores[5] += 100;
                            attackScores[13] += 100;
                            attackScores[29] += 100;
                            attackScores[21] += 100;
                        }
                    } else {
                        attackScores[10] += 25;
                        attackScores[9] += 25;
                        attackScores[31] += 25;
                        attackScores[15] += 25;
                        attackScores[5] += 25;
                        attackScores[13] += 25;
                        attackScores[29] += 25;
                        attackScores[21] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[31] += 50;
                    attackScores[15] += 50;
                    attackScores[5] += 50;
                    attackScores[13] += 50;
                    attackScores[29] += 50;
                    attackScores[21] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-2, 2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                        } else {
                            attackScores[11] += 100;
                            attackScores[9] += 100;
                            attackScores[30] += 100;
                            attackScores[14] += 100;
                            attackScores[6] += 100;
                            attackScores[16] += 100;
                            attackScores[32] += 100;
                            attackScores[22] += 100;
                        }
                    } else {
                        attackScores[11] += 25;
                        attackScores[9] += 25;
                        attackScores[30] += 25;
                        attackScores[14] += 25;
                        attackScores[6] += 25;
                        attackScores[16] += 25;
                        attackScores[32] += 25;
                        attackScores[22] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[30] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[30] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[30] += 50;
                    attackScores[14] += 50;
                    attackScores[6] += 50;
                    attackScores[16] += 50;
                    attackScores[32] += 50;
                    attackScores[22] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[30] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[30] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(2, -2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                        } else {
                            attackScores[12] += 100;
                            attackScores[10] += 100;
                            attackScores[33] += 100;
                            attackScores[35] += 100;
                            attackScores[19] += 100;
                            attackScores[7] += 100;
                            attackScores[17] += 100;
                            attackScores[23] += 100;
                        }
                    } else {
                        attackScores[12] += 25;
                        attackScores[10] += 25;
                        attackScores[33] += 25;
                        attackScores[35] += 25;
                        attackScores[19] += 25;
                        attackScores[7] += 25;
                        attackScores[17] += 25;
                        attackScores[23] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[33] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[33] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[33] += 50;
                    attackScores[35] += 50;
                    attackScores[19] += 50;
                    attackScores[7] += 50;
                    attackScores[17] += 50;
                    attackScores[23] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[33] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[33] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(2, 2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[24] += G.paintPerChips() * 200;
                        } else {
                            attackScores[11] += 100;
                            attackScores[12] += 100;
                            attackScores[8] += 100;
                            attackScores[20] += 100;
                            attackScores[36] += 100;
                            attackScores[34] += 100;
                            attackScores[18] += 100;
                            attackScores[24] += 100;
                        }
                    } else {
                        attackScores[11] += 25;
                        attackScores[12] += 25;
                        attackScores[8] += 25;
                        attackScores[20] += 25;
                        attackScores[36] += 25;
                        attackScores[34] += 25;
                        attackScores[18] += 25;
                        attackScores[24] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[8] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[8] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[8] += 50;
                    attackScores[20] += 50;
                    attackScores[36] += 50;
                    attackScores[34] += 50;
                    attackScores[18] += 50;
                    attackScores[24] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[8] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[8] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-3, 0);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[1] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                        } else {
                            attackScores[1] += 100;
                            attackScores[29] += 100;
                            attackScores[13] += 100;
                            attackScores[9] += 100;
                            attackScores[14] += 100;
                            attackScores[30] += 100;
                            attackScores[25] += 100;
                        }
                    } else {
                        attackScores[1] += 25;
                        attackScores[29] += 25;
                        attackScores[13] += 25;
                        attackScores[9] += 25;
                        attackScores[14] += 25;
                        attackScores[30] += 25;
                        attackScores[25] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[29] += 50;
                    attackScores[13] += 50;
                    attackScores[9] += 50;
                    attackScores[14] += 50;
                    attackScores[30] += 50;
                    attackScores[25] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(0, -3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[2] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                        } else {
                            attackScores[2] += 100;
                            attackScores[33] += 100;
                            attackScores[17] += 100;
                            attackScores[10] += 100;
                            attackScores[15] += 100;
                            attackScores[31] += 100;
                            attackScores[26] += 100;
                        }
                    } else {
                        attackScores[2] += 25;
                        attackScores[33] += 25;
                        attackScores[17] += 25;
                        attackScores[10] += 25;
                        attackScores[15] += 25;
                        attackScores[31] += 25;
                        attackScores[26] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[33] += 50;
                    attackScores[17] += 50;
                    attackScores[10] += 50;
                    attackScores[15] += 50;
                    attackScores[31] += 50;
                    attackScores[26] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(0, 3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[3] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                        } else {
                            attackScores[3] += 100;
                            attackScores[16] += 100;
                            attackScores[11] += 100;
                            attackScores[18] += 100;
                            attackScores[34] += 100;
                            attackScores[32] += 100;
                            attackScores[27] += 100;
                        }
                    } else {
                        attackScores[3] += 25;
                        attackScores[16] += 25;
                        attackScores[11] += 25;
                        attackScores[18] += 25;
                        attackScores[34] += 25;
                        attackScores[32] += 25;
                        attackScores[27] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[16] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[16] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[16] += 50;
                    attackScores[11] += 50;
                    attackScores[18] += 50;
                    attackScores[34] += 50;
                    attackScores[32] += 50;
                    attackScores[27] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[16] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[16] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(3, 0);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[4] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                        } else {
                            attackScores[4] += 100;
                            attackScores[19] += 100;
                            attackScores[35] += 100;
                            attackScores[36] += 100;
                            attackScores[20] += 100;
                            attackScores[12] += 100;
                            attackScores[28] += 100;
                        }
                    } else {
                        attackScores[4] += 25;
                        attackScores[19] += 25;
                        attackScores[35] += 25;
                        attackScores[36] += 25;
                        attackScores[20] += 25;
                        attackScores[12] += 25;
                        attackScores[28] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[19] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[19] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[19] += 50;
                    attackScores[35] += 50;
                    attackScores[36] += 50;
                    attackScores[20] += 50;
                    attackScores[12] += 50;
                    attackScores[28] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[19] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[19] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-3, -1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                        } else {
                            attackScores[5] += 100;
                            attackScores[30] += 100;
                            attackScores[21] += 100;
                            attackScores[13] += 100;
                            attackScores[9] += 100;
                            attackScores[25] += 100;
                            attackScores[29] += 100;
                        }
                    } else {
                        attackScores[5] += 25;
                        attackScores[30] += 25;
                        attackScores[21] += 25;
                        attackScores[13] += 25;
                        attackScores[9] += 25;
                        attackScores[25] += 25;
                        attackScores[29] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[21] += 50;
                    attackScores[13] += 50;
                    attackScores[9] += 50;
                    attackScores[25] += 50;
                    attackScores[29] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-3, 1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                        } else {
                            attackScores[6] += 100;
                            attackScores[29] += 100;
                            attackScores[25] += 100;
                            attackScores[9] += 100;
                            attackScores[14] += 100;
                            attackScores[22] += 100;
                            attackScores[30] += 100;
                        }
                    } else {
                        attackScores[6] += 25;
                        attackScores[29] += 25;
                        attackScores[25] += 25;
                        attackScores[9] += 25;
                        attackScores[14] += 25;
                        attackScores[22] += 25;
                        attackScores[30] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[25] += 50;
                    attackScores[9] += 50;
                    attackScores[14] += 50;
                    attackScores[22] += 50;
                    attackScores[30] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-1, -3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[33] += G.paintPerChips() * 200;
                            attackScores[5] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                        } else {
                            attackScores[33] += 100;
                            attackScores[5] += 100;
                            attackScores[26] += 100;
                            attackScores[10] += 100;
                            attackScores[15] += 100;
                            attackScores[21] += 100;
                            attackScores[31] += 100;
                        }
                    } else {
                        attackScores[33] += 25;
                        attackScores[5] += 25;
                        attackScores[26] += 25;
                        attackScores[10] += 25;
                        attackScores[15] += 25;
                        attackScores[21] += 25;
                        attackScores[31] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[26] += 50;
                    attackScores[10] += 50;
                    attackScores[15] += 50;
                    attackScores[21] += 50;
                    attackScores[31] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-1, 3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[34] += G.paintPerChips() * 200;
                            attackScores[6] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                        } else {
                            attackScores[34] += 100;
                            attackScores[6] += 100;
                            attackScores[22] += 100;
                            attackScores[16] += 100;
                            attackScores[11] += 100;
                            attackScores[27] += 100;
                            attackScores[32] += 100;
                        }
                    } else {
                        attackScores[34] += 25;
                        attackScores[6] += 25;
                        attackScores[22] += 25;
                        attackScores[16] += 25;
                        attackScores[11] += 25;
                        attackScores[27] += 25;
                        attackScores[32] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[22] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[22] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[22] += 50;
                    attackScores[16] += 50;
                    attackScores[11] += 50;
                    attackScores[27] += 50;
                    attackScores[32] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[22] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[22] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(1, -3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                        } else {
                            attackScores[7] += 100;
                            attackScores[31] += 100;
                            attackScores[23] += 100;
                            attackScores[17] += 100;
                            attackScores[10] += 100;
                            attackScores[26] += 100;
                            attackScores[33] += 100;
                        }
                    } else {
                        attackScores[7] += 25;
                        attackScores[31] += 25;
                        attackScores[23] += 25;
                        attackScores[17] += 25;
                        attackScores[10] += 25;
                        attackScores[26] += 25;
                        attackScores[33] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[23] += 50;
                    attackScores[17] += 50;
                    attackScores[10] += 50;
                    attackScores[26] += 50;
                    attackScores[33] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(1, 3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[32] += G.paintPerChips() * 200;
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                        } else {
                            attackScores[32] += 100;
                            attackScores[8] += 100;
                            attackScores[11] += 100;
                            attackScores[18] += 100;
                            attackScores[24] += 100;
                            attackScores[27] += 100;
                            attackScores[34] += 100;
                        }
                    } else {
                        attackScores[32] += 25;
                        attackScores[8] += 25;
                        attackScores[11] += 25;
                        attackScores[18] += 25;
                        attackScores[24] += 25;
                        attackScores[27] += 25;
                        attackScores[34] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[11] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[11] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[11] += 50;
                    attackScores[18] += 50;
                    attackScores[24] += 50;
                    attackScores[27] += 50;
                    attackScores[34] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[11] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[11] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(3, -1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[36] += G.paintPerChips() * 200;
                            attackScores[7] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                        } else {
                            attackScores[36] += 100;
                            attackScores[7] += 100;
                            attackScores[23] += 100;
                            attackScores[28] += 100;
                            attackScores[12] += 100;
                            attackScores[19] += 100;
                            attackScores[35] += 100;
                        }
                    } else {
                        attackScores[36] += 25;
                        attackScores[7] += 25;
                        attackScores[23] += 25;
                        attackScores[28] += 25;
                        attackScores[12] += 25;
                        attackScores[19] += 25;
                        attackScores[35] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[23] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[23] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[23] += 50;
                    attackScores[28] += 50;
                    attackScores[12] += 50;
                    attackScores[19] += 50;
                    attackScores[35] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[23] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[23] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(3, 1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[8] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                        } else {
                            attackScores[8] += 100;
                            attackScores[35] += 100;
                            attackScores[12] += 100;
                            attackScores[28] += 100;
                            attackScores[24] += 100;
                            attackScores[20] += 100;
                            attackScores[36] += 100;
                        }
                    } else {
                        attackScores[8] += 25;
                        attackScores[35] += 25;
                        attackScores[12] += 25;
                        attackScores[28] += 25;
                        attackScores[24] += 25;
                        attackScores[20] += 25;
                        attackScores[36] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[12] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[12] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[12] += 50;
                    attackScores[28] += 50;
                    attackScores[24] += 50;
                    attackScores[20] += 50;
                    attackScores[36] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[12] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[12] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-3, -2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                        } else {
                            attackScores[15] += 100;
                            attackScores[25] += 100;
                            attackScores[21] += 100;
                            attackScores[13] += 100;
                            attackScores[29] += 100;
                        }
                    } else {
                        attackScores[15] += 25;
                        attackScores[25] += 25;
                        attackScores[21] += 25;
                        attackScores[13] += 25;
                        attackScores[29] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[21] += 50;
                    attackScores[13] += 50;
                    attackScores[29] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-3, 2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                        } else {
                            attackScores[16] += 100;
                            attackScores[25] += 100;
                            attackScores[30] += 100;
                            attackScores[14] += 100;
                            attackScores[22] += 100;
                        }
                    } else {
                        attackScores[16] += 25;
                        attackScores[25] += 25;
                        attackScores[30] += 25;
                        attackScores[14] += 25;
                        attackScores[22] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[30] += 50;
                    attackScores[14] += 50;
                    attackScores[22] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-2, -3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                        } else {
                            attackScores[26] += 100;
                            attackScores[13] += 100;
                            attackScores[31] += 100;
                            attackScores[15] += 100;
                            attackScores[21] += 100;
                        }
                    } else {
                        attackScores[26] += 25;
                        attackScores[13] += 25;
                        attackScores[31] += 25;
                        attackScores[15] += 25;
                        attackScores[21] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[31] += 50;
                    attackScores[15] += 50;
                    attackScores[21] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-2, 3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                        } else {
                            attackScores[27] += 100;
                            attackScores[14] += 100;
                            attackScores[22] += 100;
                            attackScores[16] += 100;
                            attackScores[32] += 100;
                        }
                    } else {
                        attackScores[27] += 25;
                        attackScores[14] += 25;
                        attackScores[22] += 25;
                        attackScores[16] += 25;
                        attackScores[32] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[22] += 50;
                    attackScores[16] += 50;
                    attackScores[32] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(2, -3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                        } else {
                            attackScores[19] += 100;
                            attackScores[26] += 100;
                            attackScores[23] += 100;
                            attackScores[17] += 100;
                            attackScores[33] += 100;
                        }
                    } else {
                        attackScores[19] += 25;
                        attackScores[26] += 25;
                        attackScores[23] += 25;
                        attackScores[17] += 25;
                        attackScores[33] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[23] += 50;
                    attackScores[17] += 50;
                    attackScores[33] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(2, 3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                        } else {
                            attackScores[27] += 100;
                            attackScores[20] += 100;
                            attackScores[18] += 100;
                            attackScores[24] += 100;
                            attackScores[34] += 100;
                        }
                    } else {
                        attackScores[27] += 25;
                        attackScores[20] += 25;
                        attackScores[18] += 25;
                        attackScores[24] += 25;
                        attackScores[34] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[18] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[18] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[18] += 50;
                    attackScores[24] += 50;
                    attackScores[34] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[18] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[18] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(3, -2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                        } else {
                            attackScores[28] += 100;
                            attackScores[17] += 100;
                            attackScores[35] += 100;
                            attackScores[19] += 100;
                            attackScores[23] += 100;
                        }
                    } else {
                        attackScores[28] += 25;
                        attackScores[17] += 25;
                        attackScores[35] += 25;
                        attackScores[19] += 25;
                        attackScores[23] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[35] += 50;
                    attackScores[19] += 50;
                    attackScores[23] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(3, 2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                            attackScores[24] += G.paintPerChips() * 200;
                        } else {
                            attackScores[18] += 100;
                            attackScores[28] += 100;
                            attackScores[20] += 100;
                            attackScores[36] += 100;
                            attackScores[24] += 100;
                        }
                    } else {
                        attackScores[18] += 25;
                        attackScores[28] += 25;
                        attackScores[20] += 25;
                        attackScores[36] += 25;
                        attackScores[24] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[20] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[20] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[20] += 50;
                    attackScores[36] += 50;
                    attackScores[24] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[20] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[20] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-4, 0);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[9] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                        } else {
                            attackScores[9] += 100;
                            attackScores[29] += 100;
                            attackScores[25] += 100;
                            attackScores[30] += 100;
                        }
                    } else {
                        attackScores[9] += 25;
                        attackScores[29] += 25;
                        attackScores[25] += 25;
                        attackScores[30] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[29] += 50;
                    attackScores[25] += 50;
                    attackScores[30] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(0, -4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[10] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                        } else {
                            attackScores[10] += 100;
                            attackScores[33] += 100;
                            attackScores[26] += 100;
                            attackScores[31] += 100;
                        }
                    } else {
                        attackScores[10] += 25;
                        attackScores[33] += 25;
                        attackScores[26] += 25;
                        attackScores[31] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[33] += 50;
                    attackScores[26] += 50;
                    attackScores[31] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(0, 4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[11] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                        } else {
                            attackScores[11] += 100;
                            attackScores[32] += 100;
                            attackScores[27] += 100;
                            attackScores[34] += 100;
                        }
                    } else {
                        attackScores[11] += 25;
                        attackScores[32] += 25;
                        attackScores[27] += 25;
                        attackScores[34] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[32] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[32] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[32] += 50;
                    attackScores[27] += 50;
                    attackScores[34] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[32] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[32] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(4, 0);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[12] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                        } else {
                            attackScores[12] += 100;
                            attackScores[35] += 100;
                            attackScores[36] += 100;
                            attackScores[28] += 100;
                        }
                    } else {
                        attackScores[12] += 25;
                        attackScores[35] += 25;
                        attackScores[36] += 25;
                        attackScores[28] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[35] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[35] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[35] += 50;
                    attackScores[36] += 50;
                    attackScores[28] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[35] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[35] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-4, -1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[13] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                        } else {
                            attackScores[13] += 100;
                            attackScores[29] += 100;
                            attackScores[25] += 100;
                        }
                    } else {
                        attackScores[13] += 25;
                        attackScores[29] += 25;
                        attackScores[25] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[29] += 50;
                    attackScores[25] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-4, 1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[14] += G.paintPerChips() * 200;
                            attackScores[25] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                        } else {
                            attackScores[14] += 100;
                            attackScores[25] += 100;
                            attackScores[30] += 100;
                        }
                    } else {
                        attackScores[14] += 25;
                        attackScores[25] += 25;
                        attackScores[30] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[25] += 50;
                    attackScores[30] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-1, -4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[15] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                        } else {
                            attackScores[15] += 100;
                            attackScores[26] += 100;
                            attackScores[31] += 100;
                        }
                    } else {
                        attackScores[15] += 25;
                        attackScores[26] += 25;
                        attackScores[31] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[26] += 50;
                    attackScores[31] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-1, 4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[16] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                        } else {
                            attackScores[16] += 100;
                            attackScores[32] += 100;
                            attackScores[27] += 100;
                        }
                    } else {
                        attackScores[16] += 25;
                        attackScores[32] += 25;
                        attackScores[27] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[32] += 50;
                    attackScores[27] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(1, -4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[17] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                            attackScores[26] += G.paintPerChips() * 200;
                        } else {
                            attackScores[17] += 100;
                            attackScores[33] += 100;
                            attackScores[26] += 100;
                        }
                    } else {
                        attackScores[17] += 25;
                        attackScores[33] += 25;
                        attackScores[26] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[33] += 50;
                    attackScores[26] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(1, 4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[18] += G.paintPerChips() * 200;
                            attackScores[27] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                        } else {
                            attackScores[18] += 100;
                            attackScores[27] += 100;
                            attackScores[34] += 100;
                        }
                    } else {
                        attackScores[18] += 25;
                        attackScores[27] += 25;
                        attackScores[34] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[27] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[27] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[27] += 50;
                    attackScores[34] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[27] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[27] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(4, -1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[19] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                        } else {
                            attackScores[19] += 100;
                            attackScores[28] += 100;
                            attackScores[35] += 100;
                        }
                    } else {
                        attackScores[19] += 25;
                        attackScores[28] += 25;
                        attackScores[35] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[28] += 50;
                    attackScores[35] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(4, 1);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[20] += G.paintPerChips() * 200;
                            attackScores[28] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                        } else {
                            attackScores[20] += 100;
                            attackScores[28] += 100;
                            attackScores[36] += 100;
                        }
                    } else {
                        attackScores[20] += 25;
                        attackScores[28] += 25;
                        attackScores[36] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[28] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[28] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[28] += 50;
                    attackScores[36] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[28] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[28] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-3, -3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[31] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                            attackScores[21] += G.paintPerChips() * 200;
                        } else {
                            attackScores[31] += 100;
                            attackScores[29] += 100;
                            attackScores[21] += 100;
                        }
                    } else {
                        attackScores[31] += 25;
                        attackScores[29] += 25;
                        attackScores[21] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[21] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-3, 3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[32] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                            attackScores[22] += G.paintPerChips() * 200;
                        } else {
                            attackScores[32] += 100;
                            attackScores[30] += 100;
                            attackScores[22] += 100;
                        }
                    } else {
                        attackScores[32] += 25;
                        attackScores[30] += 25;
                        attackScores[22] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[22] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(3, -3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[35] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                            attackScores[23] += G.paintPerChips() * 200;
                        } else {
                            attackScores[35] += 100;
                            attackScores[33] += 100;
                            attackScores[23] += 100;
                        }
                    } else {
                        attackScores[35] += 25;
                        attackScores[33] += 25;
                        attackScores[23] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[23] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(3, 3);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[34] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                            attackScores[24] += G.paintPerChips() * 200;
                        } else {
                            attackScores[34] += 100;
                            attackScores[36] += 100;
                            attackScores[24] += 100;
                        }
                    } else {
                        attackScores[34] += 25;
                        attackScores[36] += 25;
                        attackScores[24] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[24] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[24] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[24] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[24] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[24] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(-4, -2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[29] += G.paintPerChips() * 200;
                        } else {
                            attackScores[21] += 100;
                            attackScores[29] += 100;
                        }
                    } else {
                        attackScores[21] += 25;
                        attackScores[29] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[29] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-4, 2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[30] += G.paintPerChips() * 200;
                        } else {
                            attackScores[22] += 100;
                            attackScores[30] += 100;
                        }
                    } else {
                        attackScores[22] += 25;
                        attackScores[30] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[30] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-2, -4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[21] += G.paintPerChips() * 200;
                            attackScores[31] += G.paintPerChips() * 200;
                        } else {
                            attackScores[21] += 100;
                            attackScores[31] += 100;
                        }
                    } else {
                        attackScores[21] += 25;
                        attackScores[31] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[31] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(-2, 4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[22] += G.paintPerChips() * 200;
                            attackScores[32] += G.paintPerChips() * 200;
                        } else {
                            attackScores[22] += 100;
                            attackScores[32] += 100;
                        }
                    } else {
                        attackScores[22] += 25;
                        attackScores[32] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[32] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(2, -4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[33] += G.paintPerChips() * 200;
                        } else {
                            attackScores[23] += 100;
                            attackScores[33] += 100;
                        }
                    } else {
                        attackScores[23] += 25;
                        attackScores[33] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[33] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(2, 4);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[34] += G.paintPerChips() * 200;
                        } else {
                            attackScores[24] += 100;
                            attackScores[34] += 100;
                        }
                    } else {
                        attackScores[24] += 25;
                        attackScores[34] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[34] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[34] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[34] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[34] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[34] += 50;
                    }
                }
            }
        }
        loc = G.me.translate(4, -2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[23] += G.paintPerChips() * 200;
                            attackScores[35] += G.paintPerChips() * 200;
                        } else {
                            attackScores[23] += 100;
                            attackScores[35] += 100;
                        }
                    } else {
                        attackScores[23] += 25;
                        attackScores[35] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[35] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                    }
                }
            }
        }
        loc = G.me.translate(4, 2);
        if (G.rc.onTheMap(loc)) {
            MapInfo info = G.rc.senseMapInfo(loc);
            if (!info.isWall()) {
                if (info.getPaint() == PaintType.EMPTY) {
                    if (info.hasRuin()) {
                        if (G.rc.canSenseRobotAtLocation(loc)
                                && G.rc.senseRobotAtLocation(loc).team == G.opponentTeam) {
                            attackScores[24] += G.paintPerChips() * 200;
                            attackScores[36] += G.paintPerChips() * 200;
                        } else {
                            attackScores[24] += 100;
                            attackScores[36] += 100;
                        }
                    } else {
                        attackScores[24] += 25;
                        attackScores[36] += 25;
                    }
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[36] += 25;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[36] += 25;
                    }
                } else if (info.getPaint().isEnemy()) {
                    attackScores[36] += 50;
                    if (G.opponentRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[36] += 50;
                    } else if (G.allyRobotsString.indexOf(loc.toString()) != -1) {
                        attackScores[36] += 50;
                    }
                }
            }
        }
    }

    public static void exploreAttackScores2() throws Exception {
        // dont delete this function its here in case the codegen for
        // exploreAttackScores gets too big
    }

    public static void exploreMoveScores() throws Exception {
        moveScores = Motion.defaultMicro.micro(
                Motion.bug2Helper(G.me, Motion.exploreRandomlyAggressiveLoc(), Motion.TOWARDS, 0, 0),
                Motion.exploreLoc);
    }

    public static void retreatMoveScores() throws Exception {
        moveScores = Motion.defaultMicro.micro(Motion.retreatDir(), Motion.retreatLoc);
    }
}
