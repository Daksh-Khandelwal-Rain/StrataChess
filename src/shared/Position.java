package shared;

/**
 * CONCEPT: Value Object Pattern
 * ─────────────────────────────────────────────────────────────────────────────
 * A "Value Object" is a small class that represents a meaningful concept —
 * here, a board coordinate — purely by its VALUE, not by its identity.
 *
 * Compare this to a normal object (like a Player) where two different Player
 * objects with the same name are still two different objects in memory.
 * A Value Object says: "if two Positions have the same row and col, they ARE
 * the same position — we don't care which object in memory they are."
 *
 * To enforce that, we make Position IMMUTABLE: once created, row and col
 * can never change. This makes it safe to share the same Position object
 * across many parts of your program without fear of one part accidentally
 * changing it and breaking something else.
 *
 * WHY IMMUTABILITY MATTERS:
 * Imagine passing a position to the rules engine, and the rules engine
 * accidentally modifies it. Now your board state is corrupted. Immutability
 * prevents this entire class of bugs.
 */
public final class Position {

    // ── Fields ────────────────────────────────────────────────────────────────
    // 'final' on a field means it can only be assigned ONCE (in the constructor).
    // After construction, these values can never change — this is immutability.
    public final int row; // 0 = top row (Black's back row in our setup)
    public final int col; // 0 = leftmost column (the 'a' file in chess notation)

    // ── Constructor ───────────────────────────────────────────────────────────
    /**
     * Creates a new Position at the given row and column.
     * This is the ONLY way to set row and col — they're final, so after
     * this constructor runs, they can never be changed.
     *
     * @param row  The row index, 0–7 (top to bottom).
     * @param col  The column index, 0–7 (left to right).
     */
    public Position(int row, int col) {
        this.row = row;
        this.col = col;
    }

    // ── Utility Methods ───────────────────────────────────────────────────────

    /**
     * CONCEPT: Guard Clause / Input Validation
     * Instead of letting invalid positions propagate through your program and
     * cause mysterious NullPointerExceptions deep in the rules engine, we
     * validate at the boundary — right here, at the coordinate itself.
     *
     * @return true if this position is within the 8×8 board.
     */
    public boolean isOnBoard() {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    /**
     * Returns the Manhattan distance between this position and another.
     * Manhattan distance = |Δrow| + |Δcol| — the number of orthogonal steps.
     * Useful for trap visibility checks (is the king within 1 square of a trap?).
     */
    public int manhattanDistance(Position other) {
        return Math.abs(this.row - other.row) + Math.abs(this.col - other.col);
    }

    /**
     * Returns the Chebyshev distance — the number of king-moves between two squares.
     * A king can move diagonally, so Chebyshev distance = max(|Δrow|, |Δcol|).
     * Used for king-move validation and trap detection radius.
     */
    public int chebyshevDistance(Position other) {
        return Math.max(Math.abs(this.row - other.row), Math.abs(this.col - other.col));
    }

    // ── Serialization ─────────────────────────────────────────────────────────
    /**
     * CONCEPT: Serialization — converting an object to a string so it can be
     * transmitted over the network (a socket only sends bytes / text).
     *
     * Format: "row,col" — e.g., "3,5"
     * The matching parse() method reconstructs the object from that string.
     */
    public String serialize() {
        return row + "," + col;
    }

    /**
     * CONCEPT: Factory Method / Static Constructor
     * 'parse' is a static method that acts like an alternative constructor —
     * it creates a Position from a serialized string rather than two ints.
     * We call it a "factory method" because it's a static method whose job
     * is to produce a new instance of the class.
     *
     * @param s  A string in the format "row,col", e.g. "3,5".
     * @return   A new Position object.
     */
    public static Position parse(String s) {
        String[] parts = s.split(",");
        return new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    // ── equals() and hashCode() ───────────────────────────────────────────────
    /**
     * CONCEPT: Object Identity vs. Object Equality
     * In Java, == checks if two variables point to the SAME object in memory.
     * But for value objects, we want to check if they have the SAME value.
     *
     * Example without overriding equals():
     *   Position a = new Position(3, 5);
     *   Position b = new Position(3, 5);
     *   a == b         → FALSE (different objects in memory)
     *   a.equals(b)    → FALSE (Java's default equals() also uses ==)
     *
     * By overriding equals(), we teach Java what "equal" means for Position:
     *   a.equals(b)    → TRUE (same row and col = same position)
     *
     * The @Override annotation tells the compiler: "I intend to override a
     * parent class method." If I accidentally misname the method, the compiler
     * will catch it as an error — a useful safety net.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                // same memory address → trivially equal
        if (!(o instanceof Position)) return false; // different type → not equal
        Position other = (Position) o;
        return this.row == other.row && this.col == other.col;
    }

    /**
     * CONCEPT: The equals/hashCode Contract
     * In Java, if you override equals(), you MUST also override hashCode().
     * The rule is: if a.equals(b), then a.hashCode() MUST equal b.hashCode().
     *
     * WHY: Java's HashMap and HashSet use hashCode() to decide which "bucket"
     * to store an object in. If two equal objects had different hash codes,
     * the map would put them in different buckets and never find them again.
     * This is one of Java's most common subtle bugs — we prevent it here.
     *
     * The formula: multiply one field by a prime (31) and add the other.
     * This spreads values across the integer range to minimize collisions.
     */
    @Override
    public int hashCode() {
        return 31 * row + col;
    }

    /**
     * CONCEPT: toString() for Debugging
     * Every object in Java has a toString() method. By default it returns
     * something like "shared.Position@7ef88735" — useless for debugging.
     * Overriding it gives you readable output in logs and the debugger.
     */
    @Override
    public String toString() {
        // Convert to chess notation (e.g., row 7 col 0 → "a1") for readability.
        char file = (char) ('a' + col); // col 0='a', col 1='b', ..., col 7='h'
        int  rank = 8 - row;           // row 0=rank 8 (top), row 7=rank 1 (bottom)
        return "" + file + rank;       // e.g., "e4", "g7"
    }
}