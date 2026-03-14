package SPAARK;

import battlecode.common.Direction;
import battlecode.common.MapLocation;

@FunctionalInterface
public interface Micro {
    /**
     * Micro function - returns weights of moving each direction, where highest
     * weight is movement direction. Can be chained using the weights.
     * as a general rule, 5 micro score = 1 paint
     * if none of the weights are above 0, then don't move
     * 
     * @param d    Pathfinding direction
     * @param dest Destination location
     * @return Length-9 array of weights for moving in each direction, mapped the
     *         same as G.ALL_DIRECTIONS
     * @throws Exception
     */
    public int[] micro(Direction d, MapLocation dest) throws Exception;
}