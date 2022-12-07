import java.awt.Color;

/**
 * simulation.Critter that optimizes flytrap behavior by making more efficient movement and prioritizing groups of critters
 */
public class EricA extends Critter {
    private enum Frame { FIND_OTHERS, GROUP, MIGRATE }

    /* State variables */

    private Frame state = Frame.FIND_OTHERS;
    private Direction commitDir = Direction.EAST; // Direction of last infection
    private Direction migrateDir = Direction.EAST; // Direction to travel during migration
    private boolean justBorn = true;

    private int commitTimer = 0; // Duration of frames that this critter faces the direction of last infection

    private double colScale = COL_SCALE_MAX; // Controls the color flashing
    private int colDir = 1;

    /* State variables across all critters */

    private static int signal = 0; // Controls migration state across all critters

    /* Parameters to tweak behavior */

    private static final int MIGRATE_THRESHOLD = 18000; // When `signal` reaches this, migrate critters
    private static final int MIGRATE_PROMOTE = 1; // `signal` rate of increase per critter
    private static final int MIGRATE_INHIBIT = 25; // `signal` rate of decrease per critter in `MIGRATE` state
    private static final double MIGRATE_TURN = 0.35; // Proportion of frames where migrating critters turn randomly
    private static final int COMMIT_TIMER_INIT = 6; // How many frames the critter stays facing the last infection

    private static final double COL_SCALE_MAX = 2.75;
    private static final double COL_SCALE_MIN = 0.35;
    private static final double COL_CHANGE_PER_FRAME = 0.10;
    private static final Color INITIAL_COLOR = new Color(110, 75, 245); // Purple!

    @Override
    public Action getMove(CritterInfo info) {
        // Handle style status
        justBorn = false;
        updateColor();

        Direction closestEnemy = MoveHelper.closestNeighbor(info, Neighbor.OTHER);

        // Always prioritize infecting other critters
        if (info.getFront() == Neighbor.OTHER) {
            commitDir = info.getDirection();
            commitTimer = COMMIT_TIMER_INIT;
            return Action.INFECT;
        // Run when chased from behind
        } else if (info.getBack() == Neighbor.OTHER && info.getFront() == Neighbor.EMPTY)
            return Action.HOP;

        // Turn towards enemies close by
        if (closestEnemy != null)
            return MoveHelper.optimalTurn(info, closestEnemy);

        // Commit to direction of last infection
        if (commitTimer-- > 0)
            return MoveHelper.optimalTurn(info, commitDir);

        return switch (state) {
            case FIND_OTHERS -> findOthers(info);
            case GROUP -> group(info);
            case MIGRATE -> migrate(info);
        };
    }

    /**
     * Directs the finding state of the critter in which it attempts to locate other same-type critters and form
     * a protective colony
     * @return the Action to advance this critter's finding behavior
     */
    public Action findOthers(CritterInfo info) {
        // Attempt to find and group with same type critter
        Direction closestFriend = MoveHelper.closestNeighbor(info, Neighbor.SAME);
        if (closestFriend != null) {
            state = Frame.GROUP;
            return MoveHelper.optimalTurn(info, MoveHelper.directionOf(info, closestFriend));
        }

        // Continue search otherwise
        return info.getFront() == Neighbor.WALL ? Action.RIGHT : Action.HOP;
    }

    /**
     * Directs the grouping state of the critter in which it forms a colony that faces empty locations;
     * Grouping strengthens flytrap behavior by covering weak spots of individual critters. The critter also
     * emits signals to promote migration, eventually reaching a culmination in which many critters move
     * to another colony. This promotes growth when there is a large amount of this critter
     * @param info info from this critter's turn
     * @return the Action to advance this critter's grouping behavior
     */
    public Action group(CritterInfo info) {
        Direction closestFriend = MoveHelper.closestNeighbor(info, Neighbor.SAME);
        Direction closestEmpty = MoveHelper.closestNeighbor(info, Neighbor.EMPTY);

        // Migrate to other group after reaching a certain threshold across all critters
        if ((signal += MIGRATE_PROMOTE) >= MIGRATE_THRESHOLD) {
            state = Frame.MIGRATE;
            migrateDir = MoveHelper.toCardinal(MoveHelper.shiftDir(MoveHelper.toGrid(migrateDir), 1));
            return MoveHelper.optimalTurn(info, migrateDir);
        }

        // Revert to searching if no friends surround the critter
        if (closestFriend == null) {
            state = Frame.FIND_OTHERS;
            return Action.HOP;
        }

        // Create barrier facing vulnerable space
        if (closestEmpty != null)
            return MoveHelper.optimalTurn(info, closestEmpty);

        // Face direction of friends to optimize flytrap behavior
        return MoveHelper.optimalTurn(info, MoveHelper.directionOf(info, closestFriend));
    }

    /**
     * Directs the migration state of the critter in which it moves across the grid in attempt to locate another
     * colony. As it migrates, it will emit inhibition signals to prevent more than small groups of critters
     * from moving at once.
     * @param info info from this critter's turn
     * @return the Action to advance this critter's migration behavior
     */
    public Action migrate(CritterInfo info) {
        Direction closestFriend = MoveHelper.closestNeighbor(info, Neighbor.SAME);

        // Decrease migration rate of other critters
        signal = Math.max(signal - MIGRATE_INHIBIT, 0);

        // Randomly turn to prevent parallel migrations (less collisions)
        if (Math.random() <= MIGRATE_TURN) {
            migrateDir = MoveHelper.toCardinal(MoveHelper.shiftDir(MoveHelper.toGrid(migrateDir), new int[] {-1, 1} [(int) (2*Math.random())]));
        }

        // Prioritize reaching a new destination
        if (info.getFront() == Neighbor.WALL)
            migrateDir = MoveHelper.toCardinal(MoveHelper.shiftDir(MoveHelper.toGrid(migrateDir), new int[] {2, -1, 1} [(int) (3*Math.random())]));
        if (info.getDirection() != migrateDir)
            return MoveHelper.optimalTurn(info, migrateDir);
        if (info.getFront() == Neighbor.EMPTY)
            return Action.HOP;

        // Attach to new group if it exists
        if (closestFriend != null) {
            state = Frame.GROUP;
            return MoveHelper.optimalTurn(info, MoveHelper.directionOf(info, closestFriend));
        }

        return Action.HOP;
    }

    /**
     * Updates the gradient of this critter by oscillating a multiplier for initColor from
     * COL_SCALE_MIN to COL_SCALE_MAX
     */
    private void updateColor() {
        colScale += colDir * COL_CHANGE_PER_FRAME;
        if (COL_SCALE_MIN > colScale || colScale > COL_SCALE_MAX)
            colDir *= -1;
        colScale = Math.min(COL_SCALE_MAX, Math.max(COL_SCALE_MIN, colScale));
    }

    @Override
    public Color getColor() {
        return justBorn ? Color.WHITE : new Color(
                (int) Math.min(255, colScale * INITIAL_COLOR.getRed()),
                (int) Math.min(255, colScale * INITIAL_COLOR.getGreen()),
                (int) Math.min(255, colScale * INITIAL_COLOR.getBlue()));
    }

    @Override
    public String toString() {
        // ✿❀✾✽✼✤✥❈
        // ⚴☥⚳⚵⚸♆
        // ❉✼✸✱⚝
        // ◜◝◞◟
        // ◴◷◶◵
        // ▓▒░
        // ◐◓◑◒
        return justBorn ?  "⏺" : "✿";
    }
}

/**
 * Static class to provide helper methods and optimal movements, such as shortest cost turns
 */
class MoveHelper {
    /**
     * Create new direction system to take advantage of numerical turns and shifts
     */
    public enum GridDirection { RIGHT, DOWN, LEFT, UP }

    /**
     * Converts simulation.Critter.Direction to GridDirection
     * @param dir A simulation.Critter/Cardinal Direction
     * @return The corresponding GridDirection
     */
    public static GridDirection toGrid(Critter.Direction dir) {
        return switch (dir) {
            case NORTH -> GridDirection.UP; case EAST -> GridDirection.RIGHT;
            case SOUTH -> GridDirection.DOWN; case WEST -> GridDirection.LEFT;
        };
    }

    /**
     * Converts a GridDirection to simulation.Critter.Direction
     * @param dir A GridDirection
     * @return The corresponding simulation.Critter/Cardinal Direction
     */
    public static Critter.Direction toCardinal(GridDirection dir) {
        return switch (dir) {
            case UP -> Critter.Direction.NORTH; case LEFT -> Critter.Direction.WEST;
            case DOWN -> Critter.Direction.SOUTH; case RIGHT -> Critter.Direction.EAST;
        };
    }

    /**
     * Returns a direction shifted left or right
     * @param dir An initial Grid.Direction
     * @param shift The amount to shift clockwise
     * @return The shifted GridDirection
     */
    public static GridDirection shiftDir(GridDirection dir, int shift) {
        if ((dir.ordinal() + shift) % 4 < 0)
            return GridDirection.values()[3 + ((dir.ordinal() + shift) % 4)];
        return GridDirection.values()[(dir.ordinal() + shift) % 4];
    }

    /**
     * Returns the turn from simulation.Critter.Action to face a certain direction in an optimal amount of steps
     * @param info A current simulation.CritterInfo
     * @param toFaceCardinal The simulation.Critter.Direction to face
     * @return The simulation.Critter.Action to take in order to face toFaceCardinal
     */
    public static Critter.Action optimalTurn(CritterInfo info, Critter.Direction toFaceCardinal) {
        Critter.Direction currCardinal = info.getDirection();
        if (currCardinal == toFaceCardinal)
            return Critter.Action.INFECT;

        GridDirection curr = toGrid(currCardinal);
        GridDirection toFace = toGrid(toFaceCardinal);

        int leftCost = Math.abs(shiftDir(curr, -1).ordinal() - toFace.ordinal());
        int rightCost = Math.abs(shiftDir(curr, 1).ordinal() - toFace.ordinal());
        return rightCost < leftCost ? Critter.Action.RIGHT : Critter.Action.LEFT;
    }

    /**
     * Returns the simulation.Critter.Direction of the closest occurrence of a certain neighbor type
     * @param info A current simulation.CritterInfo
     * @param neighbor The simulation.Critter.Neighbor type to locate
     * @return The simulation.Critter.Direction of the closest neighbor of the given type
     */
    public static Critter.Direction closestNeighbor(CritterInfo info, Critter.Neighbor neighbor) {
        Integer shift = null;
        if (info.getFront() == neighbor)
            shift = 0;
        else if (info.getLeft() == info.getRight() && info.getLeft() == neighbor)
            shift = Math.random() > 0.5 ? 1 : -1; // randomly select equally close neighbors
        else if (info.getLeft() == neighbor)
            shift = -1;
        else if (info.getRight() == neighbor)
            shift = 1;
        else if (info.getBack() == neighbor)
            shift = 2;
        return shift == null ? null : toCardinal(shiftDir(toGrid(info.getDirection()), shift));
    }

    /**
     * Returns the simulation.Critter.Direction of the simulation.Critter in the cardinalTargetDir of this simulation.Critter
     * @param info A current simulation.CritterInfo
     * @param cardinalTargetDir The simulation.Critter whose Direction is desired
     * @return The simulation.Critter.Direction of the simulation.Critter at cardinalTargetDir
     */
    public static Critter.Direction directionOf(CritterInfo info, Critter.Direction cardinalTargetDir) {
        return switch (toGrid(cardinalTargetDir).ordinal() - toGrid(info.getDirection()).ordinal()) {
            case 1 -> info.getRightDirection(); case -1 -> info.getLeftDirection();
            case 0 -> info.getFrontDirection(); default -> info.getBackDirection();
        };
    }
}
