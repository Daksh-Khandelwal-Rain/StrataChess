package view;

import controller.GameController;
import engine.*;
import engine.Piece;
import shared.Position;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: The View in MVC — Pure Presentation Layer
 * ─────────────────────────────────────────────────────────────────────────────
 * BoardView's only job is to DISPLAY the current game state and PASS user
 * input to the controller. It contains zero game logic. It never decides
 * whether a move is legal — it asks the controller, which asks the engine.
 *
 * CONCEPT: Event-Driven GUI Programming
 * In a terminal program, you write code that runs top-to-bottom.
 * In a GUI, the program sits idle and WAITS for events: mouse clicks,
 * key presses, timer ticks. When an event fires, a registered "handler"
 * method runs. JavaFX uses this model exclusively.
 *
 * Here, we register a mouse-click handler on the canvas. When the player
 * clicks, we convert pixel coordinates to board coordinates, determine
 * what they clicked on (a piece? a destination?), and call the controller.
 *
 * CONCEPT: Canvas vs. Scene Graph
 * JavaFX offers two rendering approaches:
 *   1. Scene Graph — individual Node objects (like HTML DOM). Easy to style,
 *      but heavy for 64 squares of chess.
 *   2. Canvas — a raw 2D drawing surface. You draw pixels directly with a
 *      GraphicsContext. Faster for grid-based games, full visual control.
 * We use Canvas for the board and GraphicsContext for drawing.
 *
 * DESIGN AESTHETIC:
 * Chess-inspired dark/gold color palette.
 * Ivory (#F0D9B5) for light squares — classic chess board tone.
 * Dark brown (#B58863) for dark squares — standard wood chess color.
 * Deep black (#1A1A1A) background to frame the board like a stage.
 * Gold (#FFD700) highlights for selected pieces and valid moves.
 * Crimson (#DC143C) for check warnings.
 * All text uses a serif font to match the classical chess aesthetic.
 */
public class BoardView extends BorderPane implements Game.GameListener {

    // ── Layout Constants ──────────────────────────────────────────────────────
    private static final int SQUARE_SIZE  = 80;   // pixels per square
    private static final int BOARD_SIZE   = SQUARE_SIZE * 8; // 640px total

    // ── Chess Aesthetic Colors ────────────────────────────────────────────────
    private static final Color LIGHT_SQUARE     = Color.web("#F0D9B5");
    private static final Color DARK_SQUARE      = Color.web("#B58863");
    private static final Color BOARD_BORDER     = Color.web("#4A2C0A");
    private static final Color BACKGROUND       = Color.web("#1A1A1A");
    private static final Color HIGHLIGHT_SELECT = Color.web("#FFD700CC"); // gold, semi-transparent
    private static final Color HIGHLIGHT_MOVE   = Color.web("#7FFF0080"); // green, semi-transparent
    private static final Color HIGHLIGHT_CHECK  = Color.web("#DC143C99"); // crimson, semi-transparent
    private static final Color TRAP_OWNER_COLOR = Color.web("#FF6B0099"); // orange-red for owned traps
    private static final Color TRAP_SENSE_COLOR = Color.web("#FFD70066"); // gold tint for sensed traps

    // ── Piece Unicode Symbols ──────────────────────────────────────────────────
    // CONCEPT: Unicode for piece rendering without image assets.
    // Chess has standard Unicode codepoints for all pieces.
    // White pieces: ♔♕♖♗♘♙   Black pieces: ♚♛♜♝♞♟
    // This lets us draw beautiful pieces without loading any image files.
    private static final String[] WHITE_SYMBOLS = {"♔", "♕", "♖", "♗", "♘", "♙"};
    private static final String[] BLACK_SYMBOLS = {"♚", "♛", "♜", "♝", "♞", "♟"};

    // ── State ─────────────────────────────────────────────────────────────────
    private final GameController controller;
    private       Position       selectedSquare;    // Which square is currently selected?
    private       List<Position> legalMoveTargets;  // Squares to highlight as valid destinations
    private       boolean        playerInCheck;     // Is the current player in check? (for red flash)

    // ── Animation State ───────────────────────────────────────────────────────
    // CONCEPT: Tracking animation state
    // During a move animation, we store the piece being animated and its
    // current pixel offset (dx, dy). The canvas redraws each frame with
    // the piece drawn at its interpolated position.
    private Position animFrom, animTo;
    private double   animOffsetX, animOffsetY;
    private String   animSymbol;
    private boolean  isAnimating;

    // ── JavaFX Components ─────────────────────────────────────────────────────
    private final Canvas       boardCanvas;
    private final GraphicsContext gc;
    private final Label        statusLabel;
    private final Label        player0TimeLabel;
    private final Label        player1TimeLabel;
    private final Label        player0CoinsLabel;
    private final Label        player1CoinsLabel;

    // Timer that ticks every second to update the clock display
    private final Timeline clockTimer;

    // ── Constructor ───────────────────────────────────────────────────────────
    public BoardView(GameController controller) {
        this.controller       = controller;
        this.legalMoveTargets = new ArrayList<>();
        this.isAnimating      = false;

        // Register this view as the game's listener (Observer Pattern)
        controller.getGame().setListener(this);

        // ── Build the Canvas ──────────────────────────────────────────────────
        boardCanvas = new Canvas(BOARD_SIZE, BOARD_SIZE);
        gc = boardCanvas.getGraphicsContext2D();

        // ── Wire up Mouse Input ───────────────────────────────────────────────
        // CONCEPT: Lambda as Event Handler
        // setOnMouseClicked() takes a functional interface (EventHandler<MouseEvent>).
        // A lambda expression "e -> handleClick(e.getX(), e.getY())" is a concise
        // way to implement a one-method interface without writing a full anonymous class.
        boardCanvas.setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));

        // ── Build Status Bar ──────────────────────────────────────────────────
        statusLabel = styledLabel("Welcome to StrataChess", "#FFD700", 18);
        HBox statusBar = new HBox(statusLabel);
        statusBar.setAlignment(Pos.CENTER);
        statusBar.setPadding(new Insets(10));
        statusBar.setStyle("-fx-background-color: #1A1A1A;");

        // ── Build Player Info Panels ──────────────────────────────────────────
        player0TimeLabel  = styledLabel("07:00", "#FFD700", 22);
        player0CoinsLabel = styledLabel("Coins: 0", "#C0C0C0", 14);
        player1TimeLabel  = styledLabel("07:00", "#FFD700", 22);
        player1CoinsLabel = styledLabel("Coins: 0", "#C0C0C0", 14);

        VBox player0Panel = buildPlayerPanel("White", player0TimeLabel, player0CoinsLabel);
        VBox player1Panel = buildPlayerPanel("Black", player1TimeLabel, player1CoinsLabel);

        // ── Compose Layout ────────────────────────────────────────────────────
        // BorderPane: center=board, top=black info, bottom=white info, top-area=status
        HBox centerRow = new HBox(10, player1Panel, boardCanvas, player0Panel);
        centerRow.setAlignment(Pos.CENTER);
        centerRow.setPadding(new Insets(10));
        centerRow.setStyle("-fx-background-color: #1A1A1A;");

        setTop(statusBar);
        setCenter(centerRow);
        setStyle("-fx-background-color: #1A1A1A;");

        // ── Clock Timer ───────────────────────────────────────────────────────
        // CONCEPT: JavaFX Timeline
        // A Timeline runs KeyFrames at specified intervals.
        // Here, every second, we call updateClockDisplay() to refresh the labels.
        // We must call Platform.runLater() for any UI update from a background
        // thread — but since Timeline runs on the JavaFX Application Thread by
        // default, we can call updateClockDisplay() directly.
        clockTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClockDisplay()));
        clockTimer.setCycleCount(Animation.INDEFINITE);
        clockTimer.play();

        // Draw the initial board
        redraw();
    }

    // ── Mouse Click Handler ───────────────────────────────────────────────────

    /**
     * CONCEPT: Stateful Click Handling — a Two-Click Interaction
     * Chess selection works in two clicks:
     *   Click 1: Select a piece (store selectedSquare, highlight legal moves)
     *   Click 2: Select a destination (submit action to controller, clear selection)
     *
     * If the player clicks their own piece on the second click, we SWITCH
     * selection to the new piece (they changed their mind).
     * If they click an invalid square, we deselect.
     *
     * This two-click model is standard for chess GUIs. The alternative (click
     * and drag) is more work to implement but can be added later.
     */
    private void handleClick(double pixelX, double pixelY) {
        if (isAnimating) return; // Block input during animations

        int col = (int) (pixelX / SQUARE_SIZE);
        int row = (int) (pixelY / SQUARE_SIZE);
        Position clicked = new Position(row, col);

        if (!clicked.isOnBoard()) return;

        Game game = controller.getGame();
        int myId = controller.getLocalPlayerId();

        // Only respond to clicks on your turn
        if (game.getCurrentPlayerId() != myId) return;

        Piece pieceAt = game.getBoard().getPieceAt(clicked);

        if (selectedSquare == null) {
            // ── First click: select a friendly piece ──────────────────────────
            if (pieceAt != null && pieceAt.getOwnerId() == myId) {
                selectedSquare    = clicked;
                legalMoveTargets  = controller.getLegalMovesFor(clicked);
                redraw();
            }
        } else {
            // ── Second click: either re-select or submit move ─────────────────
            if (pieceAt != null && pieceAt.getOwnerId() == myId) {
                // Re-selecting a different friendly piece — switch selection
                selectedSquare   = clicked;
                legalMoveTargets = controller.getLegalMovesFor(clicked);
                redraw();
            } else if (legalMoveTargets.contains(clicked)) {
                // Valid destination — submit the move
                Position from = selectedSquare;
                Position to   = clicked;
                clearSelection();

                // Trigger the 0.5-second animation, THEN tell the controller
                animateMove(from, to, () -> controller.onPlayerMove(from, to));
            } else {
                // Clicked an invalid square — deselect
                clearSelection();
                redraw();
            }
        }
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    /**
     * CONCEPT: JavaFX Animation with Timeline and KeyValues
     * ─────────────────────────────────────────────────────────────────────────
     * JavaFX's animation system works by defining a start state, an end state,
     * and a duration. The engine interpolates between them for every frame.
     *
     * A SimpleDoubleProperty is a special variable that JavaFX can "watch" and
     * animate. We animate two properties — the x and y pixel offsets of the
     * moving piece. On each frame, we update animOffsetX/Y and call redraw().
     *
     * The 0.5-second duration is defined in the spec. It's long enough to see
     * the movement clearly but short enough to feel snappy.
     *
     * After the animation completes, we call onComplete.run() — this is the
     * actual controller.onPlayerMove() call that commits the move to the engine.
     * Animation first, then state change. This keeps visual feedback instant.
     *
     * @param from       Source square.
     * @param to         Destination square.
     * @param onComplete Callback to run when animation finishes.
     */
    private void animateMove(Position from, Position to, Runnable onComplete) {
        isAnimating = true;
        animFrom    = from;
        animTo      = to;

        // Determine which piece symbol to animate
        Piece piece = controller.getGame().getBoard().getPieceAt(from);
        animSymbol  = piece != null ? getPieceSymbol(piece) : "";

        // Pixel centers of source and destination squares
        double startX = from.col * SQUARE_SIZE + SQUARE_SIZE / 2.0;
        double startY = from.row * SQUARE_SIZE + SQUARE_SIZE / 2.0;
        double endX   = to.col   * SQUARE_SIZE + SQUARE_SIZE / 2.0;
        double endY   = to.row   * SQUARE_SIZE + SQUARE_SIZE / 2.0;

        // javafx.beans.property for animatable double values
        javafx.beans.property.SimpleDoubleProperty xProp =
            new javafx.beans.property.SimpleDoubleProperty(startX);
        javafx.beans.property.SimpleDoubleProperty yProp =
            new javafx.beans.property.SimpleDoubleProperty(startY);

        // Redraw every frame during animation to show the piece in-motion
        xProp.addListener((obs, ov, nv) -> { animOffsetX = nv.doubleValue(); redraw(); });
        yProp.addListener((obs, ov, nv) -> { animOffsetY = nv.doubleValue(); redraw(); });

        // CONCEPT: KeyValue + KeyFrame + Timeline = JavaFX's animation primitives
        // A KeyFrame says "at time T, property P should have value V."
        // A Timeline runs between KeyFrames, interpolating values smoothly.
        KeyValue kvX    = new KeyValue(xProp, endX, Interpolator.EASE_BOTH);
        KeyValue kvY    = new KeyValue(yProp, endY, Interpolator.EASE_BOTH);
        KeyFrame kf     = new KeyFrame(Duration.millis(500), kvX, kvY); // 0.5 seconds
        Timeline anim   = new Timeline(kf);

        // EASE_BOTH: starts slow, speeds up in the middle, slows again at the end.
        // This feels more natural than LINEAR (constant speed), which looks robotic.

        anim.setOnFinished(e -> {
            isAnimating = false;
            animFrom = animTo = null;
            animSymbol = null;
            onComplete.run(); // NOW commit the move to the engine
            redraw();
        });

        anim.play();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    /**
     * CONCEPT: Immediate Mode Rendering
     * The Canvas API is "immediate mode" — you clear it and redraw everything
     * from scratch each time. This is different from "retained mode" (like HTML
     * DOM or JavaFX Scene Graph) where you modify persistent objects and the
     * framework re-renders them.
     *
     * Immediate mode is simple and fast for our 64-square board. Every redraw:
     *   1. Clear the canvas
     *   2. Draw all squares (colors + highlights)
     *   3. Draw all pieces (Unicode symbols)
     *   4. Draw trap indicators
     *   5. Draw the animated piece on top (if animating)
     *   6. Draw coordinate labels around the edge
     */
    private void redraw() {
        // Ensure all UI updates happen on the JavaFX Application Thread.
        // CONCEPT: Thread Safety in GUIs
        // JavaFX is NOT thread-safe. Only the "Application Thread" may modify
        // UI components. If a background thread (like a network listener) calls
        // redraw(), Platform.runLater() queues it for execution on the correct thread.
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::redraw);
            return;
        }

        Game  game  = controller.getGame();
        Board board = game.getBoard();
        int   myId  = controller.getLocalPlayerId();

        // Position of the current player's crown holder (for trap visibility)
        Position crownPos = game.getPlayer(myId).getCrownPosition();

        // ── Step 1: Draw Board Border ─────────────────────────────────────────
        gc.setFill(BOARD_BORDER);
        gc.fillRect(-4, -4, BOARD_SIZE + 8, BOARD_SIZE + 8);

        // ── Step 2: Draw Squares ──────────────────────────────────────────────
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                // Alternate light/dark: a square is light if (row + col) is even
                boolean isLight = (r + c) % 2 == 0;
                gc.setFill(isLight ? LIGHT_SQUARE : DARK_SQUARE);
                gc.fillRect(c * SQUARE_SIZE, r * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            }
        }

        // ── Step 3: Draw Highlights ───────────────────────────────────────────
        // Selected square — gold outline
        if (selectedSquare != null) {
            drawHighlight(selectedSquare, HIGHLIGHT_SELECT);
        }

        // Legal move destinations — green dots
        for (Position lm : legalMoveTargets) {
            drawHighlight(lm, HIGHLIGHT_MOVE);
        }

        // Check highlight — crimson flash on the crown holder
        if (playerInCheck) {
            Position crown = game.getPlayer(myId).getCrownPosition();
            if (crown != null) drawHighlight(crown, HIGHLIGHT_CHECK);
        }

        // ── Step 4: Draw Traps ────────────────────────────────────────────────
        for (Trap trap : board.getAllTraps()) {
            if (trap.isVisibleTo(myId, crownPos)) {
                // Owner sees their traps in orange-red; sensed traps (near king) in gold
                boolean isOwner = trap.getOwnerId() == myId;
                Color trapColor = isOwner ? TRAP_OWNER_COLOR : TRAP_SENSE_COLOR;
                int r = trap.getPosition().row;
                int c = trap.getPosition().col;
                gc.setFill(trapColor);
                double margin = SQUARE_SIZE * 0.25;
                gc.fillOval(c * SQUARE_SIZE + margin, r * SQUARE_SIZE + margin,
                            SQUARE_SIZE - 2 * margin, SQUARE_SIZE - 2 * margin);
                // Draw a small "T" label in the center
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Georgia", FontWeight.BOLD, 14));
                gc.fillText("T", c * SQUARE_SIZE + SQUARE_SIZE * 0.42,
                                 r * SQUARE_SIZE + SQUARE_SIZE * 0.62);
            }
        }

        // ── Step 5: Draw Pieces ───────────────────────────────────────────────
        for (Piece piece : board.getAllPieces()) {
            Position pos = piece.getPosition();

            // Skip the animating piece — we'll draw it separately at its interpolated position
            if (isAnimating && animFrom != null && pos.equals(animFrom)) continue;

            drawPieceAt(piece, pos.col * SQUARE_SIZE, pos.row * SQUARE_SIZE);
        }

        // ── Step 6: Draw Animating Piece On Top ───────────────────────────────
        // During animation, draw the moving piece at its current interpolated pixel position
        if (isAnimating && animFrom != null && animSymbol != null) {
            double x = animOffsetX - SQUARE_SIZE / 2.0;
            double y = animOffsetY - SQUARE_SIZE / 2.0;
            Piece animPiece = board.getPieceAt(animFrom);
            if (animPiece != null) {
                drawPieceSymbolAt(animSymbol, animPiece.getOwnerId(), x, y);
            }
        }

        // ── Step 7: Coordinate Labels ─────────────────────────────────────────
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, 11));
        for (int i = 0; i < 8; i++) {
            char file = (char) ('a' + i);
            gc.setFill(i % 2 == 0 ? DARK_SQUARE : LIGHT_SQUARE);
            gc.fillText(String.valueOf(file), i * SQUARE_SIZE + SQUARE_SIZE - 12,
                        BOARD_SIZE - 4); // file labels at bottom
            gc.setFill(i % 2 == 0 ? LIGHT_SQUARE : DARK_SQUARE);
            gc.fillText(String.valueOf(8 - i), 3,
                        i * SQUARE_SIZE + 14); // rank labels on left
        }
    }

    /** Draws a semi-transparent highlight rectangle over a square. */
    private void drawHighlight(Position pos, Color color) {
        gc.setFill(color);
        gc.fillRect(pos.col * SQUARE_SIZE, pos.row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
    }

    /** Draws a piece's Unicode symbol centered on its square. */
    private void drawPieceAt(Piece piece, double x, double y) {
        drawPieceSymbolAt(getPieceSymbol(piece), piece.getOwnerId(), x, y);
    }

    private void drawPieceSymbolAt(String symbol, int ownerId, double x, double y) {
        // Shadow effect: draw the symbol slightly offset in a dark color first
        gc.setFont(Font.font("Segoe UI Symbol", FontWeight.BOLD, SQUARE_SIZE - 16));

        // Shadow pass — adds depth and makes pieces pop off the board
        gc.setFill(Color.rgb(0, 0, 0, 0.4));
        gc.fillText(symbol, x + 10, y + SQUARE_SIZE - 8);

        // Main piece color: warm ivory for White, deep charcoal for Black
        gc.setFill(ownerId == 0 ? Color.web("#FFFFF0") : Color.web("#2B2B2B"));
        gc.fillText(symbol, x + 8, y + SQUARE_SIZE - 10);

        // Crown holder indicator: small gold star above the piece
        Piece p = controller.getGame().getBoard().getPieceAt(new Position(
            (int)(y / SQUARE_SIZE), (int)(x / SQUARE_SIZE)));
        if (p != null && p.isCrownHolder() && p.getType() != Piece.Type.KING) {
            gc.setFont(Font.font("Segoe UI Symbol", 14));
            gc.setFill(Color.GOLD);
            gc.fillText("★", x + SQUARE_SIZE - 18, y + 16); // star in top-right corner
        }
    }

    /** Returns the Unicode chess symbol for a given piece. */
    private String getPieceSymbol(Piece piece) {
        int idx = switch (piece.getType()) {
            case KING   -> 0;
            case QUEEN  -> 1;
            case ROOK   -> 2;
            case BISHOP -> 3;
            case KNIGHT -> 4;
            case PAWN   -> 5;
        };
        return piece.getOwnerId() == 0 ? WHITE_SYMBOLS[idx] : BLACK_SYMBOLS[idx];
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void clearSelection() {
        selectedSquare   = null;
        legalMoveTargets = new ArrayList<>();
    }

    private void updateClockDisplay() {
        Game game = controller.getGame();
        player0TimeLabel.setText(game.getPlayer(0).getFormattedTime());
        player1TimeLabel.setText(game.getPlayer(1).getFormattedTime());
        player0CoinsLabel.setText("Coins: " + game.getPlayer(0).getCoins());
        player1CoinsLabel.setText("Coins: " + game.getPlayer(1).getCoins());
    }

    private Label styledLabel(String text, String hexColor, int size) {
        Label l = new Label(text);
        l.setFont(Font.font("Georgia", FontWeight.BOLD, size));
        l.setTextFill(Color.web(hexColor));
        return l;
    }

    private VBox buildPlayerPanel(String playerName, Label timeLabel, Label coinsLabel) {
        Label nameLabel = styledLabel(playerName, "#C0C0C0", 16);
        VBox panel = new VBox(8, nameLabel, timeLabel, coinsLabel);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(120);
        panel.setStyle("-fx-background-color: #2A2A2A; -fx-border-color: #4A4A4A; " +
                       "-fx-border-radius: 6; -fx-background-radius: 6;");
        return panel;
    }

    // ── GameListener Callbacks ────────────────────────────────────────────────
    /**
     * CONCEPT: Observer Pattern in Action
     * These methods are called by Game.java whenever something important happens.
     * BoardView registered itself as the listener, so it receives these events
     * and updates the UI accordingly.
     *
     * All UI updates MUST happen on the JavaFX Application Thread.
     * Platform.runLater() ensures this even if the callback comes from a
     * network thread.
     */

    @Override
    public void onMoveMade(Position from, Position to, Piece captured) {
        Platform.runLater(() -> {
            playerInCheck = false; // Clear any previous check highlight
            redraw();
        });
    }

    @Override
    public void onTrapPlaced(Trap trap) {
        Platform.runLater(this::redraw);
    }

    @Override
    public void onCrownTransferred(Position from, Position to) {
        Platform.runLater(() -> {
            statusLabel.setText("★ Crown transferred! New crown holder at " + to);
            redraw();
        });
    }

    @Override
    public void onTurnChanged(int newCurrentPlayerId) {
        Platform.runLater(() -> {
            String playerName = controller.getGame().getPlayer(newCurrentPlayerId).getName();
            statusLabel.setText(playerName + "'s turn");
            redraw();
        });
    }

    @Override
    public void onCheckDetected(int playerId) {
        Platform.runLater(() -> {
            playerInCheck = (playerId == controller.getLocalPlayerId());
            String name = controller.getGame().getPlayer(playerId).getName();
            statusLabel.setText("⚠ " + name + " is in CHECK!");
            redraw();
        });
    }

    @Override
    public void onGameOver(int winnerId, String reason) {
        Platform.runLater(() -> {
            clockTimer.stop();
            String name = controller.getGame().getPlayer(winnerId).getName();
            statusLabel.setText("♛ " + name + " wins by " + reason + "! ♛");
            redraw();
        });
    }
}