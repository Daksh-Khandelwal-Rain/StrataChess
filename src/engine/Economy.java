package engine;

import java.util.Map;

/**
 * CONCEPT: Pure Functions and the "Service" Pattern
 * Economy.java is a STATELESS service: it holds no data of its own.
 * It contains only the RULES of the economy.
 *
 * StrataChess has exactly TWO income sources:
 *   1. Tribute        - the player who LOSES a piece to a normal capture
 *                       receives ceil(pieceValue / 2) coins.
 *   2. Strata Squares - entering an active Strata Square pays +1 (normal
 *                       eligible piece) or +2 (Crown Holder).
 * There is no capture bounty, passive income, or any other source.
 */
public class Economy {

    // ── Standard Piece Values ─────────────────────────────────────────────────
    // The single authoritative piece-value table. Tribute is derived from it.
    private static final Map<Piece.Type, Integer> PIECE_VALUES = Map.of(
        Piece.Type.PAWN,   1,
        Piece.Type.KNIGHT, 3,
        Piece.Type.BISHOP, 3,
        Piece.Type.ROOK,   5,
        Piece.Type.QUEEN,  9,
        Piece.Type.KING,   0
    );

    // ── Costs ─────────────────────────────────────────────────────────────────

    /** Cost to place one trap. */
    public static final int TRAP_COST = 3;

    /** Cost to execute the crown transfer. */
    public static final int CROWN_TRANSFER_COST = 5;

    // ── Strata Rewards ────────────────────────────────────────────────────────

    /** Reward when a normal eligible piece (N, B, R, Q) enters an active Strata Square. */
    public static final int STRATA_REWARD_NORMAL = 1;

    /** Reward when the Crown Holder (any piece type) enters an active Strata Square. */
    public static final int STRATA_REWARD_CROWN_HOLDER = 2;

    private Economy() {}

    // ── Piece Values / Tribute ────────────────────────────────────────────────

    /** Returns the standard value of a piece type (P=1, N=3, B=3, R=5, Q=9, K=0). */
    public static int getValueOf(Piece.Type type) {
        return PIECE_VALUES.getOrDefault(type, 0);
    }

    /** Tribute for losing a piece of this type: ceil(value / 2). */
    public static int tributeFor(Piece.Type type) {
        return (getValueOf(type) + 1) / 2;
    }

    /**
     * Awards Tribute to the player who LOST the piece in a normal capture.
     * The capturing player receives nothing. Crown Holder status is ignored:
     * Tribute always uses the underlying piece type.
     * Never call this for trap kills.
     *
     * @return the Tribute awarded.
     */
    public static int awardTribute(Piece capturedPiece, Player losingPlayer) {
        int tribute = tributeFor(capturedPiece.getType());
        losingPlayer.addCoins(tribute);
        return tribute;
    }

    // ── Strata Eligibility ────────────────────────────────────────────────────

    /**
     * Coins a piece earns for entering an active Strata Square.
     * Crown Holder overrides piece type (+2). Otherwise N/B/R/Q earn +1;
     * Pawn and a non-crown King earn 0.
     */
    public static int strataRewardFor(Piece piece) {
        if (piece.isCrownHolder()) return STRATA_REWARD_CROWN_HOLDER;
        return switch (piece.getType()) {
            case KNIGHT, BISHOP, ROOK, QUEEN -> STRATA_REWARD_NORMAL;
            default -> 0;
        };
    }

    // ── Spending ──────────────────────────────────────────────────────────────

    /** Attempts to charge the trap cost. Returns false (no deduction) if unaffordable. */
    public static boolean chargeTrapCost(Player player) {
        return player.spendCoins(TRAP_COST);
    }

    /** Charges the crown transfer cost. Returns false if unaffordable (safety guard). */
    public static boolean chargeCrownTransferCost(Player player) {
        return player.spendCoins(CROWN_TRANSFER_COST);
    }
}