package engine;

import shared.Position;

/**
 * CONCEPT: Simple Data-Holder Class (Plain Old Java Object — POJO)
 * ─────────────────────────────────────────────────────────────────────────────
 * Not every class needs complex logic. Trap's job is simply to RECORD that a
 * trap exists at a certain position, placed by a certain player. Board.java
 * handles activation (removing the trap when triggered). RulesEngine handles
 * visibility logic. Trap itself just holds data.
 *
 * This is the INFORMATION EXPERT principle: a class should hold the data it
 * needs to describe itself, and other classes that need to ACT on traps (Board,
 * RulesEngine) access that data through Trap's getters.
 *
 * GAME RULES RECAP (from the spec):
 *   - Traps cost 3 coins and consume the player's entire turn.
 *   - A player can place a maximum of 3 traps TOTAL across the whole game.
 *   - A trap activates when an opponent's piece steps on it — the piece is
 *     destroyed and the trap disappears.
 *   - Exception: the King cannot be killed by a trap. If the King steps on a
 *     trap, the trap is simply removed and the King survives.
 *   - Traps are always visible to the owner.
 *   - Traps are visible to the opponent's king if within a 1-square radius
 *     (Chebyshev distance ≤ 1).
 *   - All other opponent pieces cannot see traps.
 */
public class Trap {

    // ── Fields ────────────────────────────────────────────────────────────────
    private final Position position; // Where is this trap placed on the board?
    private final int      ownerId;  // Which player placed this trap (0 or 1)?

    // ── Constructor ───────────────────────────────────────────────────────────
    public Trap(int ownerId, Position position) {
        this.ownerId  = ownerId;
        this.position = position;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Position getPosition() { return position; }
    public int      getOwnerId()  { return ownerId;  }

    // ── Visibility Logic ──────────────────────────────────────────────────────
    /**
     * Returns true if this trap should be visible to the given viewer.
     *
     * VISIBILITY RULES:
     *   1. Always visible to the trap's owner.
     *   2. Visible to the opponent's crown holder if it is within Chebyshev
     *      distance 1 (i.e., the crown holder is on an adjacent square).
     *   3. Invisible to all other opponent pieces.
     *
     * CONCEPT: Chebyshev Distance for "King Proximity"
     * Chebyshev distance counts diagonals as 1 step, matching how a King moves.
     * Distance 1 means the crown holder is on any immediately adjacent square
     * (including diagonals) — exactly the right "sensing radius" for the spec.
     *
     * @param viewerId        The player whose perspective we are checking.
     * @param crownHolderPos  The position of the viewer's crown holder.
     * @return true if this trap should be rendered visible to viewerId.
     */
    public boolean isVisibleTo(int viewerId, Position crownHolderPos) {
        // Rule 1: Always visible to the owner
        if (viewerId == ownerId) return true;

        // Rule 2: Visible to opponent's crown holder if adjacent (distance ≤ 1)
        if (crownHolderPos != null && position.chebyshevDistance(crownHolderPos) <= 1) {
            return true;
        }

        // Rule 3: Not visible
        return false;
    }

    // ── Serialization (for networking) ────────────────────────────────────────
    /**
     * Format: "TRAP|ownerId|row,col"
     * Sent over the network so both players' boards stay in sync.
     */
    public String serialize() {
        return "TRAP|" + ownerId + "|" + position.serialize();
    }

    public static Trap deserialize(String s) {
        String[] parts = s.split("\\|");
        return new Trap(Integer.parseInt(parts[1]), Position.parse(parts[2]));
    }

    @Override
    public String toString() {
        return "Trap[owner=" + ownerId + " at " + position + "]";
    }
}