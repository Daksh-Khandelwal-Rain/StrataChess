package engine.pieces;

import engine.Board;
import engine.Piece;
import shared.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: Diagonal Sliding Piece
 * ─────────────────────────────────────────────────────────────────────────────
 * The Bishop slides diagonally in four directions. Compare this with Rook:
 * the implementation is structurally IDENTICAL — only the direction vectors
 * differ. This confirms that slide() is a well-designed abstraction:
 * it encapsulates the HOW (keep going until blocked), while the caller
 * only specifies the WHERE (which direction to go).
 *
 * A fun property of diagonal movement: a Bishop starting on a light square
 * ALWAYS stays on light squares, and vice versa. This emerges naturally
 * from the math — adding equal deltas to both row and col always flips
 * the parity of (row+col), keeping the color consistent. You don't need
 * to code this rule — the geometry handles it automatically.
 */
public class Bishop extends Piece {

    public Bishop(int ownerId, Position position) {
        super(Type.BISHOP, ownerId, position);
    }

    @Override
    public List<Position> getValidMoves(Board board) {
        List<Position> moves = new ArrayList<>();
        // The four diagonal directions — always both row AND col change together
        slide(moves, board, -1, -1); // up-left
        slide(moves, board, -1, +1); // up-right
        slide(moves, board, +1, -1); // down-left
        slide(moves, board, +1, +1); // down-right
        return moves;
    }
}