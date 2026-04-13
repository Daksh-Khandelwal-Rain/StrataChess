package engine.pieces;

import engine.Board;
import engine.Piece;
import shared.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: The Most Rule-Dense Piece — Conditional Logic in Clean Code
 * ─────────────────────────────────────────────────────────────────────────────
 * Despite being the weakest piece, the Pawn has the most movement conditions:
 *
 *   1. It moves FORWARD only — direction depends on which player owns it.
 *      Player 0 (White, starting at bottom): moves UP → dRow = -1
 *      Player 1 (Black, starting at top):    moves DOWN → dRow = +1
 *
 *   2. It moves ONE square forward onto an EMPTY square (no capturing forward).
 *
 *   3. It may move TWO squares forward on its FIRST MOVE ONLY, if both
 *      the intermediate and destination squares are empty.
 *
 *   4. It captures DIAGONALLY forward — one square to the left or right
 *      of its forward direction, but ONLY if an opponent piece is there.
 *      (In standard chess, a pawn can never capture the square directly ahead.)
 *
 *   5. Upon reaching the back rank (row 0 for White, row 7 for Black),
 *      it promotes automatically to a Queen in this implementation.
 *      Promotion is handled by Board.java when it applies the move.
 *
 * WHY IS DIRECTION DERIVED FROM OWNER ID?
 * We could store a "direction" field in each Pawn, but that would mean
 * the caller has to remember to set it correctly at construction time.
 * It's cleaner and safer to DERIVE it from ownerId — we already know
 * player 0 is at the bottom (high row numbers) and moves toward row 0.
 * Deriving eliminates a whole category of bug where the direction is set wrong.
 *
 * NOTE ON PROMOTION:
 * getValidMoves() simply returns the pawn's reachable squares, including
 * promotion squares. The actual promotion (replacing the Pawn with a Queen
 * on the board) is applied in Board.applyMove() after the move is validated.
 * Separation of concerns: Pawn says where it CAN go; Board handles what
 * HAPPENS when it gets there.
 */
public class Pawn extends Piece {

    public Pawn(int ownerId, Position position) {
        super(Type.PAWN, ownerId, position);
    }

    @Override
    public List<Position> getValidMoves(Board board) {
        List<Position> moves = new ArrayList<>();

        // ── 1. Determine forward direction ────────────────────────────────────
        // Player 0 (White) is set up at rows 6-7 and moves toward row 0 (up the board).
        // Player 1 (Black) is set up at rows 0-1 and moves toward row 7 (down the board).
        int forward = (ownerId == 0) ? -1 : +1;

        // The row this pawn starts on — used to determine if it can move 2 squares.
        // White pawns start at row 6, Black pawns at row 1.
        int startRow = (ownerId == 0) ? 6 : 1;

        // ── 2. One square forward (non-capturing) ─────────────────────────────
        Position oneStep = new Position(position.row + forward, position.col);
        if (oneStep.isOnBoard() && isEmpty(oneStep, board)) {
            moves.add(oneStep);

            // ── 3. Two squares forward (only from starting row, only if path clear) ──
            // The 'oneStep' square must be empty (checked above) AND we must still
            // be on the starting row (hasMoved is false only on the first turn).
            if (!hasMoved) {
                Position twoStep = new Position(position.row + (2 * forward), position.col);
                if (twoStep.isOnBoard() && isEmpty(twoStep, board)) {
                    moves.add(twoStep);
                }
            }
        }

        // ── 4. Diagonal captures (only if an opponent piece is there) ─────────
        // Pawns can ONLY capture diagonally, never straight ahead.
        // They also CANNOT move diagonally unless there is an opponent to capture.
        int[] captureCols = { position.col - 1, position.col + 1 }; // left and right diagonals

        for (int captureCol : captureCols) {
            Position captureSquare = new Position(position.row + forward, captureCol);
            if (captureSquare.isOnBoard() && isOccupiedByOpponent(captureSquare, board)) {
                moves.add(captureSquare);
            }
        }

        // En passant is NOT implemented in this version (per the game spec).
        // If it were, we would add logic here to check the last move made on
        // the board and allow diagonal capture of an adjacent pawn that just
        // moved two squares.

        return moves;
    }

    /**
     * Returns true if this pawn has reached the promotion rank.
     * White promotes at row 0; Black promotes at row 7.
     * Called by Board.applyMove() to decide whether to promote.
     */
    public boolean hasReachedPromotion() {
        int promotionRow = (ownerId == 0) ? 0 : 7;
        return position.row == promotionRow;
    }
}