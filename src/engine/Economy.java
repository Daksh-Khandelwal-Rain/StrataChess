package engine;

import java.util.Map;

/**
 * CONCEPT: Pure Functions and the "Service" Pattern
 * Economy.java is a STATELESS service — it holds no data of its own.
 * All it contains is the RULES of the economy: exchange rates and costs.
 */
public class Economy {

    // ── Coin Value Table ──────────────────────────────────────────────────────
    private static final Map<Piece.Type, Integer> COIN_VALUES = Map.of(
        Piece.Type.PAWN,   1,
        Piece.Type.KNIGHT, 2,
        Piece.Type.BISHOP, 2,
        Piece.Type.ROOK,   3,
        Piece.Type.QUEEN,  4,
        Piece.Type.KING,   0
    );

    /** Cost to place one mine (trap). */
    public static final int TRAP_COST = 3;

    /**
     * FIX: Crown transfer now costs 5 coins (was free).
     * This makes it a meaningful late-game decision, not a free escape.
     * You need to earn it through captures before using it as a strategic trump card.
     */
    public static final int CROWN_TRANSFER_COST = 5;

    private Economy() {}

    // ── Core Methods ──────────────────────────────────────────────────────────

    /**
     * Awards coins to a player for capturing an opponent's piece via movement.
     * Trap kills do NOT award coins — only direct captures do.
     */
    public static int awardForCapture(Piece capturedPiece, Player capturingPlayer) {
        int coins = COIN_VALUES.getOrDefault(capturedPiece.getType(), 0);
        capturingPlayer.addCoins(coins);
        return coins;
    }

    /**
     * Attempts to charge a player the trap placement cost.
     * Returns false (no deduction) if they can't afford it.
     */
    public static boolean chargeTrapCost(Player player) {
        return player.spendCoins(TRAP_COST);
    }

    /**
     * FIX: Charges the crown transfer cost (5 coins).
     * Called by Game.handleCrownTransfer after validation succeeds.
     * Returns false if the player somehow can't afford it (safety guard).
     */
    public static boolean chargeCrownTransferCost(Player player) {
        return player.spendCoins(CROWN_TRANSFER_COST);
    }

    /** Returns the coin reward for capturing a given piece type (used by UI tooltips). */
    public static int getValueOf(Piece.Type type) {
        return COIN_VALUES.getOrDefault(type, 0);
    }
}