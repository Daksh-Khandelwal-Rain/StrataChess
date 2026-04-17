package engine;

import shared.Action;
import shared.Position;

/**
 * CONCEPT: Finite State Machine (FSM)
 * ─────────────────────────────────────────────────────────────────────────────
 * A Finite State Machine is a model where a system can be in exactly ONE of a
 * fixed set of states at any time, and transitions between states happen only
 * in response to specific events.
 *
 * StrataChess has exactly three states:
 *
 *   WAITING ──(both players connected)──► PLAYING ──(checkmate/timeout)──► GAME_OVER
 *
 * This pattern is everywhere in software:
 *   - A traffic light: RED → GREEN → YELLOW → RED
 *   - A vending machine: IDLE → ITEM_SELECTED → PAYMENT → DISPENSING → IDLE
 *   - A network connection: CLOSED → CONNECTING → OPEN → CLOSING → CLOSED
 *
 * WHY USE A STATE MACHINE?
 * Because it makes illegal transitions IMPOSSIBLE at the code level. Without a
 * state machine, a bug might cause the game to accept moves while in GAME_OVER
 * state, or start a timer before the game is PLAYING. By gating every operation
 * with a state check, we prevent an entire class of bugs.
 *
 * Game.java is the COORDINATOR — it delegates work to Board, RulesEngine,
 * Economy, and Player. It doesn't implement any of those things itself.
 * This is the FACADE pattern: a single, simple interface (Game) that hides
 * the complexity of the subsystem behind it.
 */
public class Game {

    // ── State Enum ────────────────────────────────────────────────────────────
    public enum State {
        WAITING,   // Game not yet started — waiting for both players to connect
        PLAYING,   // Game in progress — one player's turn is active
        GAME_OVER  // Game has ended — a winner has been decided
    }

    // ── Core Components ───────────────────────────────────────────────────────
    private final Board    board;
    private final Player[] players; // players[0] = White, players[1] = Black
    private       State    state;
    private       int      currentPlayerId; // Whose turn is it? (0 or 1)
    private       int      totalTurns;      // Total half-moves made in the game
    private       int      winnerId;        // -1 if no winner yet

    // ── Listener Interface ────────────────────────────────────────────────────
    /**
     * CONCEPT: Observer / Listener Pattern
     * Game doesn't know anything about the GUI or the network. But it needs
     * a way to say "hey, something changed — update yourself." It does this
     * by calling a LISTENER — an interface that the GUI implements.
     *
     * The GUI registers itself as a listener:
     *   game.setListener(boardView);
     *
     * When a move is made, Game calls:
     *   listener.onMoveMade(from, to, captured);
     *
     * BoardView receives this and updates the visual board. Game never
     * references BoardView directly — it only knows about the GameListener
     * interface. This is called "programming to an interface, not an
     * implementation" — a core principle of good OOP.
     */
    public interface GameListener {
        void onMoveMade(Position from, Position to, Piece captured);
        void onTrapPlaced(Trap trap);
        void onCrownTransferred(Position from, Position to);
        void onTurnChanged(int newCurrentPlayerId);
        void onCheckDetected(int playerId);
        void onGameOver(int winnerId, String reason);
    }

    private GameListener listener;

    // ── Constructor ───────────────────────────────────────────────────────────
    public Game(String player0Name, String player1Name) {
        this.board   = new Board();
        this.players = new Player[]{
            new Player(0, player0Name),
            new Player(1, player1Name)
        };
        this.state           = State.WAITING;
        this.currentPlayerId = 0; // White always goes first
        this.totalTurns      = 0;
        this.winnerId        = -1;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Starts the game: sets up the board, places pieces, starts the first clock. */
    public void start() {
        board.setupInitialPosition();

        // Set each player's initial crown position (where their King is placed)
        players[0].setCrownPosition(new Position(7, 4)); // White King at e1
        players[1].setCrownPosition(new Position(0, 4)); // Black King at e8

        state = State.PLAYING;
        players[currentPlayerId].startClock();

        if (listener != null) listener.onTurnChanged(currentPlayerId);
    }

    // ── Action Processing ─────────────────────────────────────────────────────

    /**
     * Process an incoming action from a player. This is called by GameController,
     * which has already validated the action through RulesEngine.
     *
     * CONCEPT: The Chain of Responsibility
     * processAction coordinates the response by calling into multiple subsystems:
     *   1. Validate the action (RulesEngine)
     *   2. Apply the state change (Board, Player, Economy)
     *   3. Notify the observer (GameListener → BoardView)
     *   4. Check for game-ending conditions
     *   5. Advance to the next turn
     *
     * @return true if the action was successfully applied.
     */
    public boolean processAction(Action action) {
        // Guard: only accept actions during PLAYING state
        if (state != State.PLAYING) return false;

        // Guard: only accept actions from the current player
        if (action.playerId != currentPlayerId) return false;

        // Guard: check for timer expiry before accepting any move
        if (players[currentPlayerId].isOutOfTime()) {
            endGame(1 - currentPlayerId, "timeout");
            return false;
        }

        // Validate and apply the specific action type
        boolean applied = switch (action.type) {
            case MOVE           -> handleMove(action);
            case PLACE_TRAP     -> handleTrapPlacement(action);
            case CROWN_TRANSFER -> handleCrownTransfer(action);
        };

        if (!applied) return false;

        // Increment turn count and advance to the next player's turn
        totalTurns++;
        advanceTurn();
        return true;
    }

    // ── Action Handlers ───────────────────────────────────────────────────────

    private boolean handleMove(Action action) {
        // Final rule check before applying
        if (!RulesEngine.isLegalAction(action, board, players, totalTurns)) return false;

        Player actor   = players[action.playerId];
        int opponent   = 1 - action.playerId;

        // Apply the move and get any captured piece
        Piece captured = board.applyMove(action.from, action.to);

        // Award coins for the capture (if any) — trap kills don't reach here
        if (captured != null) {
            Economy.awardForCapture(captured, actor);
        }

        // If the crown holder moved, update the player's tracked crown position
        Piece movedPiece = board.getPieceAt(action.to);
        if (movedPiece != null && movedPiece.isCrownHolder()) {
            actor.setCrownPosition(action.to);
        }

        // Notify the GUI
        if (listener != null) listener.onMoveMade(action.from, action.to, captured);

        // Check if the opponent is now in checkmate
        if (RulesEngine.isCheckmate(board, opponent, players[opponent])) {
            endGame(action.playerId, "checkmate");
        } else if (RulesEngine.isInCheck(board, opponent, players[opponent])) {
            if (listener != null) listener.onCheckDetected(opponent);
        }

        return true;
    }

    private boolean handleTrapPlacement(Action action) {
        if (!RulesEngine.isLegalAction(action, board, players, totalTurns)) return false;

        Player actor = players[action.playerId];

        // Deduct coin cost
        Economy.chargeTrapCost(actor);
        actor.recordTrapPlaced();

        // Place the trap on the board
        Trap trap = new Trap(action.playerId, action.to);
        board.addTrap(trap);

        if (listener != null) listener.onTrapPlaced(trap);
        return true;
    }

    private boolean handleCrownTransfer(Action action) {
        if (!RulesEngine.isLegalAction(action, board, players, totalTurns)) return false;

        Player actor = players[action.playerId];

        // Apply the crown transfer on the board (flips isCrownHolder flags)
        board.applyCrownTransfer(action.from, action.to);

        // Update the player's tracked crown position
        actor.setCrownPosition(action.to);
        actor.useCrownTransfer();

        if (listener != null) listener.onCrownTransferred(action.from, action.to);
        return true;
    }

    // ── Turn Management ───────────────────────────────────────────────────────

    private void advanceTurn() {
        // Stop the current player's clock
        players[currentPlayerId].stopClock();

        // Switch to the other player
        currentPlayerId = 1 - currentPlayerId;

        // Check timer on the NEW current player too (could be 0 already)
        if (players[currentPlayerId].isOutOfTime()) {
            endGame(1 - currentPlayerId, "timeout");
            return;
        }

        // Start the new player's clock
        players[currentPlayerId].startClock();

        if (listener != null) listener.onTurnChanged(currentPlayerId);
    }

    // ── Game Over ─────────────────────────────────────────────────────────────

    private void endGame(int winnerId, String reason) {
        this.state    = State.GAME_OVER;
        this.winnerId = winnerId;
        players[0].stopClock();
        players[1].stopClock();
        if (listener != null) listener.onGameOver(winnerId, reason);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Board    getBoard()            { return board; }
    public Player[] getPlayers()          { return players; }
    public Player   getPlayer(int id)     { return players[id]; }
    public State    getState()            { return state; }
    public int      getCurrentPlayerId()  { return currentPlayerId; }
    public int      getTotalTurns()       { return totalTurns; }
    public int      getWinnerId()         { return winnerId; }
    public void     setListener(GameListener l) { this.listener = l; }
}