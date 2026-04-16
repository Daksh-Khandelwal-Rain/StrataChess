package engine;

import engine.pieces.*;
import shared.Position;

/**
 * CONCEPT: Factory / Utility for Deep Copying Pieces
 * ─────────────────────────────────────────────────────────────────────────────
 * This class exists for one reason: the Board copy constructor needs to create
 * independent copies of Piece objects so that RulesEngine simulations never
 * accidentally mutate the real game state.
 *
 * WHY A SEPARATE CLASS?
 * Piece.java is abstract — you can't instantiate it directly. Each subclass
 * (King, Queen, Rook, Bishop, Knight, Pawn) is a different type. To copy a
 * Piece we need to know its concrete type and call the right constructor.
 * This logic doesn't belong in Piece (it would create a circular dependency
 * if Piece knew about its own subclasses), and it doesn't belong in Board
 * (Board shouldn't know about piece subclass details). PieceCopier is a
 * clean, isolated place for this one responsibility.
 *
 * HOW IT WORKS:
 * copy() inspects the piece's Type enum, creates a fresh instance of the
 * correct subclass at the same position, then copies over the mutable fields
 * (hasMoved, isCrownHolder) that affect game rules.
 *
 * This is the FACTORY METHOD pattern — a static method that creates objects
 * of the right type based on runtime information, without the caller needing
 * to know which subclass to instantiate.
 */
public class PieceCopier {

    // Private constructor — pure utility class, never instantiated
    private PieceCopier() {}

    /**
     * Creates a deep copy of the given piece.
     * The copy has:
     *   - The same type, owner, and position as the original
     *   - The same hasMoved and isCrownHolder state
     *   - NO shared reference to the original — mutating the copy is safe
     *
     * @param original The piece to copy. Must not be null.
     * @return A new, independent Piece of the same concrete type.
     */
    public static Piece copy(Piece original) {
        Position pos = original.getPosition();

        // CONCEPT: Switch on enum — exhaustive, compiler-checked.
        // If a new piece type is ever added to Piece.Type and this switch
        // isn't updated, the compiler will warn about the unhandled case.
        Piece copy = switch (original.getType()) {
            case KING   -> new engine.pieces.King  (original.getOwnerId(), new Position(pos.row, pos.col));
            case QUEEN  -> new engine.pieces.Queen (original.getOwnerId(), new Position(pos.row, pos.col));
            case ROOK   -> new engine.pieces.Rook  (original.getOwnerId(), new Position(pos.row, pos.col));
            case BISHOP -> new engine.pieces.Bishop(original.getOwnerId(), new Position(pos.row, pos.col));
            case KNIGHT -> new engine.pieces.Knight(original.getOwnerId(), new Position(pos.row, pos.col));
            case PAWN   -> new engine.pieces.Pawn  (original.getOwnerId(), new Position(pos.row, pos.col));
        };

        // Copy mutable state that affects rules:
        //   hasMoved      — determines if a Pawn can still move two squares
        //   isCrownHolder — determines the win condition target
        // We call setPosition() with the same position to trigger hasMoved = true
        // only if the original has already moved. Since new pieces start with
        // hasMoved = false, we need to manually sync this field.
        if (original.hasMoved()) {
            copy.setPosition(new Position(pos.row, pos.col)); // triggers hasMoved = true
        }
        copy.setCrownHolder(original.isCrownHolder());

        return copy;
    }
}