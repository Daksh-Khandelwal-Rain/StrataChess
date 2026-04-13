package engine;

import engine.pieces.*;
import shared.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: The Model — Single Source of Truth
 * ─────────────────────────────────────────────────────────────────────────────
 * Board.java answers exactly one question: "What is at square (row, col)?"
 *
 * It does NOT know about rules. It does NOT know about networking. It does NOT
 * draw itself. Its ONLY job is to store the 8×8 grid of pieces and the list of
 * placed traps, and to apply moves when the controller tells it to.
 *
 * WHY IS THIS SEPARATION IMPORTANT?
 * The RulesEngine needs to SIMULATE board states — "if I make this move, does
 * my king end up in check?" To simulate, it creates a COPY of the board, applies
 * a hypothetical move to the copy, and checks the result. If Board had rules
 * logic embedded in it, making a clean copy would be a nightmare. Keeping Board
 * as pure data storage makes copying simple and simulation safe.
 *
 * COORDINATE SYSTEM:
 * Row 0 = top of the board (Black's back rank in standard setup)
 * Row 7 = bottom of the board (White's back rank)
 * Col 0 = left (the 'a' file), Col 7 = right (the 'h' file)
 *
 * 2D ARRAY INDEXING:
 * grid[row][col] — always row first, then column.
 * This matches how matrices are described in math (row, column)
 * and is the universal convention in board-game programming.
 */
public class Board {

    // ── The Grid ──────────────────────────────────────────────────────────────
    // A 2D array of Piece references. null means the square is empty.
    // CONCEPT: null as "absence of value" — a common Java idiom, though
    // modern code often uses Optional<Piece> to be more explicit. For a
    // learning project, null is cleaner to read and understand.
    private final Piece[][] grid;

    // ── Traps ─────────────────────────────────────────────────────────────────
    // Traps are stored separately from pieces because they are hidden —
    // they don't render like pieces and they don't move. A simple List works
    // perfectly here; we never need random access by position (we scan the
    // list when checking if a square has a trap).
    private final List<Trap> traps;

    // ── Constructor ───────────────────────────────────────────────────────────
    /** Creates an empty 8×8 board with no pieces or traps. */
    public Board() {
        this.grid  = new Piece[8][8]; // Java initializes all elements to null
        this.traps = new ArrayList<>();
    }

    // ── Deep Copy Constructor ─────────────────────────────────────────────────
    /**
     * CONCEPT: Copy Constructor for Safe Simulation
     * ─────────────────────────────────────────────────────────────────────────
     * This is the KEY that makes check-detection possible.
     *
     * To check if a move results in check, RulesEngine does:
     *   Board simulated = new Board(realBoard);   // copy
     *   simulated.applyMove(hypotheticalAction);  // mutate the copy
     *   boolean inCheck = isKingInCheck(simulated); // test the copy
     *   // realBoard is completely untouched
     *
     * WHY NOT JUST USE board.clone()?
     * Java's default clone() is a SHALLOW copy — it copies the array reference
     * but not the objects inside it. Modifying the clone would still modify the
     * same Piece objects the original points to. We need a DEEP copy: a new grid
     * with new Piece references (though we don't need to deep-copy the Piece
     * fields themselves, since we only move pieces in simulation, not mutate them).
     *
     * Note: We DON'T deep-copy traps for simulation purposes — trap activation
     * doesn't affect check-detection logic. We copy the reference list.
     */
    public Board(Board other) {
        this.grid  = new Piece[8][8];
        this.traps = new ArrayList<>(other.traps); // shallow copy of trap list is fine

        // Copy grid references — same Piece objects, new grid array
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                this.grid[r][c] = other.grid[r][c];
            }
        }
    }

    // ── Setup Methods ─────────────────────────────────────────────────────────

    /**
     * Places a piece on the board at its current position.
     * Called during game setup and after pawn promotion.
     */
    public void placePiece(Piece piece) {
        Position pos = piece.getPosition();
        grid[pos.row][pos.col] = piece;
    }

    /**
     * Sets up the standard StrataChess starting position.
     * White pieces at rows 6-7 (bottom), Black at rows 0-1 (top).
     * Back rows use the standard chess piece order: R N B Q K B N R
     */
    public void setupInitialPosition() {
        // ── Black pieces (top, player 1) ──────────────────────────────────────
        placePiece(new Rook  (1, new Position(0, 0)));
        placePiece(new Knight(1, new Position(0, 1)));
        placePiece(new Bishop(1, new Position(0, 2)));
        placePiece(new Queen (1, new Position(0, 3)));
        placePiece(new King  (1, new Position(0, 4)));
        placePiece(new Bishop(1, new Position(0, 5)));
        placePiece(new Knight(1, new Position(0, 6)));
        placePiece(new Rook  (1, new Position(0, 7)));
        for (int c = 0; c < 8; c++) {
            placePiece(new Pawn(1, new Position(1, c)));
        }

        // ── White pieces (bottom, player 0) ───────────────────────────────────
        placePiece(new Rook  (0, new Position(7, 0)));
        placePiece(new Knight(0, new Position(7, 1)));
        placePiece(new Bishop(0, new Position(7, 2)));
        placePiece(new Queen (0, new Position(7, 3)));
        placePiece(new King  (0, new Position(7, 4)));
        placePiece(new Bishop(0, new Position(7, 5)));
        placePiece(new Knight(0, new Position(7, 6)));
        placePiece(new Rook  (0, new Position(7, 7)));
        for (int c = 0; c < 8; c++) {
            placePiece(new Pawn(0, new Position(6, c)));
        }
    }

    // ── Piece Access ──────────────────────────────────────────────────────────

    /** Returns the piece at the given position, or null if the square is empty. */
    public Piece getPieceAt(Position pos) {
        if (!pos.isOnBoard()) return null;
        return grid[pos.row][pos.col];
    }

    /** Returns a flat list of all pieces currently on the board. */
    public List<Piece> getAllPieces() {
        List<Piece> pieces = new ArrayList<>();
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (grid[r][c] != null)
                    pieces.add(grid[r][c]);
        return pieces;
    }

    /** Returns all pieces belonging to the given player. */
    public List<Piece> getPiecesFor(int ownerId) {
        List<Piece> result = new ArrayList<>();
        for (Piece p : getAllPieces())
            if (p.getOwnerId() == ownerId)
                result.add(p);
        return result;
    }

    // ── Move Application ──────────────────────────────────────────────────────
    /**
     * CONCEPT: Mutation — Applying State Changes
     * applyMove() is the ONLY place where the board state changes for a movement.
     * It handles three things: moving the piece, handling captures, and pawn
     * promotion. It does NOT validate the move — that is GameController's job.
     *
     * The discipline of "only mutate state in one place" makes bugs much easier
     * to find. If pieces are moving incorrectly, you look HERE — not in five
     * different places across the codebase.
     *
     * @param from  Source square.
     * @param to    Destination square.
     * @return      The captured piece, or null if no capture.
     */
    public Piece applyMove(Position from, Position to) {
        Piece moving  = grid[from.row][from.col];
        Piece captured = grid[to.row][to.col]; // null if destination is empty

        // Remove the piece from its source square
        grid[from.row][from.col] = null;

        // Handle trap activation: if destination has a trap and the moving
        // piece is NOT the king, the piece is destroyed. The trap also disappears.
        Trap trap = getTrapAt(to);
        if (trap != null && moving.getType() != Piece.Type.KING) {
            traps.remove(trap);
            // The moving piece is destroyed — don't place it at destination.
            // Return null because trap kills don't award coins (per game rules).
            return null; // The piece that triggered the trap is gone
        } else if (trap != null) {
            // King stepped on trap — king survives, trap is removed
            traps.remove(trap);
        }

        // Place the moving piece at its new square
        moving.setPosition(to);
        grid[to.row][to.col] = moving;

        // ── Pawn Promotion ────────────────────────────────────────────────────
        // CONCEPT: Polymorphic instanceof Check
        // We use instanceof to check if the moving piece is a Pawn. This is one
        // of the few legitimate uses of instanceof — we genuinely need to call
        // a Pawn-specific method (hasReachedPromotion). In well-designed code,
        // instanceof appears rarely. Here it's appropriate because promotion is
        // a special one-off rule, not a general behavior of all pieces.
        if (moving instanceof Pawn && ((Pawn) moving).hasReachedPromotion()) {
            // Replace the Pawn with a Queen at the same position
            Queen promoted = new Queen(moving.getOwnerId(), to);
            // If the pawn was the crown holder (rare but possible after crown transfer),
            // the new queen inherits crown status.
            if (moving.isCrownHolder()) {
                promoted.setCrownHolder(true);
            }
            grid[to.row][to.col] = promoted;
        }

        return captured; // null if no capture, non-null Piece if captured
    }

    // ── Trap Management ───────────────────────────────────────────────────────

    /** Adds a trap to the board at the given position. */
    public void addTrap(Trap trap) {
        traps.add(trap);
    }

    /** Returns the trap at a position, or null if none exists there. */
    public Trap getTrapAt(Position pos) {
        for (Trap t : traps)
            if (t.getPosition().equals(pos))
                return t;
        return null;
    }

    /** Returns all traps currently on the board. */
    public List<Trap> getAllTraps() {
        return new ArrayList<>(traps); // return a copy to prevent external mutation
    }

    // ── Crown Transfer ────────────────────────────────────────────────────────
    /**
     * Executes a crown transfer: the current crown holder loses crown status,
     * and the target piece gains it. Called by GameController after validation.
     */
    public void applyCrownTransfer(Position fromPos, Position toPos) {
        Piece fromPiece = grid[fromPos.row][fromPos.col];
        Piece toPiece   = grid[toPos.row][toPos.col];

        if (fromPiece != null) fromPiece.setCrownHolder(false);
        if (toPiece   != null) toPiece.setCrownHolder(true);
    }

    // ── Debug Utility ─────────────────────────────────────────────────────────
    /**
     * Prints the board to the console. Extremely useful during development
     * and debugging. When you can't run the GUI yet, this lets you verify
     * that your engine is placing and moving pieces correctly.
     *
     * Output format: each square shows a piece symbol (K, q, P, etc.),
     * a dot for an empty square, or a T for a trap square.
     */
    public void printToConsole() {
        System.out.println("  a b c d e f g h");
        for (int r = 0; r < 8; r++) {
            System.out.print((8 - r) + " "); // rank labels (8 down to 1)
            for (int c = 0; c < 8; c++) {
                Piece p = grid[r][c];
                if (p != null) {
                    System.out.print(p + " ");
                } else if (getTrapAt(new Position(r, c)) != null) {
                    System.out.print("T "); // trap indicator
                } else {
                    System.out.print(". ");
                }
            }
            System.out.println();
        }
        System.out.println();
    }
}