package engine.pieces;

import engine.Board;
import engine.Piece;
import shared.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: Concrete Subclass + Single-Step Movement
 * ─────────────────────────────────────────────────────────────────────────────
 * The King can move exactly ONE square in any of 8 directions (orthogonal
 * and diagonal). Unlike sliding pieces (Rook, Bishop, Queen), it does not
 * continue past that one step. This is called "step movement."
 *
 * We express the 8 directions as an array of (dRow, dCol) deltas — this
 * is a classic programming technique for board games. Instead of writing
 * eight separate if-statements, we loop over the 8 direction pairs.
 *
 * IMPORTANT — What getValidMoves() does NOT check here:
 * It does not filter out squares that would put the King in check.
 * That responsibility belongs entirely to RulesEngine.filterLegalMoves().
 * The reason is that "am I moving into check?" requires simulating the
 * entire board after the move — that's RulesEngine's job. Each piece class
 * only answers: "where can I physically reach?"
 */
public class King extends Piece {

    // ── The 8 directions a King can move ──────────────────────────────────────
    // Each row in this 2D array is one direction: {dRow, dCol}.
    // dRow: -1 = up,   0 = same row, +1 = down
    // dCol: -1 = left, 0 = same col, +1 = right
    private static final int[][] DIRECTIONS = {
        {-1, -1}, {-1, 0}, {-1, +1},  // up-left, up, up-right
        { 0, -1},           { 0, +1},  // left,        right
        {+1, -1}, {+1, 0}, {+1, +1}   // down-left, down, down-right
    };

    public King(int ownerId, Position position) {
        // 'super(...)' calls the Piece constructor — always the first line in a subclass constructor.
        super(Type.KING, ownerId, position);
    }

    @Override
    public List<Position> getValidMoves(Board board) {
        List<Position> moves = new ArrayList<>();

        // Loop over all 8 possible directions
        for (int[] dir : DIRECTIONS) {
            Position target = new Position(position.row + dir[0], position.col + dir[1]);

            // Only consider squares that are actually on the board
            if (!target.isOnBoard()) continue;

            // The King can move to: an empty square, or an occupied square if it's an opponent
            // (to capture). It cannot move onto a square with its own piece.
            if (isEmpty(target, board) || isOccupiedByOpponent(target, board)) {
                moves.add(target);
            }
        }

        return moves;
        // Remember: RulesEngine will later remove any of these that result in check.
    }
}