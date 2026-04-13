package engine.pieces;

import engine.Board;
import engine.Piece;
import shared.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: Jump Movement — Why the Knight is Special
 * ─────────────────────────────────────────────────────────────────────────────
 * The Knight is the only piece in chess that JUMPS. It does not slide —
 * it moves in an "L-shape": two squares in one direction and one square
 * perpendicular. Critically, pieces in between DO NOT block it. This is
 * why we DON'T use the slide() helper here.
 *
 * The eight possible L-shape offsets are:
 *   (-2,-1), (-2,+1)  — two up, one left/right
 *   (+2,-1), (+2,+1)  — two down, one left/right
 *   (-1,-2), (-1,+2)  — one up, two left/right
 *   (+1,-2), (+1,+2)  — one down, two left/right
 *
 * This is another case where representing directions as a 2D array of
 * offsets dramatically simplifies the code. Without this pattern, you
 * would write eight separate if-statements — eight chances to make an
 * off-by-one error. With the array, you write the logic once and loop.
 *
 * ALGORITHM NOTE: Unlike sliding pieces, Knight movement is just a single
 * check per offset — no loop needed. Just compute the target square and
 * check if it's on the board and not occupied by a friendly piece.
 */
public class Knight extends Piece {

    // All 8 possible L-shaped jump offsets {dRow, dCol}
    private static final int[][] JUMPS = {
        {-2, -1}, {-2, +1}, // two up
        {+2, -1}, {+2, +1}, // two down
        {-1, -2}, {-1, +2}, // one up
        {+1, -2}, {+1, +2}  // one down
    };

    public Knight(int ownerId, Position position) {
        super(Type.KNIGHT, ownerId, position);
    }

    @Override
    public List<Position> getValidMoves(Board board) {
        List<Position> moves = new ArrayList<>();

        for (int[] jump : JUMPS) {
            Position target = new Position(position.row + jump[0], position.col + jump[1]);

            // Knight can jump to: any on-board square that isn't occupied by a friendly piece.
            // Note: NO check for pieces in between — Knights jump over everything.
            if (target.isOnBoard() && (isEmpty(target, board) || isOccupiedByOpponent(target, board))) {
                moves.add(target);
            }
        }

        return moves;
    }
}