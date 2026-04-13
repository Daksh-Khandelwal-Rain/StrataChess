package engine;

import java.util.Map;

/**
 * CONCEPT: Pure Functions and the "Service" Pattern
 * ─────────────────────────────────────────────────────────────────────────────
 * Economy.java is a STATELESS service — it holds no data of its own.
 * All it contains is logic: given a captured piece type, how many coins does
 * the capturing player earn?
 *
 * WHY STATELESS?
 * The actual coin BALANCE is stored in Player.java (where it belongs, since
 * coins are a per-player attribute). Economy.java just knows the RULES of
 * the economy — the exchange rates. This is called "separating policy from
 * data": the rule about "a Queen capture = 4 coins" lives here; the current
 * coin balance lives in Player.
 *
 * This makes Economy extremely easy to test and change. Want to rebalance the
 * economy (e.g., make Knights worth 3 coins)? Change one line in this file.
 * The rest of the codebase doesn't care.
 *
 * CONCEPT: Map as a Lookup Table
 * Instead of a long if-else chain:
 *   if (type == PAWN)   return 1;
 *   if (type == KNIGHT) return 2;
 *   ... etc
 *
 * We use a Map<Piece.Type, Integer> as a lookup table. This is more readable
 * and easier to modify. It also communicates intent better: "this is a table
 * of values," not "this is a sequence of conditions."
 */
public class Economy {

    // ── Coin Value Table ──────────────────────────────────────────────────────
    // Map.of() creates an IMMUTABLE map in one line.
    // 'static final' means this is a class-level constant shared by all instances.
    // It is computed once when the class is loaded and never changes.
    private static final Map<Piece.Type, Integer> COIN_VALUES = Map.of(
        Piece.Type.PAWN,   1,
        Piece.Type.KNIGHT, 2,
        Piece.Type.BISHOP, 2,
        Piece.Type.ROOK,   3,
        Piece.Type.QUEEN,  4,
        Piece.Type.KING,   0  // The King cannot normally be captured; 0 coins as a safe fallback
    );

    /** Cost of placing one trap. */
    public static final int TRAP_COST = 3;

    // ── Private Constructor ───────────────────────────────────────────────────
    /**
     * CONCEPT: Utility Class Convention
     * A "utility class" is a class with only static methods and no instance
     * state. By making the constructor private, we signal clearly: "you are
     * not supposed to create instances of this class with 'new Economy()'."
     * Java's Math class does the same thing.
     */
    private Economy() {}

    // ── Core Award Method ─────────────────────────────────────────────────────
    /**
     * Awards coins to a player for capturing an opponent's piece.
     * Returns the number of coins awarded (useful for UI feedback).
     *
     * GAME RULE: Trap kills do NOT award coins. This method should only be
     * called by Board.applyMove() when a piece is captured via movement,
     * not when triggered by a trap. The distinction is enforced at the call
     * site in GameController.
     *
     * @param capturedPiece  The piece that was captured (must not be null).
     * @param capturingPlayer The player who made the capture.
     * @return The number of coins awarded.
     */
    public static int awardForCapture(Piece capturedPiece, Player capturingPlayer) {
        int coins = COIN_VALUES.getOrDefault(capturedPiece.getType(), 0);
        capturingPlayer.addCoins(coins);
        return coins;
    }

    /**
     * Attempts to charge a player the cost of placing a trap.
     * Returns true if the player had enough coins (and deducts them).
     * Returns false if the player cannot afford it (no deduction made).
     *
     * @param player The player attempting to buy a trap.
     * @return true if the purchase succeeded.
     */
    public static boolean chargeTrapCost(Player player) {
        return player.spendCoins(TRAP_COST);
    }

    /**
     * Returns the coin reward for capturing a given piece type.
     * Useful for the GUI to show tooltips like "capture this Queen for 4 coins."
     */
    public static int getValueOf(Piece.Type type) {
        return COIN_VALUES.getOrDefault(type, 0);
    }
}