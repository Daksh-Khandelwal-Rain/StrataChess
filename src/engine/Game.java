package engine;

import shared.Action;
import shared.Position;

/**
 * CONCEPT: Finite State Machine (FSM)
 * StrataChess has exactly three states:
 *   WAITING → PLAYING → GAME_OVER
 *
 * Game.java is the COORDINATOR — delegates work to Board, RulesEngine,
 * Economy, and Player. It doesn't implement any of those things itself.
 */
public class Game {

    // ── State Enum ────────────────────────────────────────────────────────────
    public enum State { WAITING, PLAYING, GAME_OVER }

    // ── Core Components ───────────────────────────────────────────────────────
    private final Board    board;
    private final Player[] players;
    private       State    state;
    private       int      currentPlayerId;
    private       int      totalTurns;
    private       int      winnerId;

    // ── Listener Interface ────────────────────────────────────────────────────
    /**
     * CONCEPT: Observer / Listener Pattern
     * Game notifies the GUI about events without knowing anything about JavaFX.
     *
     * FIX: Added onCheckmateWarning — fires when a player is in checkmate but
     * still has crown transfer available as an escape route. The game does NOT
     * end immediately; the player gets one chance to use crown transfer.
     * If they can't (already used or can't afford it), onGameOver fires instead.
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
    }

    private GameListener listener;

    // ── Constructor ───────────────────────────────────────────────────────────
    public Game(String player0Name, String player1Name) {
        this.board           = new Board();
        this.players         = new Player[]{
            new Player(0, player0Name),
            new Player(1, player1Name)
        };
        this.state           = State.WAITING;
        this.currentPlayerId = 0;
        this.totalTurns      = 0;
        this.winnerId        = -1;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        board.setupInitialPosition();
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

        totalTurns++;
        advanceTurn();
        return true;
    }

    // ── Action Handlers ───────────────────────────────────────────────────────

    private boolean handleMove(Action action) {
        if (!RulesEngine.isLegalAction(action, board, players, totalTurns)) return false;

        Player actor    = players[action.playerId];
        int    opponent = 1 - action.playerId;

        Piece captured = board.applyMove(action.from, action.to);
        if (captured != null) {
            Economy.awardForCapture(captured, actor);
        }

        Piece movedPiece = board.getPieceAt(action.to);
        if (movedPiece != null && movedPiece.isCrownHolder()) {
            actor.setCrownPosition(action.to);
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
        if (!RulesEngine.isLegalAction(action, board, players, totalTurns)) return false;

        Player actor = players[action.playerId];
        Economy.chargeTrapCost(actor);
        actor.recordTrapPlaced();

        Trap trap = new Trap(action.playerId, action.to);
        board.addTrap(trap);

        if (listener != null) listener.onTrapPlaced(trap);
        return true;
    }

    private boolean handleCrownTransfer(Action action) {
        if (!RulesEngine.isLegalAction(action, board, players, totalTurns)) return false;

        Player actor = players[action.playerId];

        /**
         * FIX: Charge 5 coins for the crown transfer.
         * RulesEngine already validated the player has enough coins, so this
         * should always succeed here — but we keep the return-false safety guard.
         */
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