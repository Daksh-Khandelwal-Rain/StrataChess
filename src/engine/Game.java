package engine;

import shared.Action;
import shared.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CONCEPT: Finite State Machine (FSM)
 * StrataChess has exactly three states:
 *   WAITING → PLAYING → GAME_OVER
 *
 * Game.java is the COORDINATOR — delegates work to Board, RulesEngine,
 * Economy, StrataManager, and Player. It doesn't implement those rules itself.
 */
public class Game {

    // ── State Enum ────────────────────────────────────────────────────────────
    public enum State { WAITING, PLAYING, GAME_OVER }

    // ── Core Components ───────────────────────────────────────────────────────
    private final Board         board;
    private final Player[]      players;
    private final StrataManager strata;
    private final Random        random;
    private       State         state;
    private       int           currentPlayerId;
    private       int           totalTurns;
    private       int           winnerId;

    // ── Listener Interface ────────────────────────────────────────────────────
    /**
     * CONCEPT: Observer / Listener Pattern
     * Game notifies the GUI about events without knowing anything about JavaFX.
     *
     * The economy callbacks are default no-op methods, so existing listener
     * implementations keep compiling.
     */
    public interface GameListener {
        void onMoveMade(Position from, Position to, Piece captured);
        void onTrapPlaced(Trap trap);
        void onCrownTransferred(Position from, Position to);
        void onTurnChanged(int newCurrentPlayerId);
        void onCheckDetected(int playerId);
        /**
         * Fires when a player is in checkmate but crown transfer is still
         * available. The game continues — this player MUST use crown transfer
         * on their next turn or lose by attempting any other illegal action.
         */
        void onCheckmateWarning(int playerId);
        void onGameOver(int winnerId, String reason);

        /** A player received Tribute for losing a piece to a normal capture. */
        default void onTributeAwarded(int playerId, int coins) {}

        /** A player collected from a Strata Square (fires for both players). */
        default void onStrataCollected(int playerId, StrataManager.StrataCollection collection) {}
    }

    private GameListener listener;

    // ── Constructors ──────────────────────────────────────────────────────────
    public Game(String player0Name, String player1Name) {
        this(player0Name, player1Name, new Random());
    }

    /** Lets callers (tests, networking) supply the Random used for Strata generation. */
    public Game(String player0Name, String player1Name, Random random) {
        this.board           = new Board();
        this.players         = new Player[]{
            new Player(0, player0Name),
            new Player(1, player1Name)
        };
        this.strata          = new StrataManager();
        this.random          = random;
        this.state           = State.WAITING;
        this.currentPlayerId = 0;
        this.totalTurns      = 0;
        this.winnerId        = -1;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        board.setupInitialPosition();
        strata.generate(random);
        players[0].setCrownPosition(new Position(7, 4)); // White King e1
        players[1].setCrownPosition(new Position(0, 4)); // Black King e8
        state = State.PLAYING;
        players[currentPlayerId].startClock();
        if (listener != null) listener.onTurnChanged(currentPlayerId);
    }

    // ── Action Processing ─────────────────────────────────────────────────────

    public boolean processAction(Action action) {
        if (state != State.PLAYING)                    return false;
        if (action.playerId != currentPlayerId)        return false;
        if (players[currentPlayerId].isOutOfTime()) {
            endGame(1 - currentPlayerId, "timeout");
            return false;
        }

        boolean applied = switch (action.type) {
            case MOVE           -> handleMove(action);
            case PLACE_TRAP     -> handleTrapPlacement(action);
            case CROWN_TRANSFER -> handleCrownTransfer(action);
        };

        if (!applied) return false;

        players[currentPlayerId].recordTurnsTaken();
        totalTurns++;
        advanceTurn();
        return true;
    }

    // ── Action Handlers ───────────────────────────────────────────────────────

    private boolean handleMove(Action action) {
        if (!RulesEngine.isLegalAction(action, board, players)) return false;

        Player actor    = players[action.playerId];
        int    opponent = 1 - action.playerId;

        Piece captured = board.applyMove(action.from, action.to);

        // ── Economy source 1: Tribute ─────────────────────────────────────────
        // The player who LOST the piece is paid. The capturer gets nothing.
        // Trap kills make applyMove return null, so they never pay Tribute.
        if (captured != null) {
            int tribute = Economy.awardTribute(captured, players[opponent]);
            if (listener != null) listener.onTributeAwarded(opponent, tribute);
        }

        Piece movedPiece = board.getPieceAt(action.to);
        if (movedPiece != null && movedPiece.isCrownHolder()) {
            actor.setCrownPosition(action.to);
        }

        // ── Economy source 2: Strata entry ────────────────────────────────────
        // Only if the actor's own piece actually survived and now stands on the
        // destination (a piece killed by a trap never "enters").
        if (movedPiece != null && movedPiece.getOwnerId() == action.playerId) {
            StrataManager.StrataCollection collection =
                strata.resolveEntry(action.to, movedPiece);
            if (collection != null) {
                actor.addCoins(collection.coins());
                if (listener != null) listener.onStrataCollected(action.playerId, collection);
            }
        }

        if (listener != null) listener.onMoveMade(action.from, action.to, captured);

        // ── Checkmate / Check detection ────────────────────────────────────────
        if (RulesEngine.isCheckmate(board, opponent, players[opponent])) {
            endGame(action.playerId, "checkmate");
        } else if (RulesEngine.isStalemate(board, opponent, players[opponent])) {
            endGame(-1, "stalemate");
        } else if (RulesEngine.isInCheck(board, opponent, players[opponent])) {
            if (listener != null) {
                listener.onCheckDetected(opponent);
            }
        }

        return true;
    }

    private boolean handleTrapPlacement(Action action) {
        if (!RulesEngine.isLegalAction(action, board, players)) {
            return false;
        }

        Player actor = players[action.playerId];

        if (!Economy.chargeTrapCost(actor)) {
            return false;
        }

        // This is a lifetime deployment count.
        actor.recordTrapPlaced();

        Trap existing = board.getTrapAt(action.to);

        if (existing != null) {
            // The only existing trap allowed here is an enemy trap.
            // Trap-vs-trap collision destroys both traps.
            board.removeTrap(existing);
            return true;
        }

        Trap trap = new Trap(action.playerId, action.to);
        board.addTrap(trap);

        if (listener != null) {
            listener.onTrapPlaced(trap);
        }

        return true;
    }

    private boolean handleCrownTransfer(Action action) {
        if (!RulesEngine.isLegalAction(action, board, players)) return false;

        Player actor = players[action.playerId];

        // RulesEngine already validated affordability; this is a safety guard.
        if (!Economy.chargeCrownTransferCost(actor)) return false;

        board.applyCrownTransfer(action.from, action.to);
        actor.setCrownPosition(action.to);
        actor.useCrownTransfer();

        if (listener != null) listener.onCrownTransferred(action.from, action.to);
        return true;
    }

    // ── Turn Management ───────────────────────────────────────────────────────

    private void advanceTurn() {
        players[currentPlayerId].stopClock();
        currentPlayerId = 1 - currentPlayerId;

        if (players[currentPlayerId].isOutOfTime()) {
            endGame(1 - currentPlayerId, "timeout");
            return;
        }

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

    public void checkTimeout() {
        if (state != State.PLAYING) return;

        if (players[currentPlayerId].isOutOfTime()) {
            endGame(1 - currentPlayerId, "timeout");
        }
    }



    /** Seeds Strata generation so both machines build identical squares. Call before start(). */
    public void seedStrata(long seed) {
        if (state == State.WAITING) random.setSeed(seed);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Board         getBoard()            { return board; }
    public Player[]      getPlayers()          { return players; }
    public Player        getPlayer(int id)     { return players[id]; }
    public State         getState()            { return state; }
    public int           getCurrentPlayerId()  { return currentPlayerId; }
    public int           getTotalTurns()       { return totalTurns; }
    public int           getWinnerId()         { return winnerId; }
    public StrataManager getStrata()           { return strata; }

    /** Active Strata Squares this player may see. The UI must use this, never getStrata() directly. */
    public List<StrataSquare> getVisibleStrata(int playerId) {
        return strata.getActiveVisibleTo(playerId);
    }

        /** Squares where this player may legally place a trap right now. RulesEngine decides. */
    public List<Position> getLegalTrapSquares(int playerId) {
        List<Position> squares = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position p = new Position(r, c);
                if (RulesEngine.isLegalAction(Action.placeTrap(playerId, p), board, players)) {
                    squares.add(p);
                }
            }
        }
        return squares;
    }

    public void setListener(GameListener l) { this.listener = l; }
}
