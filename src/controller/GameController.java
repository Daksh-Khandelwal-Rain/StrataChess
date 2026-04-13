package controller;

import engine.Game;
import engine.RulesEngine;
import networking.Server;
import networking.Client;
import shared.Action;
import shared.Position;
import engine.Piece;
import engine.Board;

import java.util.List;

/**
 * CONCEPT: The Controller in MVC — The Single Point of Entry
 * ─────────────────────────────────────────────────────────────────────────────
 * In the Model-View-Controller pattern, the Controller's job is to be
 * the TRANSLATOR and GATEKEEPER between all the layers:
 *
 *   GUI (BoardView)  ──click──►  GameController  ──action──►  Game (Model)
 *   Network (Client) ──recv──►   GameController  ──action──►  Game (Model)
 *   Game (Model)     ──event──►  GameController  ──update──►  GUI / Network
 *
 * WHY DOES THIS MATTER?
 * Without a controller, your GUI would need to know about the network, and the
 * network would need to know about the GUI, and everyone would need to know
 * about the rules engine. This creates a "spaghetti" of dependencies where
 * changing one thing breaks five others.
 *
 * With the controller, each layer only knows about the controller — not about
 * each other. This is called LOW COUPLING: components are minimally dependent
 * on one another. It makes the code testable (you can test the engine without
 * a GUI), replaceable (you can swap JavaFX for a terminal view), and readable.
 *
 * CONCEPT: Facade Pattern
 * GameController also acts as a FACADE — a simplified interface to a complex
 * system. Rather than forcing the GUI to call game.processAction(), then
 * check game.getState(), then call network.broadcast()... the GUI just calls
 * controller.onPlayerMove(from, to) and the controller handles all of that.
 */
public class GameController {

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final Game   game;         // The model — all game state lives here
    private       Server server;       // Non-null if this instance is the host
    private       Client client;       // Non-null if this instance is a guest

    // The local player's ID: 0 = White (host), 1 = Black (guest)
    private final int localPlayerId;

    // ── Constructor ───────────────────────────────────────────────────────────
    /**
     * @param game          The fully initialised Game object.
     * @param localPlayerId 0 if this player is the host (White), 1 if guest (Black).
     */
    public GameController(Game game, int localPlayerId) {
        this.game          = game;
        this.localPlayerId = localPlayerId;
    }

    // ── Network Setup ─────────────────────────────────────────────────────────

    /** Called when this instance is running as the server (host). */
    public void attachServer(Server server) {
        this.server = server;
    }

    /** Called when this instance is running as the client (guest). */
    public void attachClient(Client client) {
        this.client = client;
    }

    // ── GUI → Controller (Local Input) ────────────────────────────────────────

    /**
     * Called by BoardView when the local player clicks to make a move.
     * This is the primary entry point for GUI-driven actions.
     *
     * The flow:
     *   1. Construct an Action object from the GUI input
     *   2. Submit it to the game engine for validation and application
     *   3. If accepted, broadcast it to the opponent over the network
     *
     * Notice that we DON'T validate the move here. We delegate that entirely
     * to game.processAction(), which in turn calls RulesEngine. The controller's
     * job is coordination, not rule enforcement.
     *
     * @param from  The square the player clicked to pick up a piece.
     * @param to    The square the player clicked to place the piece.
     * @return true if the move was accepted by the engine.
     */
    public boolean onPlayerMove(Position from, Position to) {
        // Only accept input from the local player during their turn
        if (game.getCurrentPlayerId() != localPlayerId) return false;

        Action action = Action.move(localPlayerId, from, to);
        boolean accepted = game.processAction(action);

        if (accepted) {
            broadcast(action); // Tell the opponent what happened
        }
        return accepted;
    }

    /**
     * Called by BoardView when the local player clicks to place a trap.
     * Same flow as onPlayerMove — construct, submit, broadcast.
     *
     * @param where The square where the player wants to place the trap.
     * @return true if the trap placement was accepted.
     */
    public boolean onPlaceTrap(Position where) {
        if (game.getCurrentPlayerId() != localPlayerId) return false;

        Action action = Action.placeTrap(localPlayerId, where);
        boolean accepted = game.processAction(action);

        if (accepted) broadcast(action);
        return accepted;
    }

    /**
     * Called by BoardView when the local player initiates a crown transfer.
     *
     * @param from  The current crown holder's position.
     * @param to    The target piece that will receive the crown.
     * @return true if the transfer was accepted.
     */
    public boolean onCrownTransfer(Position from, Position to) {
        if (game.getCurrentPlayerId() != localPlayerId) return false;

        Action action = Action.crownTransfer(localPlayerId, from, to);
        boolean accepted = game.processAction(action);

        if (accepted) broadcast(action);
        return accepted;
    }

    // ── Network → Controller (Remote Input) ───────────────────────────────────

    /**
     * Called by Server or Client when an action string arrives over the network.
     * This is the entry point for the opponent's moves.
     *
     * CONCEPT: Deserializing and Trusting the Network
     * In a real multiplayer game, you would never trust raw input from the
     * network — you'd validate it rigorously. In StrataChess, since the SERVER
     * is authoritative (it validates all moves), the CLIENT can trust actions
     * the server broadcasts (the server already checked them). However, we still
     * run the action through game.processAction() to apply it to the local state.
     *
     * @param serialized  A serialized action string, e.g. "MOVE|1|1,4|3,4"
     */
    public void onRemoteAction(String serialized) {
        try {
            Action action = Action.deserialize(serialized);
            game.processAction(action);
            // No broadcast needed — this came FROM the network; don't echo it back
        } catch (Exception e) {
            System.err.println("[GameController] Failed to parse remote action: " + serialized);
            e.printStackTrace();
        }
    }

    // ── Utility: Legal Move Query ─────────────────────────────────────────────

    /**
     * Returns all legal moves for the piece at the given position.
     * Called by BoardView to highlight valid destination squares when
     * a player clicks to select a piece.
     *
     * CONCEPT: Query vs. Command
     * This method only READS state (a "query") — it doesn't change anything.
     * onPlayerMove() CHANGES state (a "command"). Keeping queries and commands
     * clearly separate is called Command-Query Separation (CQS) — it makes the
     * system easier to reason about, since queries are always safe to call.
     *
     * @param pos The position of the piece to query.
     * @return A list of legal destination squares, or an empty list if none.
     */
    public List<Position> getLegalMovesFor(Position pos) {
        Board board = game.getBoard();
        Piece piece = board.getPieceAt(pos);

        // No piece here, or wrong player's piece
        if (piece == null || piece.getOwnerId() != localPlayerId) {
            return List.of(); // Return immutable empty list
        }

        return RulesEngine.filterLegalMoves(board, piece, game.getPlayer(localPlayerId));
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Broadcasts an action to the opponent over the network.
     * If this is the server, it sends via server.broadcast().
     * If this is the client, it sends via client.send().
     * If there's no network (local play), this does nothing.
     */
    private void broadcast(Action action) {
        String serialized = action.serialize();
        try {
            if (server != null) {
                server.broadcast(serialized);
            } else if (client != null) {
                client.send(serialized);
            }
            // If both are null, we're in local/testing mode — no broadcast needed
        } catch (Exception e) {
            System.err.println("[GameController] Network broadcast failed: " + e.getMessage());
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Game getGame()            { return game; }
    public int  getLocalPlayerId()   { return localPlayerId; }
}