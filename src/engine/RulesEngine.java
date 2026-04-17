package engine;

import shared.Action;
import shared.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: Pure Functions and Separation of Concerns
 * RulesEngine stores NO state — every method takes a Board and returns a result.
 * It never modifies anything. These are PURE FUNCTIONS.
 */
public class RulesEngine {

    private RulesEngine() {}

    // ── Check Detection ───────────────────────────────────────────────────────

    /**
     * Returns true if the given player's crown holder is currently in check.
     * Uses pseudo-legal opponent moves to avoid infinite recursion.
     */
    public static boolean isInCheck(Board board, int playerId, Player player) {
        Position crownPos = player.getCrownPosition();
        if (crownPos == null) return false;

        int opponentId = 1 - playerId;
        for (Piece opp : board.getPiecesFor(opponentId)) {
            List<Position> attacks = opp.getValidMoves(board);
            if (attacks.contains(crownPos)) return true;
        }
        return false;
    }

    // ── Legal Move Filtering ──────────────────────────────────────────────────

    /**
     * Filters a piece's raw moves to only those that don't leave the
     * crown holder in check (the King Safety Filter).
     */
    public static List<Position> filterLegalMoves(Board board, Piece piece, Player player) {
        List<Position> pseudoLegal = piece.getValidMoves(board);
        List<Position> legal       = new ArrayList<>();

        for (Position target : pseudoLegal) {
            Board simulated = new Board(board);
            simulated.applyMove(piece.getPosition(), target);

            Player simulatedPlayer = new Player(player.getId(), player.getName());
            simulatedPlayer.setCrownPosition(player.getCrownPosition());
            if (piece.isCrownHolder()) {
                simulatedPlayer.setCrownPosition(target);
            }

            if (!isInCheck(simulated, player.getId(), simulatedPlayer)) {
                legal.add(target);
            }
        }

        return legal;
    }

    // ── Checkmate Detection ───────────────────────────────────────────────────

    /**
     * Returns true if the given player is in CHECKMATE:
     * in check AND no legal move escapes it.
     */
    public static boolean isCheckmate(Board board, int playerId, Player player) {
        if (!isInCheck(board, playerId, player)) return false;
        return hasNoLegalMoves(board, playerId, player);
    }

    private static boolean hasNoLegalMoves(Board board, int playerId, Player player) {
        for (Piece piece : board.getPiecesFor(playerId)) {
            if (!filterLegalMoves(board, piece, player).isEmpty()) return false;
        }
        return true;
    }

    // ── Action Validation ─────────────────────────────────────────────────────

    /**
     * The single validation gate — every action passes through here before
     * being applied to real game state.
     */
    public static boolean isLegalAction(Action action, Board board,
                                         Player[] players, int totalTurns) {
        Player actor = players[action.playerId];
        return switch (action.type) {
            case MOVE           -> isLegalMove(action, board, actor);
            case PLACE_TRAP     -> isLegalTrapPlacement(action, board, actor, totalTurns);
            case CROWN_TRANSFER -> isLegalCrownTransfer(action, board, actor, players);
            default             -> false;
        };
    }

    // ── Private Validation Helpers ────────────────────────────────────────────

    private static boolean isLegalMove(Action action, Board board, Player actor) {
        Piece piece = board.getPieceAt(action.from);
        if (piece == null || piece.getOwnerId() != actor.getId()) return false;
        return filterLegalMoves(board, piece, actor).contains(action.to);
    }

    private static boolean isLegalTrapPlacement(Action action, Board board,
                                                  Player actor, int totalTurns) {
        if (actor.getCoins() < Economy.TRAP_COST)   return false;
        if (!actor.canPlaceTrap())                   return false;
        if (board.getPieceAt(action.to) != null)     return false;
        if (board.getTrapAt(action.to)  != null)     return false;
        return isInPermittedTerritory(action.to, actor.getId(), totalTurns);
    }

    private static boolean isLegalCrownTransfer(Action action, Board board,
                                                  Player actor, Player[] players) {
        // One-time only
        if (actor.hasCrownTransferUsed()) return false;

        // Cannot use while in check
        if (isInCheck(board, actor.getId(), actor)) return false;

        /**
         * FIX: Crown transfer now requires CROWN_TRANSFER_COST coins (5).
         * This is validated here so the engine rejects it if they can't afford it,
         * just like it rejects trap placement when coins are insufficient.
         */
        if (actor.getCoins() < Economy.CROWN_TRANSFER_COST) return false;

        // 'from' must be the current crown holder belonging to the actor
        Piece fromPiece = board.getPieceAt(action.from);
        if (fromPiece == null || !fromPiece.isCrownHolder())     return false;
        if (fromPiece.getOwnerId() != actor.getId())             return false;

        // 'to' must be a non-pawn, non-crown piece belonging to the actor
        Piece toPiece = board.getPieceAt(action.to);
        if (toPiece == null)                              return false;
        if (toPiece.getOwnerId() != actor.getId())        return false;
        if (toPiece.getType() == Piece.Type.PAWN)         return false;
        if (toPiece.isCrownHolder())                      return false;

        return true;
    }

    /**
     * Returns true if the given position is within the player's allowed trap
     * placement zone, which shrinks every 15 total turns.
     *
     * Phase 1 (turns  0-14): 4 rows
     * Phase 2 (turns 15-29): 3 rows
     * Phase 3 (turns 30-44): 2 rows
     * Phase 4 (turns 45+):   1 row (home row only)
     */
    private static boolean isInPermittedTerritory(Position pos, int playerId, int totalTurns) {
        int phase       = Math.min(totalTurns / 15, 3);
        int rowsAllowed = 4 - phase;

        if (playerId == 0) {
            // White: home row is 7, territory extends upward
            return pos.row >= (8 - rowsAllowed) && pos.row <= 7;
        } else {
            // Black: home row is 0, territory extends downward
            return pos.row >= 0 && pos.row <= (rowsAllowed - 1);
        }
    }
}