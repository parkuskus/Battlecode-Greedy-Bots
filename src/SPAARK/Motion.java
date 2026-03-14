package SPAARK;

import battlecode.common.*;

public class Motion {
    public static final boolean ENABLE_EXPLORE_INDICATORS = true;

    public static int movementCooldown = 0;
    public static int lastMove = -1;

    public static final int TOWARDS = 0;
    public static final int AWAY = 1;
    public static final int AROUND = 2;
    public static final int NONE = 0;
    public static final int CLOCKWISE = 1;
    public static final int COUNTER_CLOCKWISE = -1;

    public static final int MAX_RETREAT_ROBOTS = 4;

    public static Direction lastDir = Direction.CENTER;
    public static Direction optimalDir = Direction.CENTER;
    public static int rotation = NONE;
    public static int circleDirection = CLOCKWISE;

    public static Direction lastRandomDir = Direction.CENTER;
    public static MapLocation lastRandomSpread;

    // common distance stuff
    public static int getManhattanDistance(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public static int getChebyshevDistance(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    public static MapLocation getClosest(MapLocation[] a) throws Exception {
        return getClosest(a, G.rc.getLocation());
    }

    public static MapLocation getClosest(MapLocation[] a, MapLocation me) throws Exception {
        /* Get closest MapLocation to me (Euclidean) */
        MapLocation closest = a[0];
        int distance = me.distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            if (me.distanceSquaredTo(loc) < distance) {
                closest = loc;
                distance = me.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    public static MapLocation getClosestPair(MapLocation[] a, MapLocation[] b) throws Exception {
        /* Get closest pair (Euclidean) */
        MapLocation closest = a[0];
        int distance = b[0].distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            for (int j = b.length; --j >= 0;) {
                MapLocation loc2 = b[i];
                if (loc2.distanceSquaredTo(loc) < distance) {
                    closest = loc;
                    distance = loc2.distanceSquaredTo(loc);
                }
            }
        }
        return closest;
    }

    public static MapLocation getFarthest(MapLocation[] a) throws Exception {
        /* Get farthest MapLocation to this robot (Euclidean) */
        return getFarthest(a, G.rc.getLocation());
    }

    public static MapLocation getFarthest(MapLocation[] a, MapLocation me) throws Exception {
        /* Get farthest MapLocation to me (Euclidean) */
        MapLocation closest = a[0];
        int distance = me.distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            if (me.distanceSquaredTo(loc) > distance) {
                closest = loc;
                distance = me.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    // basic random movement
    public static void moveRandomly() throws Exception {
        moveRandomly(defaultMicro);
    }

    public static void moveRandomly(Micro m) throws Exception {
        if (G.rc.isMovementReady()) {
            boolean stuck = true;
            for (int i = 8; --i >= 0;) {
                if (G.rc.canMove(G.DIRECTIONS[i])) {
                    stuck = false;
                }
            }
            if (stuck) {
                return;
            }
            // move in a random direction but minimize making useless moves back to where
            // you came from
            Direction direction = G.DIRECTIONS[Random.rand() & 7];
            if (direction == lastRandomDir.opposite() && G.rc.canMove(direction.opposite())) {
                direction = direction.opposite();
            }
            if (microMove(m.micro(direction, G.me.add(direction)))) {
                lastRandomDir = direction;
            }
        }
    }

    public static void spreadRandomly() throws Exception {
        spreadRandomly(defaultMicro);
    }

    public static void spreadRandomly(Micro m) throws Exception {
        boolean stuck = true;
        for (int i = G.DIRECTIONS.length; --i >= 0;) {
            if (G.rc.canMove(G.DIRECTIONS[i])) {
                stuck = false;
            }
        }
        if (stuck) {
            return;
        }
        if (G.rc.isMovementReady()) {
            MapLocation target = G.me;
            for (int i = G.allyRobots.length; --i >= 0;) {
                // ignore towers
                if (!G.allyRobots[i].type.isRobotType())
                    target = target.subtract(G.me.directionTo(G.allyRobots[i].getLocation()));
            }
            for (int i = 8; --i >= 0;) {
                if (!G.rc.canMove(G.DIRECTIONS[i])) {
                    target = target.subtract(G.DIRECTIONS[i]);
                }
            }
            if (target.equals(G.me)) {
                // just keep moving in the same direction as before if there's no robots nearby
                if (G.round % 3 == 0 || lastRandomSpread == null) {
                    moveRandomly(); // occasionally move randomly to avoid getting stuck
                } else if (Random.rand() % 20 == 0) {
                    // don't get stuck in corners
                    lastRandomSpread = G.me.add(G.DIRECTIONS[Random.rand() & 7]);
                    moveRandomly();
                } else {
                    // Direction direction = bug2Helper(me, lastRandomSpread, TOWARDS, 0, 0);
                    Direction direction = G.me.directionTo(target);
                    if (microMove(m.micro(direction, target))) {
                        lastRandomSpread = lastRandomSpread.add(direction);
                        lastRandomDir = direction;
                    } else {
                        moveRandomly();
                    }
                }
                lastDir = Direction.CENTER;
                optimalDir = Direction.CENTER;
            } else {
                if (lastDir == G.me.directionTo(target)) {
                    lastDir = Direction.CENTER;
                }
                Direction direction = bug2Helper(G.me, target, TOWARDS, 0, 0);
                if (microMove(m.micro(direction, target))) {
                    lastRandomSpread = target;
                    lastRandomDir = direction;
                }
            }
        }
    }

    public static MapLocation exploreLoc;

    public static void exploreRandomly() throws Exception {
        exploreRandomly(defaultMicro);
    }

    public static void exploreRandomly(Micro m) throws Exception {
        exploreLoc = exploreRandomlyLoc();
        if (G.rc.isMovementReady()) {
            bugnavTowards(exploreLoc, m);
            if (ENABLE_EXPLORE_INDICATORS)
                G.rc.setIndicatorLine(G.me, exploreLoc, 0, 255, 0);
        }
    }

    public static void exploreCorners(Micro m) throws Exception {
        MapLocation best = null;
        int bestDist = 1000000;
        int dist = G.me.distanceSquaredTo(new MapLocation(0, 0));
        String s = "";
        s += dist;
        s += " ";
        if (dist > 25 && dist < bestDist && (((POI.explored[0] >> 0) & 1) == 0)) {
            bestDist = dist;
            best = new MapLocation(0, 0);
        }
        dist = G.me.distanceSquaredTo(new MapLocation(0, G.mapHeight - 1));
        s += dist;
        s += " ";
        if (dist > 25 && dist < bestDist && (((POI.explored[G.mapHeight - 1] >> 0) & 1) == 0)) {
            bestDist = dist;
            best = new MapLocation(0, G.mapHeight - 1);
        }
        dist = G.me.distanceSquaredTo(new MapLocation(G.mapWidth - 1, 0));
        s += dist;
        s += " ";
        if (dist > 25 && dist < bestDist && (((POI.explored[0] >> G.mapWidth - 1) & 1) == 0)) {
            bestDist = dist;
            best = new MapLocation(G.mapWidth - 1, 0);
        }
        dist = G.me.distanceSquaredTo(new MapLocation(G.mapWidth - 1, G.mapHeight - 1));
        s += dist;
        s += " ";
        System.out.println(s);
        if (dist > 25 && dist < bestDist && ((((POI.explored[G.mapHeight - 1] >> G.mapWidth - 1)) & 1) == 0)) {
            bestDist = dist;
            best = new MapLocation(G.mapWidth - 1, G.mapHeight - 1);
        }
        if (best != null) {
            exploreLoc = best;
        }
        exploreRandomly(m);
    }

    public static int exploreTime = 0;

    public static final int SYMMETRY_EXPLORE_PERCENT = Integer.MAX_VALUE; // OPTNET_PARAM
    // used for soldiers at low hp, avoid exploring enemy towers
    public static boolean avoidSymmetryExplore = false;
    public static boolean exploreTowerCheck = false;

    public static MapLocation exploreRandomlyLoc() throws Exception {
        if (G.rc.isMovementReady()) {
            --exploreTime;
            if (exploreLoc != null) {
                if (G.rc.canSenseLocation(exploreLoc)) {
                    exploreLoc = null;
                } else if (exploreTime == 0) {
                    exploreLoc = null;
                } else if (Random.rand() % 35 == 0) {
                    exploreLoc = null;
                } else if (exploreTowerCheck) {
                    for (int i = POI.numberOfTowers; --i >= 0;) {
                        if (POI.towerTeams[i] != G.team) {
                            continue;
                        }
                        if (exploreLoc.isWithinDistanceSquared(POI.towerLocs[i], 20)) {
                            exploreLoc = null;
                            break;
                        }
                    }
                }
            }
            int numValidSymmetries = (POI.symmetry[0] ? 1 : 0) + (POI.symmetry[1] ? 1 : 0) + (POI.symmetry[2] ? 1 : 0);
            if (exploreLoc == null && !avoidSymmetryExplore && numValidSymmetries == 1
                    && Random.rand() >= SYMMETRY_EXPLORE_PERCENT) {
                int rand = Random.rand() % POI.numberOfTowers;
                search: for (int j = POI.numberOfTowers; --j >= 0;) {
                    int i = (j + rand) % POI.numberOfTowers;
                    // if (POI.towerTeams[i] == G.opponentTeam
                    // && ((POI.explored[POI.towerLocs[i].y] >> POI.explored[POI.towerLocs[i].x]) &
                    // 1) == 0) {
                    if (POI.towerTeams[i] == G.opponentTeam) {
                        exploreLoc = POI.towerLocs[i];
                        break;
                    }
                    if (POI.towerTeams[i] == G.team) {
                        int rand2 = Random.rand() % 3;
                        for (int j2 = 3; --j2 >= 0;) {
                            int i2 = (j2 + rand2) % 3;
                            if (POI.symmetry[i2]) {
                                MapLocation loc = POI.getOppositeMapLocation(POI.towerLocs[i], i2);
                                // if (((POI.explored[loc.y] >> POI.explored[loc.x]) & 1) == 0) {
                                exploreLoc = loc;
                                exploreTime = getChebyshevDistance(G.me, exploreLoc) + 20;
                                exploreTowerCheck = true;
                                break search;
                                // }
                            }
                        }
                    }
                }
            }
            if (exploreLoc == null) {
                int sum = G.mapArea;
                for (int i = G.mapHeight; --i >= 0;) {
                    sum -= Long.bitCount(POI.explored[i]);
                }
                // int a = Clock.getBytecodeNum();
                // for (int j = 10; --j >= 0;) {
                int rand = Random.rand() % sum;
                int cur = 0;
                for (int i = G.mapHeight; --i >= 0;) {
                    cur += G.mapWidth - Long.bitCount(POI.explored[i]);
                    if (cur > rand) {
                        rand -= cur - (G.mapWidth - Long.bitCount(POI.explored[i]));
                        int cur2 = 0;
                        for (int b = G.mapWidth; --b >= 0;) {
                            if (((POI.explored[i] >> b) & 1) == 0) {
                                if (++cur2 > rand) {
                                    // if (exploreLoc == null || getChebyshevDistance(G.me, exploreLoc) >
                                    // getChebyshevDistance(G.me, new MapLocation(b, i))) {
                                    exploreLoc = new MapLocation(b, i);
                                    // }
                                    exploreTime = getChebyshevDistance(G.me, exploreLoc) + 20;
                                    exploreTowerCheck = true;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
                // if (exploreLoc != null && G.allyRobots.length > 5) {
                // MapLocation otherBots = G.me;
                // for (int i = G.allyRobots.length; --i >= 0;) {
                // otherBots = otherBots.translate(G.allyRobots[i].location.x,
                // G.allyRobots[i].location.y);
                // }
                // if (((double) (otherBots.x * exploreLoc.x + otherBots.y * exploreLoc.y)) /
                // ((double) otherBots.distanceSquaredTo(new MapLocation(0, 0)) *
                // G.me.distanceSquaredTo(new MapLocation(0, 0))) > 0) {
                // exploreLoc = null;
                // }
                // }
            }
            if (exploreLoc == null){
                exploreLoc = new MapLocation(Random.rand() % G.mapWidth, Random.rand() % G.mapHeight);
            }
        }
        return exploreLoc;
    }

    public static MapLocation exploreRandomlyAggressiveLoc() throws Exception {
        // only use symmetryexplore
        if (G.rc.isMovementReady()) {
            --exploreTime;
            if (exploreLoc != null) {
                if (G.rc.canSenseLocation(exploreLoc)) {
                    exploreLoc = null;
                }
                if (exploreTime == 0) {
                    exploreLoc = null;
                }
                if (Random.rand() % 35 == 0) {
                    exploreLoc = null;
                }
            }
            if (exploreLoc == null) {
                int rand = Random.rand() % POI.numberOfTowers;
                search: for (int j = POI.numberOfTowers; --j >= 0;) {
                    int i = (j + rand) % POI.numberOfTowers;
                    // if (POI.towerTeams[i] == G.opponentTeam
                    // && ((POI.explored[POI.towerLocs[i].y] >> POI.explored[POI.towerLocs[i].x]) &
                    // 1) == 0) {
                    if (POI.towerTeams[i] == G.opponentTeam) {
                        exploreLoc = POI.towerLocs[i];
                        break;
                    }
                    if (POI.towerTeams[i] == G.team) {
                        int rand2 = Random.rand() % 3;
                        for (int j2 = 3; --j2 >= 0;) {
                            int i2 = (j2 + rand2) % 3;
                            if (POI.symmetry[i2]) {
                                MapLocation loc = POI.getOppositeMapLocation(POI.towerLocs[i], i2);
                                // if (((POI.explored[loc.y] >> POI.explored[loc.x]) & 1) == 0) {
                                exploreLoc = loc;
                                exploreTime = getChebyshevDistance(G.me, exploreLoc) + 20;
                                break search;
                                // }
                            }
                        }
                    }
                }
            }
            if (exploreLoc == null) {
                int sum = G.mapArea;
                for (int i = G.mapHeight; --i >= 0;) {
                    sum -= Long.bitCount(POI.explored[i]);
                }
                // int a = Clock.getBytecodeNum();
                // for (int j = 10; --j >= 0;) {
                int rand = Random.rand() % sum;
                int cur = 0;
                for (int i = G.mapHeight; --i >= 0;) {
                    cur += G.mapWidth - Long.bitCount(POI.explored[i]);
                    if (cur > rand) {
                        rand -= cur - (G.mapWidth - Long.bitCount(POI.explored[i]));
                        int cur2 = 0;
                        for (int b = G.mapWidth; --b >= 0;) {
                            if (((POI.explored[i] >> b) & 1) == 0) {
                                if (++cur2 > rand) {
                                    // if (exploreLoc == null || getChebyshevDistance(G.me, exploreLoc) >
                                    // getChebyshevDistance(G.me, new MapLocation(b, i))) {
                                    exploreLoc = new MapLocation(b, i);
                                    // }
                                    exploreTime = getChebyshevDistance(G.me, exploreLoc) + 20;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
            }
            if (exploreLoc == null){
                exploreLoc = new MapLocation(Random.rand() % G.mapWidth, Random.rand() % G.mapHeight);
            }
        }
        return exploreLoc;
    }

    // lastPaint stores how much paint has been lost to neutral/opponent territory
    // used to determine how much paint until retreating
    public static int lastPaint = 0;
    public static int paintLost = 0;

    // Retreat tower stores the id of the tower in the POI
    // if it is -1, that means no tower
    // if it is -2, that means oof only money tower or no towers found and time to
    // explore
    public static int retreatTower = -1;
    public static StringBuilder triedRetreatTowers = new StringBuilder();

    public static MapLocation retreatWaitingLoc = null;

    public static int paintNeededToStopRetreating;

    // retreat calculations
    public static final int RETREAT_PAINT_OFFSET = 30; // OPTNET_PARAM
    public static final double RETREAT_PAINT_RATIO = 0.25; // OPTNET_PARAM

    public static MapLocation[] retreatWaitingLocs = new MapLocation[] {
            new MapLocation(2, 2),
            new MapLocation(2, -2),
            new MapLocation(-2, 2),
            new MapLocation(-2, -2),
            new MapLocation(2, 0),
            new MapLocation(0, 2),
            new MapLocation(-2, 0),
            new MapLocation(0, -2),
    };

    public static int getRetreatPaint() throws Exception {
        if (G.allyRobots.length > 10) {
            return 0;
        }
        // if paint is less than getRetreatPaint, the robot may retreat
        int paint = Math.max(paintLost + RETREAT_PAINT_OFFSET,
                (int) ((double) G.rc.getType().paintCapacity * RETREAT_PAINT_RATIO));
        switch (G.rc.getType()) {
            case SOLDIER:
                return paint;
            case SPLASHER:
                if (G.mapArea > 1600 && G.rc.getNumberTowers() <= 4) {
                    return 50;
                }
                return paint;
            case MOPPER:
                return paint;
            default:
                return 0;
        }
    }

    public static void updateRetreatWaitingLoc() throws Exception {
        int ourWeight = -G.rc.getPaint();
        int robotsWithHigherWeight = 0;
        if (G.me.distanceSquaredTo(retreatLoc) == 4 || G.me.distanceSquaredTo(retreatLoc) == 8) {
            for (int i = 8; --i >= 0;) {
                MapLocation waitingLoc = retreatWaitingLocs[i].translate(retreatLoc.x, retreatLoc.y);
                if (waitingLoc.equals(G.me)) {
                    continue;
                }
                if (G.rc.canSenseLocation(waitingLoc) && G.rc.canSenseRobotAtLocation(waitingLoc)) {
                    RobotInfo r = G.rc.senseRobotAtLocation(waitingLoc);
                    int weight = -r.paintAmount;
                    if (weight > ourWeight) {
                        robotsWithHigherWeight++;
                        if (robotsWithHigherWeight >= MAX_RETREAT_ROBOTS) {
                            retreatTower = -1;
                            return;
                        }
                    }
                }
            }
            return;
        }
        retreatWaitingLoc = null;
        int bestWeight = 0;
        for (int i = 8; --i >= 0;) {
            MapLocation waitingLoc = retreatWaitingLocs[i].translate(retreatLoc.x, retreatLoc.y);
            int weight = -G.me.distanceSquaredTo(waitingLoc);
            if (G.rc.canSenseLocation(waitingLoc)) {
                if (G.rc.canSenseRobotAtLocation(waitingLoc)) {
                    RobotInfo r = G.rc.senseRobotAtLocation(waitingLoc);
                    int robotWeight = -r.paintAmount;
                    if (robotWeight > ourWeight) {
                        robotsWithHigherWeight++;
                        if (robotsWithHigherWeight >= MAX_RETREAT_ROBOTS) {
                            retreatTower = -1;
                            return;
                        }
                    }
                    continue;
                }
                PaintType paint = G.rc.senseMapInfo(waitingLoc).getPaint();
                if (paint.isEnemy()) {
                    weight -= 200;
                } else if (!paint.isAlly()) {
                    weight -= 200;
                }
            }
            if (retreatWaitingLoc == null || weight > bestWeight) {
                retreatWaitingLoc = waitingLoc;
                bestWeight = weight;
            }
        }
        if (retreatWaitingLoc == null) {
            retreatTower = -1;
        }
    }

    public static MapLocation retreatLoc = new MapLocation(-1, -1);

    public static void setRetreatLoc() throws Exception {
        // retreats to an ally tower
        // depends on which information needs to be transmitted and if tower has paint
        // if no paint towers found it should go to chip tower to update POI and find
        // paint tower to retreat to
        paintLost = 0;
        if (retreatTower >= 0) {
            // oopsies tower was replaced
            if (POI.towerTeams[retreatTower] != G.team) {
                retreatTower = -1;
            }
        }
        if (retreatTower >= 0) {
            // don't retreat to tower with lots of bots surrounding it
            MapLocation loc = POI.towerLocs[retreatTower];
            if (G.rc.canSenseRobotAtLocation(loc)) {
                RobotInfo robotInfo = G.rc.senseRobotAtLocation(loc);
                if (robotInfo.getType().getBaseType() != UnitType.LEVEL_ONE_PAINT_TOWER
                        && robotInfo.getPaintAmount() == 0) {
                    retreatTower = -1;
                } else {
                    updateRetreatWaitingLoc();
                }
            }
        }
        // if (retreatTower == -1) {
        // int best = -1;
        // while (best == -1) {
        // int bestWeight = Integer.MIN_VALUE;
        // // boolean hasCritical = false;
        // for (int i = POI.numberOfTowers; --i >= 0;) {
        // // if (POI.critical[i]) {
        // // hasCritical = true;
        // // }
        // if (POI.towerTeams[i] != G.team)
        // continue;
        // // this needs to change
        // boolean paint = POI.towerTypes[i] == UnitType.LEVEL_ONE_PAINT_TOWER;
        // // if (!paint) {
        // // // This is dumb but borks code for some reason
        // // continue;
        // // }
        // int weight = 0;
        // if (triedRetreatTowers.indexOf("" + (char) i) != -1) {
        // weight -= 1000;
        // }
        // int distance = Motion.getChebyshevDistance(G.me, POI.towerLocs[i]);
        // weight -= distance;
        // if (paint) {
        // weight += 100;
        // }
        // else if (G.rc.canSenseRobotAtLocation(POI.towerLocs[i]) &&
        // G.rc.senseRobotAtLocation(POI.towerLocs[i]).paintAmount > 0) {
        // weight += 100;
        // }
        // // if (!POI.critical[i]) {
        // // weight += 200;
        // // }

        // if (best == -1 || weight > bestWeight) {
        // best = i;
        // bestWeight = weight;
        // }
        // }
        // if (best == -1) {
        // if (triedRetreatTowers.length() == 0) {
        // // completely out of towers, how is this possible lol
        // retreatTower = -2;
        // break;
        // }
        // triedRetreatTowers = new StringBuilder();
        // continue;
        // }
        // // if (!hasCritical && POI.towerTypes[best] !=
        // UnitType.LEVEL_ONE_PAINT_TOWER) {
        // // retreatTower = -2;
        // // break;
        // // }
        // if (POI.towerTypes[best] != UnitType.LEVEL_ONE_PAINT_TOWER &&
        // !G.rc.canSenseRobotAtLocation(POI.towerLocs[best])) {
        // retreatTower = -2;
        // break;
        // }
        // retreatTower = best;
        // triedRetreatTowers.append((char) best);
        // break;
        // }
        // }
        if (retreatTower == -1) {
            int best = -1;
            while (best == -1) {
                int bestWeight = Integer.MIN_VALUE;
                // boolean hasCritical = false;
                for (int i = G.nearbyRuins.length; --i >= 0;) {
                    MapLocation loc = G.nearbyRuins[i];
                    if (!G.rc.canSenseRobotAtLocation(loc))
                        continue;
                    RobotInfo bot = G.rc.senseRobotAtLocation(loc);
                    if (bot.team != G.team)
                        continue;
                    if (bot.type.getBaseType() != UnitType.LEVEL_ONE_PAINT_TOWER && bot.paintAmount == 0) {
                        continue;
                    }
                    int weight = 0;
                    if (triedRetreatTowers.indexOf("" + (char) i) != -1) {
                        weight -= 1000;
                    }
                    int distance = Motion.getChebyshevDistance(G.me, loc);
                    weight -= distance;
                    weight += bot.paintAmount;

                    if (best == -1 || weight > bestWeight) {
                        best = i;
                        bestWeight = weight;
                    }
                }
                if (best == -1) {
                    if (triedRetreatTowers.length() == 0) {
                        // completely out of towers, how is this possible lol
                        retreatTower = -2;
                        break;
                    }
                    triedRetreatTowers = new StringBuilder();
                    continue;
                }
                MapLocation loc = G.nearbyRuins[best];
                retreatTower = POI.towerGrid[loc.y / 5][loc.x / 5];
                // retreatTower = best;
                triedRetreatTowers.append((char) best);
                break;
            }
        }
        if (retreatTower == -2) {
            // oof no tower
            retreatTower = -1;
            // retreatLoc = Motion.exploreRandomlyLoc();
            return;
        } else if (retreatTower != -1) {
            retreatLoc = POI.towerLocs[retreatTower];
            return;
            // Motion.bugnavTowards(loc, micro);
            // G.rc.setIndicatorLine(G.me, loc, 200, 0, 200);
            // if (G.rc.canSenseRobotAtLocation(loc)) {
            // int amt = -Math.min(G.rc.getType().paintCapacity - G.rc.getPaint(),
            // G.rc.senseRobotAtLocation(loc).getPaintAmount());
            // if (G.rc.canTransferPaint(loc, amt)) {
            // G.rc.transferPaint(loc, amt);
            // }
            // }
        }
    }

    public static Direction retreatDir() throws Exception {
        return retreatDir(retreatLoc);
    }

    public static Direction retreatDir(MapLocation retreatLoc) throws Exception {
        if (G.rc.isMovementReady()) {
            G.rc.setIndicatorLine(G.me, retreatLoc, 200, 0, 200);
            int dist = G.me.distanceSquaredTo(retreatLoc);
            if (dist <= 8 && G.rc.isActionReady()) {
                if (G.rc.canSenseRobotAtLocation(retreatLoc)) {
                    RobotInfo r = G.rc.senseRobotAtLocation(retreatLoc);
                    if (r.getType().getBaseType() != UnitType.LEVEL_ONE_PAINT_TOWER) {
                        if (r.paintAmount != 0) {
                            return bug2Helper(G.me, retreatLoc, TOWARDS, 0, 0);
                        }
                    }
                    int amount = paintNeededToStopRetreating - G.rc.getPaint();
                    boolean lowest = true;
                    for (int i = 8; --i >= 0;) {
                        MapLocation waitingLoc = retreatWaitingLocs[i].translate(retreatLoc.x, retreatLoc.y);
                        if (G.rc.canSenseLocation(waitingLoc) && G.rc.canSenseRobotAtLocation(waitingLoc)
                                && G.rc.senseRobotAtLocation(waitingLoc).paintAmount < G.rc.getPaint()) {
                            // if (G.rc.canSenseLocation(waitingLoc) &&
                            // G.rc.canSenseRobotAtLocation(waitingLoc) &&
                            // G.rc.senseRobotAtLocation(waitingLoc).ID < G.rc.getID()) {
                            lowest = false;
                            break;
                        }
                    }
                    if (lowest && r.paintAmount >= amount) {
                        return bug2Helper(G.me, retreatLoc, TOWARDS, 0, 0);
                    }
                }
            }
            if (dist != 4 && dist != 8) {
                if (G.rc.canSenseRobotAtLocation(retreatLoc)) {
                    if (retreatWaitingLoc == null) {
                        updateRetreatWaitingLoc();
                    }
                    if (retreatWaitingLoc != null) {
                        G.rc.setIndicatorLine(G.me, retreatWaitingLoc, 200, 0, 100);
                        // bugnavTowards(retreatWaitingLoc);
                        return bug2Helper(G.me, retreatWaitingLoc, TOWARDS, 0, 0);
                    }
                } else {
                    // bugnavTowards(retreatLoc);
                    return bug2Helper(G.me, retreatLoc, TOWARDS, 0, 0);
                }
            }
            // Motion.bugnavAround(retreatLoc, 1, 4);
        }
        return Direction.CENTER;
    }

    public static void retreat() throws Exception {
        retreat(defaultMicro);
    }

    public static void retreat(Micro micro) throws Exception {
        Motion.microMove(micro.micro(Motion.retreatDir(retreatLoc), retreatLoc));
    }

    public static void tryTransferPaint() throws Exception {
        for (int i = G.nearbyRuins.length; --i >= 0;) {
            MapLocation loc = G.nearbyRuins[i];
            if (G.rc.canSenseRobotAtLocation(loc)) {
                RobotInfo r = G.rc.senseRobotAtLocation(loc);
                int amt = -Math.min(G.rc.getType().paintCapacity - G.rc.getPaint(),
                        r.paintAmount);
                if (amt != 0 && G.rc.canTransferPaint(loc, amt)) {
                    G.rc.transferPaint(loc, amt);
                }
            }
        }
        // if (G.rc.canSenseRobotAtLocation(retreatLoc)) {
        // RobotInfo r = G.rc.senseRobotAtLocation(retreatLoc);
        // int amt = -Math.min(G.rc.getType().paintCapacity - G.rc.getPaint(),
        // r.paintAmount);
        // if (G.rc.canTransferPaint(retreatLoc, amt)) {
        // G.rc.transferPaint(retreatLoc, amt);
        // }
        // }
    }

    // cownav
    public static StringBuilder lastVisitedLocations = new StringBuilder();

    // bugnav helpers

    public static MapLocation bugnavTarget;
    public static int bugnavMode = -1;

    public static int minDistanceToTarget;
    public static int maxDistanceFromTarget;
    public static int minCircleDistance;
    public static int maxCircleDistance;
    public static boolean obstacleOnRight;
    public static MapLocation currentObstacle;
    public static StringBuilder visitedList = new StringBuilder();

    public static Direction bug2Helper(MapLocation me, MapLocation target, int mode, int minCircleDistance1,
            int maxCircleDistance1) throws Exception {
        boolean stuck = true;
        for (int i = 8; --i >= 0;) {
            if (G.rc.canMove(G.DIRECTIONS[i])) {
                stuck = false;
                break;
            }
        }

        if (stuck) {
            return Direction.CENTER;
        }

        if (bugnavTarget == null || !bugnavTarget.equals(target) || bugnavMode != mode) {
            reset();
        }
        bugnavTarget = target;
        bugnavMode = mode;
        minCircleDistance = minCircleDistance1;
        maxCircleDistance = maxCircleDistance1;

        int distanceToTarget = getChebyshevDistance(G.me, target);
        switch (bugnavMode) {
            case TOWARDS:
                if (distanceToTarget < minDistanceToTarget) {
                    reset();
                    minDistanceToTarget = distanceToTarget;
                }
                break;
            case AWAY:
                if (distanceToTarget > maxDistanceFromTarget) {
                    reset();
                    maxDistanceFromTarget = distanceToTarget;
                }
                break;
            case AROUND:
                // kind of approximation
                // probably wont circle around something with very large radius?
                int dist = G.me.distanceSquaredTo(bugnavTarget);
                if (dist < minCircleDistance) {
                    if (distanceToTarget > maxDistanceFromTarget) {
                        reset();
                        maxDistanceFromTarget = distanceToTarget;
                    }
                } else if (dist > maxCircleDistance) {
                    if (distanceToTarget < minDistanceToTarget) {
                        reset();
                        minDistanceToTarget = distanceToTarget;
                    }
                }
                break;
        }

        if (currentObstacle != null && G.rc.canSenseLocation(currentObstacle)
                && G.rc.sensePassability(currentObstacle) && !G.rc.canSenseRobotAtLocation(currentObstacle)) {
            reset();
        }

        if (visitedList.indexOf("" + getState()) != -1) {
            reset();
        }
        visitedList.append("" + getState());

        Direction targetDirection = getTargetDirection();

        if (currentObstacle == null) {
            if (canMove(targetDirection)) {
                return targetDirection;
            }

            setInitialDirection(targetDirection);
        }

        return followWall(true);
    }

    public static void reset() {
        minDistanceToTarget = Integer.MAX_VALUE;
        maxDistanceFromTarget = 0;
        obstacleOnRight = true;
        currentObstacle = null;
        visitedList = new StringBuilder();
    }

    public static Direction getTargetDirection() throws Exception {
        if (G.me.equals(bugnavTarget)) {
            if (bugnavMode == AROUND) {
                return Direction.EAST;
            } else {
                return Direction.CENTER;
            }
        }
        Direction direction = G.me.directionTo(bugnavTarget);
        switch (bugnavMode) {
            case AWAY:
                direction = direction.opposite();
                break;
            case AROUND:
                int dist = G.me.distanceSquaredTo(bugnavTarget);
                if (dist < minCircleDistance) {
                    direction = direction.opposite();
                } else if (dist <= maxCircleDistance) {
                    direction = direction.rotateLeft().rotateLeft();
                    if (circleDirection == COUNTER_CLOCKWISE) {
                        direction = direction.opposite();
                    }

                    if (!canMove(direction)) {
                        direction = direction.opposite();
                        circleDirection *= -1;
                    }
                }
                break;
        }
        return direction;
    }

    public static void setInitialDirection(Direction forward) throws Exception {
        Direction left = forward.rotateLeft();
        for (int i = 8; --i >= 0;) {
            MapLocation location = G.rc.adjacentLocation(left);
            if (G.rc.onTheMap(location) && G.rc.sensePassability(location) && !G.rc.canSenseRobotAtLocation(location)) {
                break;
            }

            left = left.rotateLeft();
        }

        Direction right = forward.rotateRight();
        for (int i = 8; --i >= 0;) {
            MapLocation location = G.rc.adjacentLocation(right);
            if (G.rc.onTheMap(location) && G.rc.sensePassability(location) && !G.rc.canSenseRobotAtLocation(location)) {
                break;
            }

            right = right.rotateRight();
        }

        // TODO: add paint weightings

        MapLocation leftLocation = G.rc.adjacentLocation(left);
        MapLocation rightLocation = G.rc.adjacentLocation(right);

        int leftDistance = getChebyshevDistance(leftLocation, bugnavTarget);
        int rightDistance = getChebyshevDistance(rightLocation, bugnavTarget);

        if (leftDistance == rightDistance) {
            obstacleOnRight = (Random.rand() % 2) == 0;
        } else if (leftDistance < rightDistance) {
            obstacleOnRight = true;
        } else if (rightDistance < leftDistance) {
            obstacleOnRight = false;
        } else {
            obstacleOnRight = G.me.distanceSquaredTo(leftLocation) < G.me.distanceSquaredTo(rightLocation);
        }

        if (obstacleOnRight) {
            currentObstacle = G.rc.adjacentLocation(left.rotateRight());
        } else {
            currentObstacle = G.rc.adjacentLocation(right.rotateLeft());
        }
    }

    public static Direction followWall(boolean canRotate) throws Exception {
        Direction direction = G.rc.getLocation().directionTo(currentObstacle);

        for (int i = 8; --i >= 0;) {
            direction = obstacleOnRight ? direction.rotateLeft() : direction.rotateRight();
            if (canMove(direction)) {
                return direction;
            }

            MapLocation location = G.rc.adjacentLocation(direction);
            if (canRotate && !G.rc.onTheMap(location)) {
                obstacleOnRight = !obstacleOnRight;
                return followWall(false);
            }

            if (G.rc.onTheMap(location)
                    && (!G.rc.sensePassability(location) || G.rc.canSenseRobotAtLocation(location))) {
                currentObstacle = location;
            }
        }
        return Direction.CENTER;
    }

    public static char getState() {
        Direction direction = G.me.directionTo(currentObstacle != null ? currentObstacle : bugnavTarget);
        int rotation = obstacleOnRight ? 1 : 0;

        return (char) ((((G.me.x << 6) | G.me.y) << 4) | (direction.ordinal() << 1) |
                rotation);
    }

    public static int[] simulateMovement(MapLocation me, MapLocation dest) throws Exception {
        MapLocation clockwiseLoc = G.rc.getLocation();
        Direction clockwiseLastDir = lastDir;
        int clockwiseStuck = 0;
        MapLocation counterClockwiseLoc = G.rc.getLocation();
        Direction counterClockwiseLastDir = lastDir;
        int counterClockwiseStuck = 0;
        search: for (int t = 0; t < 10; t++) {
            // search: for (int t = 0; t < 2; t++) {
            if (clockwiseLoc.equals(dest)) {
                break;
            }
            if (counterClockwiseLoc.equals(dest)) {
                break;
            }
            Direction clockwiseDir = clockwiseLoc.directionTo(dest);
            {
                for (int i = 9; --i >= 0;) {
                    MapLocation loc = clockwiseLoc.add(clockwiseDir);
                    if (G.rc.onTheMap(loc)) {
                        if (!G.rc.canSenseLocation(loc)) {
                            break search;
                        }
                        if (clockwiseDir != clockwiseLastDir.opposite() && G.rc.senseMapInfo(loc).isPassable()
                                && G.rc.senseRobotAtLocation(loc) == null) {
                            clockwiseLastDir = clockwiseDir;
                            break;
                        }
                    }
                    clockwiseDir = clockwiseDir.rotateRight();
                    if (i == 7) {
                        clockwiseStuck = 1;
                        break search;
                    }
                }
            }
            Direction counterClockwiseDir = counterClockwiseLoc.directionTo(dest);
            {
                for (int i = 9; --i >= 0;) {
                    MapLocation loc = counterClockwiseLoc.add(counterClockwiseDir);
                    if (G.rc.onTheMap(loc)) {
                        if (!G.rc.canSenseLocation(loc)) {
                            break search;
                        }
                        if (counterClockwiseDir != counterClockwiseLastDir.opposite()
                                && G.rc.senseMapInfo(loc).isPassable() && G.rc.senseRobotAtLocation(loc) == null) {
                            counterClockwiseLastDir = counterClockwiseDir;
                            break;
                        }
                    }
                    counterClockwiseDir = counterClockwiseDir.rotateLeft();
                    if (i == 7) {
                        counterClockwiseStuck = 1;
                        break search;
                    }
                }
            }
            clockwiseLoc = clockwiseLoc.add(clockwiseDir);
            counterClockwiseLoc = counterClockwiseLoc.add(counterClockwiseDir);
        }

        int clockwiseDist = clockwiseLoc.distanceSquaredTo(dest);
        int counterClockwiseDist = counterClockwiseLoc.distanceSquaredTo(dest);

        return new int[] { clockwiseDist, clockwiseStuck, counterClockwiseDist, counterClockwiseStuck };
    }

    public static Direction bug2Helper(MapLocation me, MapLocation me2, MapLocation dest, int mode,
            int minRadiusSquared, int maxRadiusSquared) throws Exception {
        Direction direction = me.directionTo(dest);
        if (me.equals(dest)) {
            if (mode == AROUND) {
                direction = Direction.EAST;
            } else {
                return Direction.CENTER;
            }
        }
        if (mode == AWAY) {
            direction = direction.opposite();
        } else if (mode == AROUND) {
            if (me.distanceSquaredTo(dest) < minRadiusSquared) {
                direction = direction.opposite();
            } else if (me.distanceSquaredTo(dest) <= maxRadiusSquared) {
                direction = direction.rotateLeft().rotateLeft();
                if (circleDirection == COUNTER_CLOCKWISE) {
                    direction = direction.opposite();
                }
            }
            lastDir = Direction.CENTER;
        }

        boolean stuck = true;
        for (int i = 4; --i >= 0;) {
            String m = me + " " + i + " ";
            if (visitedList.indexOf(m) == -1) {
                visitedList.append(m);
                stuck = false;
                break;
            }
        }
        if (stuck) {
            moveRandomly();
            visitedList = new StringBuilder();
            return Direction.CENTER;
        }

        // G.indicatorString.append("DIR=" + direction + " ");
        if (optimalDir != Direction.CENTER && mode != AROUND) {
            if (canMove(optimalDir) && lastDir != optimalDir.opposite()) {
                optimalDir = Direction.CENTER;
                rotation = NONE;
                visitedList = new StringBuilder();
            } else {
                direction = optimalDir;
            }
        }
        // G.indicatorString.append("OPTIMAL=" + optimalDir + " ");

        // G.indicatorString.append("CIRCLE: " + circleDirection + " ");
        // G.indicatorString.append("DIR: " + direction + " ");
        // G.indicatorString.append("OFF: " + G.rc.onTheMap(me.add(direction)) + " ");

        if (lastDir != direction.opposite()) {
            if (canMove(direction)) {
                // if (!lastBlocked) {
                // rotation = NONE;
                // }
                // lastBlocked = false;
                // boolean touchingTheWallBefore = false;
                // for (int i = DIRECTIONS.length; --i>=0;) {
                // MapLocation translatedMapLocation = me.add(d);
                // if (G.rc.onTheMap(translatedMapLocation)) {
                // if (!G.rc.senseMapInfo(translatedMapLocation).isPassable()) {
                // touchingTheWallBefore = true;
                // break;
                // }
                // }
                // }
                // if (touchingTheWallBefore) {
                // rotation = NONE;
                // }
                return direction;
            }
        } else if (canMove(direction)) {
            Direction dir;
            if (rotation == CLOCKWISE) {
                dir = direction.rotateRight();
            } else {
                dir = direction.rotateLeft();
            }
            if (!G.rc.onTheMap(me.add(dir))) {
                // boolean touchingTheWallBefore = false;
                // for (int i = DIRECTIONS.length; --i>=0;) {
                // MapLocation translatedMapLocation = me.add(d);
                // if (G.rc.onTheMap(translatedMapLocation)) {
                // if (!G.rc.senseMapInfo(translatedMapLocation).isPassable()) {
                // touchingTheWallBefore = true;
                // break;
                // }
                // }
                // }
                // if (touchingTheWallBefore) {
                // rotation = NONE;
                // }
                rotation *= -1;
                return direction;
            }
        }
        if (!G.rc.onTheMap(me.add(direction))) {
            if (mode == AROUND) {
                circleDirection *= -1;
                direction = direction.opposite();
                // G.indicatorString.append("FLIPPED ");
            } else {
                direction = me.directionTo(dest);
            }
            if (canMove(direction)) {
                return direction;
            }
        }

        if (optimalDir == Direction.CENTER) {
            optimalDir = direction;
        }

        // G.indicatorString.append("ROTATION=" + rotation + " ");
        if (rotation == NONE) {
            // if (G.rng.nextInt(2) == 0) {
            // rotation = CLOCKWISE;
            // } else {
            // rotation = COUNTER_CLOCKWISE;
            // }
            int[] simulated = simulateMovement(me, dest);

            int clockwiseDist = simulated[0];
            int counterClockwiseDist = simulated[2];
            boolean clockwiseStuck = simulated[1] == 1;
            boolean counterClockwiseStuck = simulated[3] == 1;

            // G.indicatorString.append("DIST=" + clockwiseDist + " " +
            // counterClockwiseDist
            // + " ");
            int tempMode = mode;
            if (mode == AROUND) {
                if (clockwiseDist < minRadiusSquared) {
                    if (counterClockwiseDist < minRadiusSquared) {
                        tempMode = AWAY;
                    } else {
                        tempMode = AWAY;
                    }
                } else {
                    if (counterClockwiseDist < minRadiusSquared) {
                        tempMode = AWAY;
                    } else {
                        tempMode = TOWARDS;
                    }
                }
            }
            if (clockwiseStuck) {
                rotation = COUNTER_CLOCKWISE;
            } else if (counterClockwiseStuck) {
                rotation = CLOCKWISE;
            } else if (tempMode == TOWARDS) {
                if (clockwiseDist < counterClockwiseDist) {
                    rotation = CLOCKWISE;
                } else {
                    rotation = COUNTER_CLOCKWISE;
                }
            } else if (tempMode == AWAY) {
                if (clockwiseDist < counterClockwiseDist) {
                    rotation = COUNTER_CLOCKWISE;
                } else {
                    rotation = CLOCKWISE;
                }
            }
        }

        boolean flip = false;
        for (int i = 8; --i >= 0;) {
            if (rotation == CLOCKWISE) {
                direction = direction.rotateRight();
            } else {
                direction = direction.rotateLeft();
            }
            if (!G.rc.onTheMap(me.add(direction))) {
                flip = true;
            }
            // if (G.rc.onTheMap(me.add(direction)) &&
            // G.rc.senseMapInfo(me.add(direction)).isPassable() && lastDir !=
            // direction.opposite()) {
            // if (canMove(direction)) {
            // return direction;
            // }
            // return Direction.CENTER;
            // }
            if (canMove(direction) && lastDir != direction.opposite()) {
                if (flip) {
                    rotation *= -1;
                }
                if (canMove(direction)) {
                    return direction;
                }
                return Direction.CENTER;
            }
        }
        if (flip) {
            rotation *= -1;
        }
        if (canMove(lastDir.opposite())) {
            return lastDir.opposite();
        }
        return Direction.CENTER;
    }

    // static int total = 0;
    static int turns = 0;

    // IMPORTANT: bugnav takes around 1100 bytecode

    public static void bugnavTowards(MapLocation dest) throws Exception {
        // int a = Clock.getBytecodeNum();
        bugnavTowards(dest, defaultMicro);
        // total += Clock.getBytecodeNum() - a;
        turns++;
        // G.indicatorString.append("BUG-BT" + (total / turns) + " ");
        // G.indicatorString.append("BUG-BT" + (Clock.getBytecodeNum() - a) + " ");
    }

    public static void bugnavTowards(MapLocation dest, Micro m) throws Exception {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.rc.getLocation(), dest, TOWARDS, 0, 0);
            // Direction d = bug2Helper(dest, TOWARDS, 0, 0);
            // what is purpose of this v
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            microMove(m.micro(d, dest));
        }
    }

    public static void bugnavAway(MapLocation dest) throws Exception {
        bugnavAway(dest, defaultMicro);
    }

    public static void bugnavAway(MapLocation dest, Micro m) throws Exception {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.me, dest, AWAY, 0, 0);
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            microMove(m.micro(d, dest));
        }
    }

    public static void bugnavAround(MapLocation dest, int minRadiusSquared, int maxRadiusSquared) throws Exception {
        bugnavAround(dest, minRadiusSquared, maxRadiusSquared, defaultMicro);
    }

    public static void bugnavAround(MapLocation dest, int minRadiusSquared, int maxRadiusSquared, Micro m)
            throws Exception {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.rc.getLocation(), dest, AROUND, minRadiusSquared, maxRadiusSquared);
            // Direction d = bug2Helper(dest, AROUND, minRadiusSquared, maxRadiusSquared);
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            microMove(m.micro(d, dest));
        }
    }

    public static MapLocation bfsDest;
    public static long[] bfsMap;
    public static long[] bfsDist;
    public static long[] bfsCurr;
    public static long bitmask;
    // public static StringBuilder bfsQueue = new StringBuilder();
    public static final int MAX_PATH_LENGTH = 100;

    public static void bfsInit() {
        width = G.mapWidth;
        height = G.mapHeight;
        bfsMap = new long[height + 2];
        bfsCurr = new long[height + 2];
        bfsDist = new long[(height + 2) * MAX_PATH_LENGTH];
        bitmask = (long1 << width) - 1;
    }

    public static int step = 1;
    public static int stepOffset;
    public static int width;
    public static int height;
    public static long long1 = 1;
    public static int recalculationNeeded = MAX_PATH_LENGTH;

    public static void updateBfsMap() throws Exception {
        MapInfo[] map = G.rc.senseNearbyMapInfos();
        for (int i = map.length; --i >= 0;) {
            MapInfo m = map[i];
            if (m.isWall()) {
                int loc = m.getMapLocation().y + 1;
                int subloc = m.getMapLocation().x;
                if (((bfsMap[loc] >> subloc) & 1) == 0) {
                    bfsMap[loc] |= (long1 << subloc);
                    G.rc.setIndicatorDot(m.getMapLocation(), 255, 255, 255);
                    for (int j = step - 1; j >= 0; j--) {
                        if (((bfsDist[j * (height + 2) + loc] >> subloc) & 1) != 1) {
                            recalculationNeeded = Math.min(j, recalculationNeeded);
                            break;
                        }
                    }
                }
            }
        }
    }

    public static void bfs() throws Exception {

        if (recalculationNeeded != MAX_PATH_LENGTH && recalculationNeeded < step) {
            step = recalculationNeeded;
            for (int i = 1; i <= height; i++) {
                // bfsDist[i] = 0;
                // bfsCurr[i] = 0;
                bfsCurr[i] = bfsDist[step * (height + 2) + i];
            }
            step += 1;
            // G.indicatorString.append("BFS-RECALC ");
        }
        recalculationNeeded = MAX_PATH_LENGTH;

        while (step < MAX_PATH_LENGTH && Clock.getBytecodesLeft() > 5000) {
            stepOffset = step * (height + 2);
            switch (height) {
                case 20:
                    MotionCodeGen.bfs20();
                    break;
                case 21:
                    MotionCodeGen.bfs21();
                    break;
                case 22:
                    MotionCodeGen.bfs22();
                    break;
                case 23:
                    MotionCodeGen.bfs23();
                    break;
                case 24:
                    MotionCodeGen.bfs24();
                    break;
                case 25:
                    MotionCodeGen.bfs25();
                    break;
                case 26:
                    MotionCodeGen.bfs26();
                    break;
                case 27:
                    MotionCodeGen.bfs27();
                    break;
                case 28:
                    MotionCodeGen.bfs28();
                    break;
                case 29:
                    MotionCodeGen.bfs29();
                    break;
                case 30:
                    MotionCodeGen.bfs30();
                    break;
                case 31:
                    MotionCodeGen.bfs31();
                    break;
                case 32:
                    MotionCodeGen.bfs32();
                    break;
                case 33:
                    MotionCodeGen.bfs33();
                    break;
                case 34:
                    MotionCodeGen.bfs34();
                    break;
                case 35:
                    MotionCodeGen.bfs35();
                    break;
                case 36:
                    MotionCodeGen.bfs36();
                    break;
                case 37:
                    MotionCodeGen.bfs37();
                    break;
                case 38:
                    MotionCodeGen.bfs38();
                    break;
                case 39:
                    MotionCodeGen.bfs39();
                    break;
                case 40:
                    MotionCodeGen.bfs40();
                    break;
                case 41:
                    MotionCodeGen.bfs41();
                    break;
                case 42:
                    MotionCodeGen.bfs42();
                    break;
                case 43:
                    MotionCodeGen.bfs43();
                    break;
                case 44:
                    MotionCodeGen.bfs44();
                    break;
                case 45:
                    MotionCodeGen.bfs45();
                    break;
                case 46:
                    MotionCodeGen.bfs46();
                    break;
                case 47:
                    MotionCodeGen.bfs47();
                    break;
                case 48:
                    MotionCodeGen.bfs48();
                    break;
                case 49:
                    MotionCodeGen.bfs49();
                    break;
                case 50:
                    MotionCodeGen.bfs50();
                    break;
                case 51:
                    MotionCodeGen.bfs51();
                    break;
                case 52:
                    MotionCodeGen.bfs52();
                    break;
                case 53:
                    MotionCodeGen.bfs53();
                    break;
                case 54:
                    MotionCodeGen.bfs54();
                    break;
                case 55:
                    MotionCodeGen.bfs55();
                    break;
                case 56:
                    MotionCodeGen.bfs56();
                    break;
                case 57:
                    MotionCodeGen.bfs57();
                    break;
                case 58:
                    MotionCodeGen.bfs58();
                    break;
                case 59:
                    MotionCodeGen.bfs59();
                    break;
                case 60:
                    MotionCodeGen.bfs60();
                    break;
            }
            // var cod = "";
            // for (var i = 30; i <= 60; i++) {
            // cod += "public static void bfs" + i + "() {\n";
            // for (var j = 1; j <= i; j++) {
            // cod += "Motion.bfsCurr[z] = Motion.bfsCurr[z] | (Motion.bfsCurr[z] >> 1) |
            // (Motion.bfsCurr[z] << 1);\n".replaceAll("z", j);
            // }
            // for (var j = 1; j <= i; j++) {
            // cod += "Motion.bfsDist[Motion.stepOffset + z] = (Motion.bfsCurr[z] |
            // Motion.bfsCurr[y] | Motion.bfsCurr[x]) & (Motion.bitmask ^
            // Motion.bfsMap[z]);\n".replaceAll("z", j).replaceAll("y", j -
            // 1).replaceAll("x", j + 1);
            // }
            // for (var j = 1; j <= i; j++) {
            // //cod += "Motion.bfsDist[Motion.stepOffset + z] &= Motion.bitmask ^
            // Motion.bfsMap[z];\n".replaceAll("z", j);
            // }
            // for (var j = 1; j <= i; j++) {
            // cod += "Motion.bfsCurr[z] = Motion.bfsDist[Motion.stepOffset +
            // z];\n".replaceAll("z", j);
            // }
            // cod += "}\n";
            // }
            // console.log(cod);
            step += 1;
        }

        // int b = G.round % width;
        // if (G.round == 201) {
        // for (int i = 0; i < width; i++) {
        // b = i;
        // for (int j = 0; j < height; j++) {
        // // if (((bfsDist[(G.round % 100) * (height + 2) + j + 1] >> i) &
        // 1) == 0) {
        // if (((bfsDist[(G.round % 100) * (height + 2) + j + 1] >> b) & 1)
        // == 0) {
        // if (((bfsMap[j + 1] >> b) & 1) == 0) {
        // G.rc.setIndicatorDot(new MapLocation(b, j), 255, 0, 0);
        // }
        // else {
        // G.rc.setIndicatorDot(new MapLocation(b, j), 0, 0, 0);
        // }
        // }
        // else {
        // if (((bfsMap[j + 1] >> b) & 1) == 0) {
        // G.rc.setIndicatorDot(new MapLocation(b, j), 255, 255, 255);
        // }
        // else {
        // G.rc.setIndicatorDot(new MapLocation(b, j), 0, 255, 0);
        // }
        // }
        // }
        // }
        // }
        G.indicatorString.append("BFS-STP=" + step + " ");
    }

    public static Direction getBfsDirection(MapLocation dest) throws Exception {
        boolean[] directions = new boolean[9];
        for (int i = 1; i < step; i++) {
            if (((bfsDist[i * (height + 2) + 1 + G.me.y] >> G.me.x) & 1) == 1) {
                if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y - 1] >> G.me.x) & 1) == 1) {
                    directions[7] = true;
                }
                if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y + 1] >> G.me.x) & 1) == 1) {
                    directions[3] = true;
                }
                if (G.me.x > 0) {
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y] >> (G.me.x - 1)) & 1) == 1) {
                        directions[1] = true;
                    }
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y - 1] >> (G.me.x - 1)) & 1) == 1) {
                        directions[8] = true;
                    }
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y + 1] >> (G.me.x - 1)) & 1) == 1) {
                        directions[2] = true;
                    }
                }
                if (G.me.x < width - 1) {
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y] >> (G.me.x + 1)) & 1) == 1) {
                        directions[5] = true;
                    }
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y - 1] >> (G.me.x + 1)) & 1) == 1) {
                        directions[6] = true;
                    }
                    if (((bfsDist[(i - 1) * (height + 2) + 1 + G.me.y + 1] >> (G.me.x + 1)) & 1) == 1) {
                        directions[4] = true;
                    }
                }
                break;
            }
        }
        Direction optimalDirection = Direction.CENTER;
        int minDist = Integer.MAX_VALUE;
        // int optimalIndex = 0;
        for (int i = 9; --i >= 0;) {
            if (directions[i]) {
                Direction dir = Direction.DIRECTION_ORDER[i];
                if (G.rc.canMove(dir)) {
                    if (G.me.add(dir).distanceSquaredTo(dest) < minDist) {
                        optimalDirection = dir;
                        minDist = G.me.add(dir).distanceSquaredTo(dest);
                    }
                }
            }
        }
        if (optimalDirection != Direction.CENTER) {
            return optimalDirection;
        }
        if (optimalDirection == Direction.CENTER) {
            optimalDirection = bug2Helper(G.me, dest, TOWARDS, 0, 0);
            // G.indicatorString.append("BFS-BUG ");

            if (G.rc.canMove(optimalDirection)) {
                return optimalDirection;
            }
        }
        if (G.rc.canMove(optimalDirection)) {
            return optimalDirection;
        }
        return Direction.CENTER;
    }

    public static void bfsnav(MapLocation dest) throws Exception {
        bfsnav(dest, defaultMicro);
    }

    public static void bfsnav(MapLocation dest, Micro m) throws Exception {
        int a = Clock.getBytecodesLeft();
        updateBfsTarget(dest);
        if (!G.rc.getLocation().equals(dest) && G.rc.isMovementReady()) {
            Direction d = getBfsDirection(dest);
            if (d == Direction.CENTER) {
                d = G.rc.getLocation().directionTo(dest);
            }
            microMove(m.micro(d, dest));
        }
        bfs();
        G.indicatorString.append("BFS-BT: " + (Clock.getBytecodesLeft() - a) + "-");
    }

    public static void updateBfsTarget(MapLocation dest) throws Exception {
        if (!dest.equals(bfsDest)) {
            bfsDest = dest;
            for (int i = 1; i <= height; i++) {
                bfsDist[i] = 0;
                bfsCurr[i] = 0;
            }
            bfsDist[dest.y + 1] = long1 << (dest.x);
            bfsCurr[dest.y + 1] = long1 << (dest.x);
            step = 1;
        }
    }

    public static final int DEF_MICRO_E_PAINT_PENALTY = 5;
    public static final int DEF_MICRO_E_PAINT_BOT_PENALTY = 10;
    public static final int DEF_MICRO_N_PAINT_PENALTY = 5;
    public static final int DEF_MICRO_N_PAINT_BOT_PENALTY = 5;

    /**
     * Default movement micro - avoid clusters of bots, especially on non-allied
     * paint
     */
    public static Micro defaultMicro = (Direction d, MapLocation dest) -> {
        int[] scores = new int[9];
        MapLocation nxt;
        PaintType p;
        scores[G.dirOrd(d)] += 20;
        if (d != Direction.CENTER) {
            scores[G.dirOrd(d.rotateLeft())] += 15;
            scores[G.dirOrd(d.rotateRight())] += 15;
        }
        int mopperPenalty = G.rc.getType() == UnitType.MOPPER ? GameConstants.MOPPER_PAINT_PENALTY_MULTIPLIER : 1;
        int turnsToNext = ((G.cooldown(G.rc.getPaint(), GameConstants.MOVEMENT_COOLDOWN) + movementCooldown) / 10);
        int enemyPaintPenalty = DEF_MICRO_E_PAINT_PENALTY * GameConstants.PENALTY_ENEMY_TERRITORY * mopperPenalty
                * turnsToNext;
        int neutralPaintPenalty = DEF_MICRO_N_PAINT_PENALTY * GameConstants.PENALTY_NEUTRAL_TERRITORY * mopperPenalty
                * turnsToNext;
        for (int i = 9; --i >= 0;) {
            if (!G.rc.canMove(G.ALL_DIRECTIONS[i]) && i != 8) {
                scores[i] = -1000000000;
            } else {
                nxt = G.me.add(G.ALL_DIRECTIONS[i]);
                int index = Motion.lastVisitedLocations.lastIndexOf(nxt.toString());
                if (index != -1) {
                    // penalizes sitting still
                    int numTurnsVisitedAgo = (Motion.lastVisitedLocations.length() - index) / 8;
                    if (numTurnsVisitedAgo < 5)
                        scores[i]--;
                }
                p = G.rc.senseMapInfo(nxt).getPaint();
                if (p.isEnemy()) {
                    scores[i] -= enemyPaintPenalty;
                    for (int j = 8; --j >= 0;) {
                        if (G.allyRobotsString.indexOf(nxt.add(G.DIRECTIONS[j]).toString()) != -1) {
                            scores[i] -= DEF_MICRO_E_PAINT_BOT_PENALTY;
                        }
                    }
                } else if (p == PaintType.EMPTY) {
                    scores[i] -= neutralPaintPenalty;
                    for (int j = 8; --j >= 0;) {
                        if (G.allyRobotsString.indexOf(nxt.add(G.DIRECTIONS[j]).toString()) != -1) {
                            scores[i] -= DEF_MICRO_N_PAINT_BOT_PENALTY;
                        }
                    }
                }
            }
        }
        for (int i = G.opponentRobots.length; --i >= 0;) {
            if (G.opponentRobots[i].type == UnitType.MOPPER) {
                for (int j = 9; --j >= 0;) {
                    if (G.me.add(G.ALL_DIRECTIONS[j]).isWithinDistanceSquared(G.opponentRobots[i].location, 8)) {
                        scores[j] -= 20; // lose 4 paint?
                    }
                }
            }
        }
        for (int r = G.nearbyRuins.length; --r >= 0;) {
            if (G.rc.canSenseRobotAtLocation(G.nearbyRuins[r])) {
                RobotInfo bot = G.rc.senseRobotAtLocation(G.nearbyRuins[r]);
                if (bot.team == G.opponentTeam) {
                    int toSubtract = (int) (G.paintPerChips() * G.rc.getType().moneyCost * turnsToNext
                            * (bot.type.attackStrength + bot.type.aoeAttackStrength) / G.rc.getType().health);
                    int toSubtract2 = toSubtract;
                    if (G.rc.getHealth() <= bot.type.attackStrength + bot.type.aoeAttackStrength)
                        toSubtract += 1000;
                    if (G.rc.getHealth() <= (bot.type.attackStrength + bot.type.aoeAttackStrength) * 2)
                        toSubtract2 += 2000;
                    for (int i = 9; --i >= 0;) {
                        if (G.rc.canMove(G.ALL_DIRECTIONS[i]) || i == 8) {
                            if (G.me.add(G.ALL_DIRECTIONS[i]).isWithinDistanceSquared(G.nearbyRuins[r],
                                    2)) {
                                scores[i] -= toSubtract2;
                            } else if (G.me.add(G.ALL_DIRECTIONS[i]).isWithinDistanceSquared(G.nearbyRuins[r],
                                    bot.type.actionRadiusSquared)) {
                                scores[i] -= toSubtract;
                            }
                        }
                    }
                }
            }
        }
        if (G.lastDefenseTowerRound + 15 <= G.rc.getRoundNum() && G.lastDefenseTower != null) {
            int toSubtract = (int) (G.paintPerChips() * G.rc.getType().moneyCost * turnsToNext
                    * (UnitType.LEVEL_ONE_DEFENSE_TOWER.attackStrength + UnitType.LEVEL_ONE_DEFENSE_TOWER.aoeAttackStrength) / G.rc.getType().health);
            int toSubtract2 = toSubtract;
            if (G.rc.getHealth() <= UnitType.LEVEL_ONE_DEFENSE_TOWER.attackStrength + UnitType.LEVEL_ONE_DEFENSE_TOWER.aoeAttackStrength)
                toSubtract += 1000;
            if (G.rc.getHealth() <= (UnitType.LEVEL_ONE_DEFENSE_TOWER.attackStrength + UnitType.LEVEL_ONE_DEFENSE_TOWER.aoeAttackStrength) * 2)
                toSubtract2 += 2000;
            for (int i = 9; --i >= 0;) {
                if (G.rc.canMove(G.ALL_DIRECTIONS[i]) || i == 8) {
                    if (G.me.add(G.ALL_DIRECTIONS[i]).isWithinDistanceSquared(G.lastDefenseTower,
                            2)) {
                        scores[i] -= toSubtract2;
                    } else if (G.me.add(G.ALL_DIRECTIONS[i]).isWithinDistanceSquared(G.lastDefenseTower,
                            UnitType.LEVEL_ONE_DEFENSE_TOWER.actionRadiusSquared)) {
                        scores[i] -= toSubtract;
                    }
                }
            }
        }
        return scores;
    };

    public static boolean microMove(int[] scores) throws Exception {
        int best = 0;
        int numBest = 1;
        for (int i = scores.length; --i >= 1;) {
            if (scores[i] > scores[best]) {
                best = i;
                numBest = 1;
            } else if (scores[i] == best && Random.rand() % ++numBest == 0) {
                best = i;
            }
        }
        // if (scores[best] > 0) {
        return move(G.ALL_DIRECTIONS[best]);
        // }
        // return false;
    }

    // false if it didn't move
    public static boolean move(Direction dir) throws Exception {
        if (G.rc.canMove(dir)) {
            G.rc.move(dir);
            movementCooldown += G.cooldown(G.rc.getPaint(), GameConstants.MOVEMENT_COOLDOWN);
            lastDir = dir;
            lastMove = G.rc.getRoundNum();
            RobotPlayer.updateMove();
            return true;
        }
        return false;
    }

    public static boolean canMove(Direction dir) throws Exception {
        if (G.rc.canMove(dir)) {
            return true;
        }
        if (G.rc.canSenseRobotAtLocation(G.me.add(dir)) && Random.rand() % 10 == 0) {
            return true;
        }
        return false;
    }
}