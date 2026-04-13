package shared;

/**
 * CONCEPT: The Command Pattern
 * ─────────────────────────────────────────────────────────────────────────────
 * In a simple program, you might call game.movePiece(from, to) directly.
 * That works fine until you need to send that action over a network, or log
 * it for replay, or validate it before executing it, or support undo.
 *
 * The Command Pattern solves this by turning a "do something" call into an
 * OBJECT — an Action — that carries all the information needed to perform
 * (or reverse, or transmit) the action later.
 *
 * Think of it like the difference between calling someone on the phone
 * right now vs. writing them a letter. The letter (Action object) can be:
 *   - Delivered over a network (serialized to a string, sent via socket)
 *   - Validated before being acted on (GameController inspects it first)
 *   - Logged for game replay
 *   - Queued and processed in order
 *
 * In StrataChess, EVERY action in the game — move, place trap, transfer
 * crown — is represented as an Action object. The GameController is the
 * single point that receives and processes all of them.
 */
public class Action {

    // ── Action Types ──────────────────────────────────────────────────────────
    /**
     * CONCEPT: Enum (Enumerated Type)
     * An enum is a special class that has a fixed, known set of values.
     * Instead of using magic strings like "MOVE" and "TRAP" scattered through
     * your code (which are easy to typo and hard to refactor), an enum gives
     * you compile-time safety. If you write Type.MUVE, the compiler catches it.
     * If you write "MUVE" as a string, it silently breaks at runtime.
     */
    public enum Type {
        MOVE,           // Move a piece from one square to another
        PLACE_TRAP,     // Spend 3 coins, place a trap on an empty square
        CROWN_TRANSFER  // Transfer the crown from the king to another piece
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    // All fields are final — an Action is immutable once created.
    // You cannot change your mind about what a past action was.
    public final Type     type;      // What kind of action is this?
    public final int      playerId;  // Which player (0 or 1) is performing it?
    public final Position from;      // Origin square (for MOVE and CROWN_TRANSFER)
    public final Position to;        // Destination square (for MOVE and PLACE_TRAP)

    // ── Constructor ───────────────────────────────────────────────────────────
    /**
     * Private constructor — forces all Action creation through the factory
     * methods below (of(), move(), placeTrap(), crownTransfer()).
     * This is the BUILDER / FACTORY METHOD pattern: we control how Actions
     * are created so we can enforce invariants (e.g., MOVE always has a 'to').
     */
    private Action(Type type, int playerId, Position from, Position to) {
        this.type     = type;
        this.playerId = playerId;
        this.from     = from;
        this.to       = to;
    }

    // ── Factory Methods ───────────────────────────────────────────────────────
    /**
     * CONCEPT: Named Constructors / Static Factory Methods
     * Instead of new Action(Type.MOVE, 0, from, to) — which is hard to read —
     * factory methods give the constructor a meaningful name:
     *   Action.move(0, from, to)  — instantly readable.
     *
     * This is also why Java's standard library uses Integer.parseInt() instead
     * of new Integer(string) — named factories are self-documenting.
     */

    /** Create a MOVE action: player moves a piece from → to. */
    public static Action move(int playerId, Position from, Position to) {
        return new Action(Type.MOVE, playerId, from, to);
    }

    /** Create a PLACE_TRAP action: player places a trap at the given position. */
    public static Action placeTrap(int playerId, Position where) {
        // 'from' is null for trap placement — no origin square needed.
        return new Action(Type.PLACE_TRAP, playerId, null, where);
    }

    /**
     * Create a CROWN_TRANSFER action.
     * @param from  The current crown holder's position.
     * @param to    The target piece that will receive the crown.
     */
    public static Action crownTransfer(int playerId, Position from, Position to) {
        return new Action(Type.CROWN_TRANSFER, playerId, from, to);
    }

    // ── Serialization ─────────────────────────────────────────────────────────
    /**
     * CONCEPT: Serialization Protocol
     * A network socket sends raw bytes. To transmit an Action, we convert it
     * to a plain string — this is "serialization." The receiver deserializes
     * the string back into an Action object.
     *
     * Our protocol (the agreed-upon format):
     *   MOVE:            "MOVE|playerId|fromRow,fromCol|toRow,toCol"
     *   PLACE_TRAP:      "PLACE_TRAP|playerId|toRow,toCol"
     *   CROWN_TRANSFER:  "CROWN_TRANSFER|playerId|fromRow,fromCol|toRow,toCol"
     *
     * We use "|" as a delimiter because it doesn't appear in our coordinate
     * format ("row,col"), so splitting on "|" always gives clean segments.
     *
     * This is exactly how real network protocols work — HTTP uses "\r\n" as
     * delimiters; JSON uses braces and commas. The key is: sender and receiver
     * must agree on the same format.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name());           // e.g., "MOVE"
        sb.append("|").append(playerId);  // e.g., "|0"

        if (from != null) {
            sb.append("|").append(from.serialize()); // e.g., "|6,4"
        }
        sb.append("|").append(to.serialize());       // e.g., "|4,4"

        return sb.toString(); // full result: "MOVE|0|6,4|4,4"
    }

    /**
     * Deserialize a string back into an Action object.
     * This is the reverse of serialize() — it must handle the exact same format.
     *
     * CONCEPT: Defensive Programming
     * We use a switch statement (exhaustive over enum values) so if a new
     * action type is ever added and this method isn't updated, the compiler
     * (or the runtime) will catch the unhandled case immediately.
     *
     * @param s  A serialized action string, e.g. "MOVE|0|6,4|4,4"
     * @return   The reconstructed Action object.
     */
    public static Action deserialize(String s) {
        String[] parts = s.split("\\|"); // split on "|" (escaped for regex)
        Type type     = Type.valueOf(parts[0]);
        int  playerId = Integer.parseInt(parts[1]);

        switch (type) {
            case MOVE:
                return new Action(Type.MOVE, playerId,
                        Position.parse(parts[2]),
                        Position.parse(parts[3]));

            case PLACE_TRAP:
                // No 'from' for traps — the destination is in parts[2]
                return new Action(Type.PLACE_TRAP, playerId,
                        null,
                        Position.parse(parts[2]));

            case CROWN_TRANSFER:
                return new Action(Type.CROWN_TRANSFER, playerId,
                        Position.parse(parts[2]),
                        Position.parse(parts[3]));

            default:
                // This should never happen if serialize() and deserialize()
                // are kept in sync. Throwing here makes bugs loud and obvious.
                throw new IllegalArgumentException("Unknown action type: " + parts[0]);
        }
    }

    // ── toString ──────────────────────────────────────────────────────────────
    @Override
    public String toString() {
        // Human-readable format for logging and debugging.
        // Uses Position.toString() which outputs chess notation (e.g., "e4").
        switch (type) {
            case MOVE:
                return "Player " + playerId + " MOVE " + from + " → " + to;
            case PLACE_TRAP:
                return "Player " + playerId + " PLACE_TRAP at " + to;
            case CROWN_TRANSFER:
                return "Player " + playerId + " CROWN_TRANSFER from " + from + " to " + to;
            default:
                return "Unknown action";
        }
    }
}