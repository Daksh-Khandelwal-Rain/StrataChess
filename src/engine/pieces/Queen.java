package engine.pieces;

import engine.Board;
import engine.Piece;
import shared.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: Code Reuse via Inheritance + the Sliding Algorithm
 * ─────────────────────────────────────────────────────────────────────────────
 * The Queen is the most powerful piece — she slides in all 8 directions
 * (4 orthogonal + 4 diagonal) as far as the board allows.
 *
 * Notice how short this class is. That's intentional. All the sliding
 * logic lives in Piece.slide(), which we inherited. Queen's getValidMoves()
 * simply calls slide() with each of the 8 direction vectors.
 *
 * This is a core benefit of inheritance and the DRY principle: the sliding
 * algorithm is written ONCE in Piece.java. Rook, Bishop, and Queen all
 * use it without duplicating a single line of logic.
 *
 * Think of it this way: the Queen IS a Rook AND a Bishop combined.
 * Rather than copy-pasting movement code, we REUSE the mechanism.
 */
public class Queen extends Piece {

    public Queen(int ownerId, Position position) {
        super(Type.QUEEN, ownerId, position);
    }

    @Override
    public List<Position> getValidMoves(Board board) {
        List<Position> moves = new ArrayList<>();

        // 4 orthogonal directions (same as Rook)
        slide(moves, board, -1,  0); // up
        slide(moves, board, +1,  0); // down
        slide(moves, board,  0, -1); // left
        slide(moves, board,  0, +1); // right

        // 4 diagonal directions (same as Bishop)
        slide(moves, board, -1, -1); // up-left
        slide(moves, board, -1, +1); // up-right
        slide(moves, board, +1, -1); // down-left
        slide(moves, board, +1, +1); // down-right

        return moves;
    }
}