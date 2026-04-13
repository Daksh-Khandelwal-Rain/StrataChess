package engine.pieces;

import engine.Board;
import engine.Piece;
import shared.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: Orthogonal Sliding Piece
 * ─────────────────────────────────────────────────────────────────────────────
 * The Rook slides along ranks (rows) and files (columns) — the four
 * orthogonal compass directions. It slides until it hits the board edge
 * or another piece, exactly as Piece.slide() implements.
 *
 * Notice the symmetry with Bishop.java: the only difference is the four
 * direction vectors passed to slide(). Rook uses cardinal directions
 * (up/down/left/right); Bishop uses diagonal directions. Queen uses both.
 * The algorithm is identical — only the input changes. This is a powerful
 * lesson in abstraction: parameterise what varies, share what stays constant.
 */
public class Rook extends Piece {

    public Rook(int ownerId, Position position) {
        super(Type.ROOK, ownerId, position);
    }

    @Override
    public List<Position> getValidMoves(Board board) {
        List<Position> moves = new ArrayList<>();
        // The four orthogonal directions — rows/cols only, no diagonals
        slide(moves, board, -1,  0); // up (decreasing row index)
        slide(moves, board, +1,  0); // down (increasing row index)
        slide(moves, board,  0, -1); // left (decreasing col index)
        slide(moves, board,  0, +1); // right (increasing col index)
        return moves;
    }
}