package kevin_bot_1;

import battlecode.common.*;

/**
 * Greedy Mopper — enemy paint cleaner & unit drainer.
 *
 * Decision priority (greedy):
 *  1. If low paint → refill at nearest tower
 *  2. If enemy unit nearby → attack or mop-swing (prefer lowest-paint soldier)
 *  3. If enemy paint near a ruin → mop it (enables tower building)
 *  4. If enemy paint visible → mop closest tile
 *  5. Transfer paint to low-paint allies
 *  6. Explore toward requested mopper locations or follow soldiers
 */
public class MopperBot {

    private static MapLocation spawnTower = null;
    private static MapLocation mopTarget  = null; // requested mopper location

    private static final double REFILL_LOW  = 0.25;
    private static final double REFILL_HIGH = 0.75;

    private enum State { EXPLORE, REFILL, COMBAT, MOPPING }
    private static State state = State.EXPLORE;

    static void turn() throws GameActionException {
        RobotController rc = RobotPlayer.rc;
        MapLocation me = rc.getLocation();

        if (spawnTower == null) {
            spawnTower = me;
            for (RobotInfo r : rc.senseNearbyRobots(4, RobotPlayer.myTeam))
                if (r.type.isTowerType()) { spawnTower = r.location; break; }
        }

        Messaging.readMessages();
        if (Messaging.needMopperAt != null) {
            mopTarget = Messaging.needMopperAt;
            Messaging.needMopperAt = null;
        }

        MapInfo[] nearby = rc.senseNearbyMapInfos();
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, RobotPlayer.myTeam);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, RobotPlayer.enemyTeam);

        updateKnownTowers(rc, allies);
        Messaging.relayToNearbyTower(allies);

        /* ---- decide state ---- */
        state = decideState(rc, me, nearby, allies, enemies);

        switch (state) {
            case REFILL  -> refill(rc, me, allies);
            case COMBAT  -> combat(rc, me, enemies);
            case MOPPING -> mop(rc, me, nearby, enemies);
            case EXPLORE -> explore(rc, me, nearby, allies, enemies);
        }

        /* ---- invariant actions ---- */
        invariantActions(rc, me, nearby, allies, enemies);
    }

    /* ---- state decision ---- */
    private static State decideState(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies)
            throws GameActionException {
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;

        if ((state != State.REFILL && paintRatio < REFILL_LOW)
         || (state == State.REFILL && paintRatio < REFILL_HIGH))
            return State.REFILL;

        // find combat target
        RobotInfo target = findCombatTarget(rc, me, enemies);
        if (target != null) return State.COMBAT;

        // find enemy paint to mop (prefer near ruins)
        MapLocation enemyPaint = findEnemyPaintToMop(rc, me, nearby);
        if (enemyPaint != null) return State.MOPPING;

        return State.EXPLORE;
    }

    /* ---- combat: attack/mop-swing enemies ---- */
    private static void combat(RobotController rc, MapLocation me,
            RobotInfo[] enemies) throws GameActionException {
        RobotInfo target = findCombatTarget(rc, me, enemies);
        if (target == null) { state = State.EXPLORE; return; }

        // try mop swing if 2+ enemies in cardinal direction
        if (rc.isActionReady()) {
            Direction bestSwing = findBestMopSwing(rc, me, enemies);
            if (bestSwing != null && rc.canMopSwing(bestSwing)) {
                rc.mopSwing(bestSwing);
                return;
            }
        }

        // direct attack
        if (rc.canAttack(target.location)) {
            rc.attack(target.location);
        } else if (!me.isAdjacentTo(target.location)) {
            Nav.moveIntoRange(target.location, 2);
            if (rc.canAttack(target.location)) rc.attack(target.location);
        }

        // stay close to enemy for next turn
        if (rc.isMovementReady() && !me.isAdjacentTo(target.location)) {
            Nav.moveIntoRange(target.location, 8);
        }
    }

    /* ---- mopping: clean enemy paint ---- */
    private static MapLocation currentMopLoc = null;

    private static void mop(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] enemies) throws GameActionException {
        currentMopLoc = findEnemyPaintToMop(rc, me, nearby);
        if (currentMopLoc == null) { state = State.EXPLORE; return; }

        // don't mop into enemy tower range
        if (Nav.inEnemyTowerRange(currentMopLoc, enemies)) {
            state = State.EXPLORE;
            return;
        }

        if (me.isAdjacentTo(currentMopLoc) || me.equals(currentMopLoc)) {
            if (rc.canAttack(currentMopLoc)) rc.attack(currentMopLoc);
        } else {
            Nav.moveIntoRange(currentMopLoc, 2);
            if (rc.canAttack(currentMopLoc)) rc.attack(currentMopLoc);
        }
    }

    /* ---- explore ---- */
    private static void explore(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies)
            throws GameActionException {
        // if we have a mop target from tower, go there
        if (mopTarget != null) {
            if (me.distanceSquaredTo(mopTarget) <= 2) {
                mopTarget = null;
            } else {
                Nav.bugNav(mopTarget);
                return;
            }
        }

        // follow nearest soldier for support
        RobotInfo bestFriend = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type == UnitType.SOLDIER) {
                int d = me.distanceSquaredTo(a.location);
                if (d < bestDist) { bestDist = d; bestFriend = a; }
            }
        }

        if (bestFriend != null) {
            Nav.safeFuzzyMove(bestFriend.location, enemies);
        } else {
            // random explore
            MapLocation target = new MapLocation(
                (RobotPlayer.myID * 37 + rc.getRoundNum()) % RobotPlayer.mapW,
                (RobotPlayer.myID * 53 + rc.getRoundNum()) % RobotPlayer.mapH);
            Nav.bugNav(target);
        }
    }

    /* ---- refill ---- */
    private static void refill(RobotController rc, MapLocation me,
            RobotInfo[] allies) throws GameActionException {
        RobotInfo nearestTower = null;
        int nearDist = Integer.MAX_VALUE;
        for (RobotInfo a : allies) {
            if (a.type.isTowerType() && a.paintAmount > 20) {
                int d = me.distanceSquaredTo(a.location);
                if (d < nearDist) { nearDist = d; nearestTower = a; }
            }
        }
        MapLocation target;
        if (nearestTower != null) {
            target = nearestTower.location;
        } else {
            MapLocation pt = Messaging.nearestPaintTower(me);
            target = pt != null ? pt : (spawnTower != null ? spawnTower : me);
        }
        if (me.distanceSquaredTo(target) <= 2 && nearestTower != null) {
            int amt = Math.min(rc.getType().paintCapacity - rc.getPaint(),
                               nearestTower.paintAmount);
            if (amt > 0 && rc.canTransferPaint(target, -amt))
                rc.transferPaint(target, -amt);
            if ((double) rc.getPaint() / rc.getType().paintCapacity >= REFILL_HIGH)
                state = State.EXPLORE;
        } else {
            Nav.bugNav(target);
        }
    }

    /* ---- invariant actions ---- */
    private static void invariantActions(RobotController rc, MapLocation me,
            MapInfo[] nearby, RobotInfo[] allies, RobotInfo[] enemies)
            throws GameActionException {
        // transfer paint to low-paint allies
        if (state != State.REFILL) {
            for (RobotInfo a : allies) {
                if (!a.type.isTowerType() && a.paintAmount < 15
                    && rc.getPaint() > 50
                    && rc.canTransferPaint(a.location, 20)) {
                    rc.transferPaint(a.location, 20);
                    break;
                }
            }
        }

        // attack any adjacent enemy paint tile
        if (rc.isActionReady()) {
            for (MapInfo info : rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared)) {
                if (info.getPaint().isEnemy() && rc.canAttack(info.getMapLocation())) {
                    rc.attack(info.getMapLocation());
                    break;
                }
            }
        }

        // confirm tower patterns
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        for (MapLocation ruin : ruins) {
            for (UnitType t : new UnitType[]{
                    UnitType.LEVEL_ONE_PAINT_TOWER,
                    UnitType.LEVEL_ONE_MONEY_TOWER,
                    UnitType.LEVEL_ONE_DEFENSE_TOWER}) {
                if (rc.canCompleteTowerPattern(t, ruin)) {
                    rc.completeTowerPattern(t, ruin);
                    Messaging.registerTower(ruin, t);
                    break;
                }
            }
        }
    }

    /* ============================================================ */
    /*                         HELPERS                             */
    /* ============================================================ */

    /** Find best combat target: prefer soldiers with lowest paint. */
    private static RobotInfo findCombatTarget(RobotController rc, MapLocation me,
            RobotInfo[] enemies) {
        RobotInfo best = null;
        boolean foundSoldier = false;
        int bestMetric = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType() || e.paintAmount == 0) continue;
            boolean isSoldier = e.type == UnitType.SOLDIER;
            int metric = e.paintAmount; // greedy: drain weakest first
            if (isSoldier && !foundSoldier) {
                foundSoldier = true;
                best = e;
                bestMetric = metric;
            } else if (isSoldier && metric < bestMetric) {
                best = e;
                bestMetric = metric;
            } else if (!foundSoldier && metric < bestMetric) {
                best = e;
                bestMetric = metric;
            }
        }
        return best;
    }

    /** Find best mop-swing direction hitting most enemies. */
    private static Direction findBestMopSwing(RobotController rc, MapLocation me,
            RobotInfo[] enemies) throws GameActionException {
        int[] counts = new int[4]; // N, E, S, W
        Direction[] cardinals = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType() || e.paintAmount == 0) continue;
            int dx = e.location.x - me.x;
            int dy = e.location.y - me.y;
            if (dy > 0 && dy <= 2 && Math.abs(dx) <= 1) counts[0]++;
            if (dx > 0 && dx <= 2 && Math.abs(dy) <= 1) counts[1]++;
            if (dy < 0 && dy >= -2 && Math.abs(dx) <= 1) counts[2]++;
            if (dx < 0 && dx >= -2 && Math.abs(dy) <= 1) counts[3]++;
        }
        int best = 0;
        int bestIdx = -1;
        for (int i = 0; i < 4; i++) {
            if (counts[i] > best) { best = counts[i]; bestIdx = i; }
        }
        return best >= 2 ? cardinals[bestIdx] : null;
    }

    /** Find closest enemy paint tile, preferring ones near ruins. */
    private static MapLocation findEnemyPaintToMop(RobotController rc, MapLocation me,
            MapInfo[] nearby) throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        MapLocation bestNearRuin = null;
        int bestNearDist = Integer.MAX_VALUE;
        MapLocation bestAny = null;
        int bestAnyDist = Integer.MAX_VALUE;

        for (MapInfo info : nearby) {
            if (!info.getPaint().isEnemy()) continue;
            MapLocation loc = info.getMapLocation();
            int d = me.distanceSquaredTo(loc);

            boolean nearRuin = false;
            for (MapLocation ruin : ruins) {
                if (loc.distanceSquaredTo(ruin) <= 8) { nearRuin = true; break; }
            }

            if (nearRuin && d < bestNearDist) {
                bestNearDist = d;
                bestNearRuin = loc;
            }
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = loc;
            }
        }
        return bestNearRuin != null ? bestNearRuin : bestAny;
    }

    private static void updateKnownTowers(RobotController rc, RobotInfo[] allies) {
        for (RobotInfo a : allies)
            if (a.type.isTowerType())
                Messaging.registerTower(a.location, a.type);
    }
}
