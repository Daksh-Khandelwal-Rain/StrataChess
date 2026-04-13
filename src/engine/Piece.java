package engine;

import shared.Position;
import java.util.List;

/**
 * CONCEPT: Abstract Classes and Polymorphism
 * ─────────────────────────────────────────────────────────────────────────────
 * An ABSTRACT CLASS is a class you can never instantiate directly.
 * You can't write: Piece p = new Piece(...)   ← compiler error!
 * You CAN write:  Piece p = new Rook(...)     ← perfectly fine
 *
 * This is the whole point. Piece defines the COMMON STRUCTURE that all six
 * piece types share. Each concrete subclass (King, Rook, etc.) inherits
 * that structure and adds its own movement logic.
 *
 * POLYMORPHISM: "poly" = many, "morph" = form. The same variable type
 * (Piece) can hold many different forms (King, Rook, Bishop...). When you
 * call piece.getValidMoves(), Java figures out at runtime which version
 * of the method to call based on the actual object type.
 *
 * This means Board.java can store a List<Piece> — a single list containing
 * all types of pieces — and call getValidMoves() on each without needing a
 * giant if/else or switch statement. The correct method is called automatically.
 *
 * If this were not abstract — if Piece itself defined a getValidMoves() that
 * returned an empty list — a subclass that forgot to override it would silently
 * return no moves instead of loudly failing. Abstract methods force the subclass
 * to implement them or the code won't compile. Loudness is better than silence
 * when it comes to bugs.
 */
public abstract class Piece {

    // ── Piece Types ───────────────────────────────────────────────────────────
    /**
     * CONCEPT: Using Enums for Fixed Sets of Values
     * There are exactly six chess piece types — this will never change.
     * An enum captures this fixed set at the type level, so the compiler
     * will warn you if you ever try to use a type that doesn't exist.
     * Using String "KING", "QUEEN" etc. would allow typos like "QEEN"
     * that compile fine but fail at runtime. Enums prevent that entirely.
     */
    public enum Type {
        KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    // These are 'protected' so subclasses (King, Rook, etc.) can read them
    // directly without needing a getter call. 'private' would hide them from
    // subclasses; 'public' would expose them to everyone. 'protected' is
    // the middle ground: accessible within the class and its descendants.
    protected final Type    type;      // What kind of piece is this?
    protected final int     ownerId;   // Which player owns this piece (0 or 1)?
    protected       Position position; // Where is this piece on the board RIGHT NOW?

    // Crown status: by default, only the King is the crown holder.
    // After a crown transfer, a non-King piece can hold this flag = true.
    protected boolean isCrownHolder;

    // Track whether this piece has moved — relevant for Pawn's two-square
    // opening move and potentially for castling rules if ever added.
    protected boolean hasMoved;

    // ── Constructor ───────────────────────────────────────────────────────────
    /**
     * CONCEPT: Constructor Chaining in Inheritance
     * When a subclass (e.g., Rook) calls super(type, ownerId, position),
     * it is calling THIS constructor. Java requires that the parent class
     * constructor runs before the subclass constructor, ensuring the
     * object is fully initialized in the correct order.
     */
    protected Piece(Type type, int ownerId, Position position) {
        this.type          = type;
        this.ownerId       = ownerId;
        this.position      = position;
        this.isCrownHolder = (type == Type.KING); // Only Kings start as crown holders
        this.hasMoved      = false;
    }

    // ── Abstract Method — The Core of Polymorphism ────────────────────────────
    /**
     * CONCEPT: Abstract Method
     * This method has NO body here — just a signature ending in a semicolon.
     * Every concrete subclass (King, Queen, Rook, Bishop, Knight, Pawn) MUST
     * override this method or Java will refuse to compile.
     *
     * The method returns a List of Positions that this piece can PHYSICALLY
     * reach from its current position, given the current board layout.
     *
     * IMPORTANT NOTE ON DESIGN: This method returns "pseudo-legal" moves —
     * all squares this piece can physically reach, WITHOUT yet checking whether
     * the move would leave the player's own king in check. That final filter
     * is applied by RulesEngine.filterLegalMoves(), which calls this method
     * and then removes moves that result in self-check.
     *
     * WHY SEPARATE THESE TWO CONCERNS?
     * Because checking for self-check requires simulating the board state
     * after the move — that's complex logic that belongs in RulesEngine,
     * not scattered across six different piece classes. Each class does one
     * thing well.
     *
     * @param board The current game board — needed to know what squares are
     *              occupied and by whom.
     * @return A list of squares this piece can physically move to.
     */
    public abstract List<Position> getValidMoves(Board board);

    // ── Concrete Methods — Shared by All Pieces ───────────────────────────────
    // These are NOT abstract because every piece behaves the same way for these.

    public Type     getType()        { return type; }
    public int      getOwnerId()     { return ownerId; }
    public Position getPosition()    { return position; }
    public boolean  isCrownHolder()  { return isCrownHolder; }
    public boolean  hasMoved()       { return hasMoved; }

    /** Updates the piece's position after a successful move. */
    public void setPosition(Position newPos) {
        this.position = newPos;
        this.hasMoved = true; // Once a piece moves, this is permanently true
    }

    /** Grants or removes crown holder status (used by crown transfer logic). */
    public void setCrownHolder(boolean crown) {
        this.isCrownHolder = crown;
    }

    // ── Utility Helpers for Subclasses ────────────────────────────────────────
    /**
     * CONCEPT: Protected Helper Methods
     * These small helpers are used repeatedly by the movement logic in
     * subclasses. Putting them here (in the parent) means we write them
     * once and share them everywhere. This is the DRY principle:
     * "Don't Repeat Yourself."
     *
     * Returns true if the given position is occupied by an opponent's piece.
     * A piece can capture an opponent but not its own teammate.
     */
    protected boolean isOccupiedByOpponent(Position pos, Board board) {
        Piece occupant = board.getPieceAt(pos);
        return occupant != null && occupant.getOwnerId() != this.ownerId;
    }

    /**
     * Returns true if the given position is empty on the board.
     * Used by sliding pieces (Rook, Bishop, Queen) to check if they can
     * continue sliding in a direction.
     */
    protected boolean isEmpty(Position pos, Board board) {
        return board.getPieceAt(pos) == null;
    }

    /**
     * CONCEPT: Sliding Piece Movement — a Reusable Algorithm
     * Rooks, Bishops, and Queens all move by "sliding" in a direction until
     * they hit the edge of the board or another piece. Rather than duplicating
     * this loop in each of those three classes, we define it once here.
     *
     * The direction is expressed as (dRow, dCol) — the delta applied each step.
     * Examples:
     *   Rook moving right:       dRow=0, dCol=+1
     *   Bishop moving down-left: dRow=+1, dCol=-1
     *   Queen moving up:         dRow=-1, dCol=0
     *
     * The method adds all reachable positions in that direction to the
     * provided list, stopping when it hits the board edge or another piece
     * (including that piece if it's an opponent — since capturing is legal).
     *
     * @param moves  The list to add valid positions to (modified in place).
     * @param board  The current board state.
     * @param dRow   Row delta per step (+1 = down, -1 = up).
     * @param dCol   Column delta per step (+1 = right, -1 = left).
     */
    protected void slide(List<Position> moves, Board board, int dRow, int dCol) {
        int r = position.row + dRow;
        int c = position.col + dCol;

        while (true) {
            Position next = new Position(r, c);

            // Stop if we've left the board
            if (!next.isOnBoard()) break;

            if (isEmpty(next, board)) {
                // Empty square — we can move here and keep sliding
                moves.add(next);
            } else if (isOccupiedByOpponent(next, board)) {
                // Opponent piece — we can capture here, but can't slide further
                moves.add(next);
                break;
            } else {
                // Our own piece — blocked, stop immediately (don't add this square)
                break;
            }

            r += dRow;
            c += dCol;
        }
    }

    // ── toString ──────────────────────────────────────────────────────────────
    @Override
    public String toString() {
        // Concise symbol useful for printing the board state during debugging.
        // Uppercase = White (player 0), Lowercase = Black (player 1) — chess convention.
        String symbol = switch (type) {
            case KING   -> "K";
            case QUEEN  -> "Q";
            case ROOK   -> "R";
            case BISHOP -> "B";
            case KNIGHT -> "N"; // "N" because "K" is taken by King
            case PAWN   -> "P";
        };
        return (ownerId == 0) ? symbol : symbol.toLowerCase();
    }
}