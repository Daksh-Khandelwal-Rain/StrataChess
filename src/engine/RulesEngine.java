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
    public static boolean isLegalAction(Action action, Board board, Player[] players) {

        Player actor = players[action.playerId];                                    
        if (action == null) return false;
        if (action.playerId < 0 || action.playerId >= players.length) return false;
        return switch (action.type) {
            case MOVE           -> isLegalMove(action, board, actor);
            case PLACE_TRAP     -> isLegalTrapPlacement(action, board, actor);
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

    private static boolean isLegalTrapPlacement(
            Action action,
            Board board,
            Player actor
    ) {
        if (!actor.canPlaceTrap()) {
            return false;
        }

        if (actor.getCoins() < Economy.TRAP_COST) {
            return false;
        }

        if (action.to == null || !action.to.isOnBoard()) {
            return false;
        }

        if (!isInPermittedTerritory(
                action.to,
                actor.getId(),
                actor.getTurnsTaken()
        )) {
            return false;
        }

        // Traps cannot be placed under pieces.
        if (board.getPieceAt(action.to) != null) {
            return false;
        }

        // Own trap = invalid.
        // Enemy trap = valid, because it triggers the trap-vs-trap collision.
        Trap existing = board.getTrapAt(action.to);

        return existing == null || existing.getOwnerId() != actor.getId();
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
     * Returns true if the position is within the player's current trap
     * deployment territory.
     *
     * The territory starts near the player's own side and expands by one
     * row toward the opponent after every 8 completed turns by that player.
     *
     * White starts with rows 5-6 and expands toward row 1.
     * Black starts with rows 1-2 and expands toward row 6.
     */
    private static boolean isInPermittedTerritory(
        Position pos,
        int playerId,
        int turnsTaken
    ) {
        if (pos == null || !pos.isOnBoard()) {
            return false;
        }

        int expansion = turnsTaken / 8;

        // White starts at ranks 3-4 and expands toward rank 8.
        if (playerId == 0) {
            int minRow = Math.max(0, 5 - expansion);
            return pos.row >= minRow && pos.row <= 5;
        }

        // Black starts at ranks 5-6 and expands toward rank 1.
        int maxRow = Math.min(7, 2 + expansion);
        return pos.row >= 2 && pos.row <= maxRow;
    }

    public static boolean isStalemate(Board board, int playerId, Player player) {
        // A player in check cannot be stalemated.
        if (isInCheck(board, playerId, player)) {
            return false;
        }
        
        // Not in check + no legal moves = stalemate.
        return hasNoLegalMoves(board, playerId, player);
    }
}