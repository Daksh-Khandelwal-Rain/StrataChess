package engine;

import shared.Position;

/**
 * One Strata Square: a position, who can SEE it, and how many collections remain.
 * "Private" means hidden information only, not ownership. Charges belong to the
 * square, so either player can collect them.
 */
public class StrataSquare {

    /** visibleTo value meaning both players can see this square. */
    public static final int SHARED = -1;

    public static final int INITIAL_CHARGES = 3;

    private final Position position;
    private final int      visibleTo;        // 0, 1, or SHARED
    private       int      chargesRemaining;

    public StrataSquare(Position position, int visibleTo) {
        this.position         = position;
        this.visibleTo        = visibleTo;
        this.chargesRemaining = INITIAL_CHARGES;
    }

    public Position getPosition()        { return position; }
    public int      getVisibleTo()       { return visibleTo; }
    public int      getChargesRemaining(){ return chargesRemaining; }
    public boolean  isShared()           { return visibleTo == SHARED; }
    public boolean  isActive()           { return chargesRemaining > 0; }

    public boolean isVisibleTo(int playerId) {
        return visibleTo == SHARED || visibleTo == playerId;
    }

    /** Consumes exactly one charge and returns the charges left. */
    int consumeCharge() {
        if (chargesRemaining > 0) chargesRemaining--;
        return chargesRemaining;
    }
}