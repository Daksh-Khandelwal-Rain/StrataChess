package engine;

import shared.Action;
import shared.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: Pure Functions and Separation of Concerns
 * ─────────────────────────────────────────────────────────────────────────────
 * RulesEngine is the brain of the game — but it stores NO state.
 * Every method takes a Board (and sometimes Players) as input, analyses it,
 * and returns a result. It never modifies anything. These are called PURE
 * FUNCTIONS: same input always produces the same output, with no side effects.
 *
 * WHY PURE FUNCTIONS FOR RULES?
 * Because the most critical operation in chess — detecting check — requires
 * SIMULATING a move without actually making it. If RulesEngine modified the
 * real board, you'd corrupt game state during validation. Instead, each rule
 * check creates a temporary Board copy, tests a hypothesis on it, and discards it.
 * The real board is never touched during validation.
 *
 * Think of it like a weather forecasting model: the model simulates "what if
 * it rains?" without actually making it rain. RulesEngine does the same for
 * chess moves.
 *
 * HOW CHECK DETECTION WORKS:
 * A king is in check if ANY opponent piece can move to the king's square.
 * To test this, we call getValidMoves() on each opponent piece and see if
 * any of them includes the crown holder's position. Simple and correct.
 *
 * HOW CHECKMATE DETECTION WORKS:
 * A crown holder is in checkmate if:
 *   1. It is currently in check, AND
 *   2. There is NO legal move that removes the check.
 * We test (2) by trying every possible move for every friendly piece, simulating
 * each one, and checking if ANY move leaves the crown holder out of check.
 * If none do — it's checkmate.
 */
public class RulesEngine {

    // Private constructor — pure utility class, never instantiated
    private RulesEngine() {}

    // ── Check Detection ───────────────────────────────────────────────────────

    /**
     * Returns true if the given player's crown holder is currently in check
     * on the given board state.
     *
     * ALGORITHM:
     *   For every opponent piece, get its pseudo-legal moves.
     *   If ANY of those moves targets the crown holder's position → in check.
     *
     * We use "pseudo-legal" moves here (raw piece moves, not filtered for
     * self-check) intentionally. If we used fully legal moves, we'd get
     * infinite recursion: checking for check would call filterLegalMoves,
     * which calls isInCheck, which calls filterLegalMoves... forever.
     *
     * @param board    The board state to analyse.
     * @param playerId The player whose crown holder we are checking for safety.
     * @return true if the crown holder is under attack.
     */
    public static boolean isInCheck(Board board, int playerId, Player player) {
        Position crownPos = player.getCrownPosition();
        if (crownPos == null) return false;

        int opponentId = 1 - playerId; // If playerId is 0, opponent is 1, and vice versa

        // Check if any opponent piece can attack the crown position
        for (Piece opp : board.getPiecesFor(opponentId)) {
            List<Position> attacks = opp.getValidMoves(board); // pseudo-legal moves
            if (attacks.contains(crownPos)) {
                return true; // Crown holder is under attack
            }
        }
        return false;
    }

    // ── Legal Move Filtering ──────────────────────────────────────────────────

    /**
     * CONCEPT: The King Safety Filter
     * ─────────────────────────────────────────────────────────────────────────
     * A chess move is only "legal" if it doesn't leave your own king (or crown
     * holder) in check after the move. This method takes a piece's raw moves
     * and filters out any that would result in self-check.
     *
     * HOW IT WORKS — for each candidate move:
     *   1. Create a COPY of the board (using the copy constructor)
     *   2. Apply the move to the copy
     *   3. Check if the crown holder is in check on the copy
     *   4. If yes → this move is illegal, discard it
     *   5. If no  → this move is legal, keep it
     *
     * This is the classic "try it and see" approach. It's slightly expensive
     * (copies the board for every candidate move) but it's correct and clear.
     * For a learning project, correctness first — optimize later if needed.
     *
     * @param board    The real (unmodified) board state.
     * @param piece    The piece whose moves we are filtering.
     * @param player   The piece's owner (for crown position tracking).
     * @return         A list of positions this piece can legally move to.
     */
    public static List<Position> filterLegalMoves(Board board, Piece piece, Player player) {
        List<Position> pseudoLegal = piece.getValidMoves(board);
        List<Position> legal       = new ArrayList<>();

        for (Position target : pseudoLegal) {
            // Step 1: Clone the board — we never touch the real board during testing
            Board simulated = new Board(board);

            // Step 2: Apply the move to the clone
            simulated.applyMove(piece.getPosition(), target);

            // Step 3: Update the crown position on the simulated player state
            // if the moving piece IS the crown holder (it just moved to 'target')
            Player simulatedPlayer = new Player(player.getId(), player.getName());
            simulatedPlayer.setCrownPosition(player.getCrownPosition());
            if (piece.isCrownHolder()) {
                simulatedPlayer.setCrownPosition(target);
            }

            // Step 4: Check if the crown holder is in check after the move
            if (!isInCheck(simulated, player.getId(), simulatedPlayer)) {
                legal.add(target); // This move is safe — keep it
            }
            // If isInCheck is true, we discard this target (don't add to 'legal')
        }

        return legal;
    }

    // ── Checkmate and Stalemate Detection ─────────────────────────────────────

    /**
     * Returns true if the given player is in CHECKMATE:
     *   - Their crown holder is currently in check, AND
     *   - They have no legal move that escapes check.
     *
     * CONCEPT: Exhaustive Search (Brute Force)
     * For each of the player's pieces, we generate all legal moves (not just
     * pseudo-legal — legal means already filtered for self-check). If the total
     * across all pieces is zero, and the player is in check → checkmate.
     *
     * In a real chess engine, this would be too slow for complex position analysis,
     * but for validating "is the game over?" at the end of a turn, it's called
     * only once and is perfectly fast.
     */
    public static boolean isCheckmate(Board board, int playerId, Player player) {
        // Must be in check for it to be checkmate (not stalemate)
        if (!isInCheck(board, playerId, player)) return false;

        // Check if any piece has any legal move
        return hasNoLegalMoves(board, playerId, player);
    }

    /**
     * Returns true if the player has absolutely no legal moves.
     * If they're NOT in check but have no moves, that's stalemate (a draw).
     * If they ARE in check and have no moves, that's checkmate (a loss).
     */
    private static boolean hasNoLegalMoves(Board board, int playerId, Player player) {
        for (Piece piece : board.getPiecesFor(playerId)) {
            List<Position> legalMoves = filterLegalMoves(board, piece, player);
            if (!legalMoves.isEmpty()) return false; // Found at least one legal move — not stuck
        }
        return true; // No piece has any legal move
    }

    // ── Action Validation ─────────────────────────────────────────────────────

    /**
     * CONCEPT: The Validation Gate
     * Before any action is applied to the real game state, it passes through
     * here. This method is the single guard that enforces all game rules.
     * If this returns true, the action is safe to apply. If false, it is rejected.
     *
     * Centralising validation here means:
     *   - The GUI never has to implement rule logic (it just shows the result)
     *   - The network layer submits actions and trusts the server to validate them
     *   - Rules can be changed in one place without touching other layers
     *
     * @param action   The action being attempted.
     * @param board    The current board state.
     * @param players  Both players (index 0 = White, index 1 = Black).
     * @param totalTurns  Total turns elapsed (used for territory shrink).
     * @return true if the action is legal and can be applied.
     */
    public static boolean isLegalAction(Action action, Board board,
                                         Player[] players, int totalTurns) {
        Player actor = players[action.playerId];

        switch (action.type) {
            case MOVE:
                return isLegalMove(action, board, actor);

            case PLACE_TRAP:
                return isLegalTrapPlacement(action, board, actor, totalTurns);

            case CROWN_TRANSFER:
                return isLegalCrownTransfer(action, board, actor, players);

            default:
                return false;
        }
    }

    // ── Private Validation Helpers ────────────────────────────────────────────

    private static boolean isLegalMove(Action action, Board board, Player actor) {
        Piece piece = board.getPieceAt(action.from);

        // The piece must exist and belong to the actor
        if (piece == null || piece.getOwnerId() != actor.getId()) return false;

        // The destination must be in the piece's legal moves (already filtered for check)
        List<Position> legal = filterLegalMoves(board, piece, actor);
        return legal.contains(action.to);
    }

    private static boolean isLegalTrapPlacement(Action action, Board board,
                                                  Player actor, int totalTurns) {
        // Rule: must have enough coins
        if (actor.getCoins() < Economy.TRAP_COST) return false;

        // Rule: must not have exceeded the lifetime trap limit
        if (!actor.canPlaceTrap()) return false;

        // Rule: destination must be an empty square
        if (board.getPieceAt(action.to) != null) return false;

        // Rule: destination must not already have a trap
        if (board.getTrapAt(action.to) != null) return false;

        // Rule: destination must be in the player's permitted territory
        if (!isInPermittedTerritory(action.to, actor.getId(), totalTurns)) return false;

        return true;
    }

    private static boolean isLegalCrownTransfer(Action action, Board board,
                                                  Player actor, Player[] players) {
        // Rule: can only be used once per game
        if (actor.hasCrownTransferUsed()) return false;

        // Rule: cannot be used while in check
        if (isInCheck(board, actor.getId(), actor)) return false;

        // Rule: 'from' piece must be the current crown holder
        Piece fromPiece = board.getPieceAt(action.from);
        if (fromPiece == null || !fromPiece.isCrownHolder()) return false;
        if (fromPiece.getOwnerId() != actor.getId()) return false;

        // Rule: 'to' piece must be a non-pawn, non-crown piece belonging to this player
        Piece toPiece = board.getPieceAt(action.to);
        if (toPiece == null) return false;
        if (toPiece.getOwnerId() != actor.getId()) return false;
        if (toPiece.getType() == Piece.Type.PAWN) return false;
        if (toPiece.isCrownHolder()) return false; // can't transfer to itself

        return true;
    }

    /**
     * Returns true if the given position is within the player's allowed trap
     * placement zone, which shrinks every 15 total turns.
     *
     * White (player 0) starts at the bottom (rows 4-7 = their territory).
     * Black (player 1) starts at the top  (rows 0-3 = their territory).
     *
     * The zone shrinks inward toward the player's home row:
     *   Phase 1 (turns  0-14): 4 rows available
     *   Phase 2 (turns 15-29): 3 rows
     *   Phase 3 (turns 30-44): 2 rows
     *   Phase 4 (turns 45+):   1 row (home row only)
     */
    private static boolean isInPermittedTerritory(Position pos, int playerId, int totalTurns) {
        int phase = Math.min(totalTurns / 15, 3); // cap at phase 3 (index 3 = 4th phase)
        int rowsAllowed = 4 - phase; // 4, 3, 2, 1

        if (playerId == 0) {
            // White: home row is 7, territory extends upward
            int topOfZone = 8 - rowsAllowed; // e.g., 4 rows → topOfZone = 4
            return pos.row >= topOfZone && pos.row <= 7;
        } else {
            // Black: home row is 0, territory extends downward
            int bottomOfZone = rowsAllowed - 1; // e.g., 4 rows → bottomOfZone = 3
            return pos.row >= 0 && pos.row <= bottomOfZone;
        }
    }
}