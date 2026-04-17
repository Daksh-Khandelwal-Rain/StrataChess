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
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * BoardView — the complete game UI.
 *
 * MODES: The player can be in one of four interaction modes at any time:
 *   NORMAL        — clicking a piece selects it and shows legal moves
 *   PLACING_TRAP  — the next square click places a trap there
 *   CROWN_TRANSFER — the next square click picks the crown transfer target
 *   WAITING       — not your turn, all clicks ignored
 *
 * The bottom action bar has three buttons:
 *   [Move]   — default mode, select and move pieces
 *   [🪤 Buy Trap  (3 coins)]  — opens mini store panel, then enter placement mode
 *   [♛ Crown Transfer]        — enter crown transfer mode
 */
public class BoardView extends BorderPane implements Game.GameListener {

    private static final int SQUARE_SIZE = 80;
    private static final int BOARD_SIZE  = SQUARE_SIZE * 8;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color LIGHT_SQUARE      = Color.web("#F0D9B5");
    private static final Color DARK_SQUARE       = Color.web("#B58863");
    private static final Color BOARD_BORDER      = Color.web("#4A2C0A");
    private static final Color HIGHLIGHT_SELECT  = Color.web("#FFD700CC");
    private static final Color HIGHLIGHT_MOVE    = Color.web("#7FFF0080");
    private static final Color HIGHLIGHT_CHECK   = Color.web("#DC143C99");
    private static final Color HIGHLIGHT_TRAP    = Color.web("#FF450088"); // red zone for trap placement
    private static final Color HIGHLIGHT_CROWN   = Color.web("#9B59B688"); // purple for crown target
    private static final Color TRAP_OWNER_COLOR  = Color.web("#FF6B00BB");
    private static final Color TRAP_SENSE_COLOR  = Color.web("#FFD700AA"); // bright gold — king sensing

    private static final String[] WHITE_SYMBOLS = {"♔","♕","♖","♗","♘","♙"};
    private static final String[] BLACK_SYMBOLS = {"♚","♛","♜","♝","♞","♟"};

    // ── Interaction mode ──────────────────────────────────────────────────────
    private enum Mode { NORMAL, PLACING_TRAP, CROWN_TRANSFER, WAITING }
    private Mode mode = Mode.NORMAL;

    // ── State ─────────────────────────────────────────────────────────────────
    private final GameController controller;
    private final boolean        flipped; // true for Black player

    private Position       selectedSquare;
    private List<Position> legalMoveTargets  = new ArrayList<>();
    private List<Position> validTrapSquares  = new ArrayList<>(); // territory squares for trap placement
    private List<Position> crownTargets      = new ArrayList<>(); // eligible crown transfer targets
    private boolean        playerInCheck;
    private volatile boolean redrawPending   = false;

    // Animation
    private Position animFrom, animTo;
    private double   animOffsetX, animOffsetY;
    private boolean  isAnimating;

    // ── UI Components ─────────────────────────────────────────────────────────
    private final Canvas          boardCanvas;
    private final GraphicsContext gc;
    private final Label           statusLabel;
    private final Label           player0TimeLabel;
    private final Label           player1TimeLabel;
    private final Label           player0CoinsLabel;
    private final Label           player1CoinsLabel;
    private final Label           player0TrapsLabel;
    private final Label           player1TrapsLabel;
    private final Button          trapBtn;
    private final Button          crownBtn;
    private final Button          moveBtn;
    private final VBox            trapStorePanel;
    private final Timeline        clockTimer;

    // ── Constructor ───────────────────────────────────────────────────────────
    public BoardView(GameController controller) {
        this.controller = controller;
        this.flipped    = (controller.getLocalPlayerId() == 1);
        controller.getGame().setListener(this);

        boardCanvas = new Canvas(BOARD_SIZE, BOARD_SIZE);
        gc          = boardCanvas.getGraphicsContext2D();
        boardCanvas.setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));

        // ── Status bar ────────────────────────────────────────────────────────
        statusLabel = styledLabel("StrataChess", "#FFD700", 16);
        HBox statusBar = new HBox(statusLabel);
        statusBar.setAlignment(Pos.CENTER);
        statusBar.setPadding(new Insets(8));
        statusBar.setStyle("-fx-background-color: #111111;");

        // ── Player panels ─────────────────────────────────────────────────────
        player0TimeLabel  = styledLabel("07:00", "#FFD700", 20);
        player0CoinsLabel = styledLabel("Coins: 0", "#C0C0C0", 13);
        player0TrapsLabel = styledLabel("Traps: 0/3", "#FFA07A", 13);
        player1TimeLabel  = styledLabel("07:00", "#FFD700", 20);
        player1CoinsLabel = styledLabel("Coins: 0", "#C0C0C0", 13);
        player1TrapsLabel = styledLabel("Traps: 0/3", "#FFA07A", 13);

        VBox p0Panel = buildPlayerPanel("White ♙", player0TimeLabel, player0CoinsLabel, player0TrapsLabel);
        VBox p1Panel = buildPlayerPanel("Black ♟", player1TimeLabel, player1CoinsLabel, player1TrapsLabel);

        // ── Action buttons ────────────────────────────────────────────────────
        moveBtn  = actionButton("♟ Move", "#4A4A4A");
        trapBtn  = actionButton("🪤 Buy Trap  (3 coins)", "#8B4513");
        crownBtn = actionButton("♛ Crown Transfer", "#4B0082");

        moveBtn.setOnAction(e  -> enterMoveMode());
        trapBtn.setOnAction(e  -> toggleTrapStore());
        crownBtn.setOnAction(e -> enterCrownMode());

        HBox actionBar = new HBox(12, moveBtn, trapBtn, crownBtn);
        actionBar.setAlignment(Pos.CENTER);
        actionBar.setPadding(new Insets(10, 10, 12, 10));
        actionBar.setStyle("-fx-background-color: #1A1A1A;");

        // ── Trap mini-store panel ─────────────────────────────────────────────
        trapStorePanel = buildTrapStore();
        trapStorePanel.setVisible(false);
        trapStorePanel.setManaged(false);

        // ── Board area ────────────────────────────────────────────────────────
        HBox centerRow = new HBox(10, p1Panel, boardCanvas, p0Panel);
        centerRow.setAlignment(Pos.CENTER);
        centerRow.setPadding(new Insets(10));
        centerRow.setStyle("-fx-background-color: #1A1A1A;");

        VBox mainLayout = new VBox(0, centerRow, trapStorePanel, actionBar);
        mainLayout.setStyle("-fx-background-color: #1A1A1A;");

        setTop(statusBar);
        setCenter(mainLayout);
        setStyle("-fx-background-color: #1A1A1A;");

        // ── Clock timer — labels only, no canvas redraw ───────────────────────
        clockTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClockLabels()));
        clockTimer.setCycleCount(Animation.INDEFINITE);
        clockTimer.play();

        redraw();
    }

    // ── Trap Store Panel ──────────────────────────────────────────────────────
    /**
     * The mini-store slides in below the board when the player clicks "Buy Trap".
     * It shows: cost, rules summary, how many traps they have left, and a
     * "Place Trap" button that enters placement mode.
     */
    private VBox buildTrapStore() {
        Label title     = styledLabel("🪤  Trap Store", "#FFD700", 15);
        Label costLine  = styledLabel("Cost: 3 coins per trap", "#C0C0C0", 12);
        Label limitLine = styledLabel("Lifetime limit: 3 traps total", "#C0C0C0", 12);
        Label rule1     = styledLabel("• Destroys any piece that steps on it", "#AAAAAA", 12);
        Label rule2     = styledLabel("• King survives but removes the trap", "#AAAAAA", 12);
        Label rule3     = styledLabel("• Visible only to YOU and opponent's King (within 1 square)", "#AAAAAA", 12);
        Label rule4     = styledLabel("• Can only be placed in YOUR territory (shrinks over time)", "#AAAAAA", 12);

        Button placeBtn = actionButton("✔ Place Trap — Click a valid square", "#8B4513");
        placeBtn.setOnAction(e -> enterTrapPlacementMode());

        Button cancelBtn = actionButton("✕ Cancel", "#4A4A4A");
        cancelBtn.setOnAction(e -> {
            hideTrapStore();
            enterMoveMode();
        });

        HBox btnRow = new HBox(10, placeBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(6, title, costLine, limitLine,
            new Separator(), rule1, rule2, rule3, rule4,
            new Separator(), btnRow);
        panel.setPadding(new Insets(12, 16, 12, 16));
        panel.setStyle("-fx-background-color: #2A1A0A; -fx-border-color: #8B4513;" +
                       "-fx-border-width: 1 0 0 0;");
        return panel;
    }

    // ── Mode Transitions ──────────────────────────────────────────────────────

    private void enterMoveMode() {
        mode             = Mode.NORMAL;
        selectedSquare   = null;
        legalMoveTargets = new ArrayList<>();
        validTrapSquares = new ArrayList<>();
        crownTargets     = new ArrayList<>();
        highlightButton(moveBtn);
        hideTrapStore();
        statusLabel.setText("Your turn — select a piece to move");
        redraw();
    }

    private void toggleTrapStore() {
        if (trapStorePanel.isVisible()) {
            hideTrapStore();
            enterMoveMode();
        } else {
            showTrapStore();
        }
    }

    private void showTrapStore() {
        // Check prerequisites before even showing the store
        Game   game  = controller.getGame();
        Player me    = game.getPlayer(controller.getLocalPlayerId());

        if (me.getCoins() < 3) {
            statusLabel.setText("Not enough coins! Need 3 coins to buy a trap.");
            flashStatus("#FF4444");
            return;
        }
        if (!me.canPlaceTrap()) {
            statusLabel.setText("Trap limit reached — you've used all 3 lifetime traps.");
            flashStatus("#FF4444");
            return;
        }

        trapStorePanel.setVisible(true);
        trapStorePanel.setManaged(true);
        highlightButton(trapBtn);
        mode = Mode.NORMAL; // stay in normal until they click "Place Trap"
        statusLabel.setText("Trap Store — read the rules, then click Place Trap");
    }

    private void hideTrapStore() {
        trapStorePanel.setVisible(false);
        trapStorePanel.setManaged(false);
    }

    private void enterTrapPlacementMode() {
        Game   game = controller.getGame();
        Player me   = game.getPlayer(controller.getLocalPlayerId());

        if (me.getCoins() < 3) {
            statusLabel.setText("Not enough coins!");
            return;
        }
        if (!me.canPlaceTrap()) {
            statusLabel.setText("Trap limit reached!");
            return;
        }

        mode = Mode.PLACING_TRAP;
        hideTrapStore();

        // Calculate valid trap squares (territory + empty + no existing trap)
        validTrapSquares = getValidTrapSquares();

        if (validTrapSquares.isEmpty()) {
            statusLabel.setText("No valid squares for trap placement in your territory!");
            enterMoveMode();
            return;
        }

        statusLabel.setText("🪤 Click a highlighted square to place your trap  |  ESC = cancel");
        redraw();
    }

    private void enterCrownMode() {
        Game   game = controller.getGame();
        Player me   = game.getPlayer(controller.getLocalPlayerId());

        if (me.hasCrownTransferUsed()) {
            statusLabel.setText("Crown transfer already used this game!");
            flashStatus("#FF4444");
            return;
        }
        if (engine.RulesEngine.isInCheck(game.getBoard(),
                controller.getLocalPlayerId(), me)) {
            statusLabel.setText("Cannot transfer crown while in check!");
            flashStatus("#FF4444");
            return;
        }

        mode         = Mode.CROWN_TRANSFER;
        selectedSquare = null;
        crownTargets = getEligibleCrownTargets();

        if (crownTargets.isEmpty()) {
            statusLabel.setText("No eligible pieces for crown transfer!");
            enterMoveMode();
            return;
        }

        highlightButton(crownBtn);
        statusLabel.setText("♛ First click YOUR crown holder, then click the target piece");
        redraw();
    }

    // ── Click Handler ─────────────────────────────────────────────────────────

    private void handleClick(double px, double py) {
        if (isAnimating) return;

        Game game = controller.getGame();
        int  myId = controller.getLocalPlayerId();

        if (game.getCurrentPlayerId() != myId) {
            statusLabel.setText("Wait for your turn...");
            return;
        }

        Position clicked = screenToBoard(px, py);
        if (!clicked.isOnBoard()) return;

        switch (mode) {
            case NORMAL         -> handleNormalClick(clicked);
            case PLACING_TRAP   -> handleTrapClick(clicked);
            case CROWN_TRANSFER -> handleCrownClick(clicked);
        }
    }

    private void handleNormalClick(Position clicked) {
        Game  game    = controller.getGame();
        int   myId    = controller.getLocalPlayerId();
        Piece pieceAt = game.getBoard().getPieceAt(clicked);

        if (selectedSquare == null) {
            if (pieceAt != null && pieceAt.getOwnerId() == myId) {
                selectedSquare   = clicked;
                legalMoveTargets = controller.getLegalMovesFor(clicked);
                statusLabel.setText("Selected " + pieceAt.getType().name().toLowerCase()
                    + " — click a green square to move");
                redraw();
            }
        } else {
            if (legalMoveTargets.contains(clicked)) {
                Position from = selectedSquare;
                Position to   = clicked;
                clearSelection();
                animateMove(from, to, () -> controller.onPlayerMove(from, to));
            } else if (pieceAt != null && pieceAt.getOwnerId() == myId) {
                selectedSquare   = clicked;
                legalMoveTargets = controller.getLegalMovesFor(clicked);
                redraw();
            } else {
                clearSelection();
                redraw();
            }
        }
    }

    private void handleTrapClick(Position clicked) {
        if (!validTrapSquares.contains(clicked)) {
            statusLabel.setText("Invalid square — click a red-highlighted square in your territory");
            return;
        }
        // Place the trap via controller
        boolean ok = controller.onPlaceTrap(clicked);
        if (ok) {
            statusLabel.setText("Trap placed at " + clicked + "! Turn ended.");
            enterMoveMode();
        } else {
            statusLabel.setText("Couldn't place trap there — try another square.");
        }
    }

    private void handleCrownClick(Position clicked) {
        Game  game    = controller.getGame();
        int   myId    = controller.getLocalPlayerId();
        Piece pieceAt = game.getBoard().getPieceAt(clicked);

        if (selectedSquare == null) {
            // First click — must be the current crown holder
            if (pieceAt != null && pieceAt.getOwnerId() == myId && pieceAt.isCrownHolder()) {
                selectedSquare = clicked;
                statusLabel.setText("Crown holder selected — now click the target piece (purple)");
                redraw();
            } else {
                statusLabel.setText("Click YOUR current crown holder first (the piece with ★)");
            }
        } else {
            // Second click — must be an eligible target
            if (crownTargets.contains(clicked)) {
                boolean ok = controller.onCrownTransfer(selectedSquare, clicked);
                if (ok) {
                    statusLabel.setText("♛ Crown transferred! New crown holder: " + clicked);
                    enterMoveMode();
                } else {
                    statusLabel.setText("Crown transfer failed — check the rules.");
                    enterMoveMode();
                }
            } else {
                statusLabel.setText("Invalid target — must be a non-pawn friendly piece (purple highlight)");
            }
        }
    }

    // ── Valid Square Calculators ──────────────────────────────────────────────

    /**
     * Returns all empty squares in the player's current permitted territory
     * that don't already have a trap.
     */
    private List<Position> getValidTrapSquares() {
        Game         game  = controller.getGame();
        Board        board = game.getBoard();
        int          myId  = controller.getLocalPlayerId();
        int          turns = game.getTotalTurns();
        List<Position> valid = new ArrayList<>();

        // Territory: phase shrinks every 15 turns
        int phase       = Math.min(turns / 15, 3);
        int rowsAllowed = 4 - phase;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position p = new Position(r, c);

                // Must be in territory
                boolean inTerritory;
                if (myId == 0) { // White — bottom rows
                    inTerritory = r >= (8 - rowsAllowed);
                } else {         // Black — top rows
                    inTerritory = r < rowsAllowed;
                }
                if (!inTerritory) continue;

                // Must be empty
                if (board.getPieceAt(p) != null) continue;

                // Must not already have a trap
                if (board.getTrapAt(p) != null) continue;

                valid.add(p);
            }
        }
        return valid;
    }

    /**
     * Returns positions of all friendly non-pawn, non-crown pieces
     * eligible to receive the crown transfer.
     */
    private List<Position> getEligibleCrownTargets() {
        Game         game  = controller.getGame();
        Board        board = game.getBoard();
        int          myId  = controller.getLocalPlayerId();
        List<Position> targets = new ArrayList<>();

        for (Piece p : board.getPiecesFor(myId)) {
            if (p.getType() == Piece.Type.PAWN) continue;
            if (p.isCrownHolder()) continue; // can't transfer to itself
            targets.add(p.getPosition());
        }
        return targets;
    }

    // ── Coordinate Helpers ────────────────────────────────────────────────────

    private Position screenToBoard(double px, double py) {
        int sc = (int)(px / SQUARE_SIZE);
        int sr = (int)(py / SQUARE_SIZE);
        if (flipped) return new Position(7 - sr, 7 - sc);
        return new Position(sr, sc);
    }

    private double colToX(int col) {
        return flipped ? (7 - col) * SQUARE_SIZE : col * SQUARE_SIZE;
    }

    private double rowToY(int row) {
        return flipped ? (7 - row) * SQUARE_SIZE : row * SQUARE_SIZE;
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private void animateMove(Position from, Position to, Runnable onComplete) {
        isAnimating = true;
        animFrom    = from;
        animTo      = to;

        double sx = colToX(from.col) + SQUARE_SIZE / 2.0;
        double sy = rowToY(from.row) + SQUARE_SIZE / 2.0;
        double ex = colToX(to.col)   + SQUARE_SIZE / 2.0;
        double ey = rowToY(to.row)   + SQUARE_SIZE / 2.0;

        javafx.beans.property.SimpleDoubleProperty xP =
            new javafx.beans.property.SimpleDoubleProperty(sx);
        javafx.beans.property.SimpleDoubleProperty yP =
            new javafx.beans.property.SimpleDoubleProperty(sy);

        xP.addListener((o,ov,nv) -> { animOffsetX = nv.doubleValue(); redraw(); });
        yP.addListener((o,ov,nv) -> { animOffsetY = nv.doubleValue(); redraw(); });

        Timeline anim = new Timeline(new KeyFrame(Duration.millis(500),
            new KeyValue(xP, ex, Interpolator.EASE_BOTH),
            new KeyValue(yP, ey, Interpolator.EASE_BOTH)));

        anim.setOnFinished(e -> {
            isAnimating = false;
            animFrom = animTo = null;
            onComplete.run();
            redraw();
        });
        anim.play();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private void redraw() {
        if (!Platform.isFxApplicationThread()) {
            if (!redrawPending) {
                redrawPending = true;
                Platform.runLater(() -> { redrawPending = false; redrawNow(); });
            }
            return;
        }
        redrawNow();
    }

    private void redrawNow() {
        Game  game  = controller.getGame();
        Board board = game.getBoard();
        int   myId  = controller.getLocalPlayerId();
        Position crownPos = game.getPlayer(myId).getCrownPosition();

        // Border
        gc.setFill(BOARD_BORDER);
        gc.fillRect(-4, -4, BOARD_SIZE + 8, BOARD_SIZE + 8);

        // Squares
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                gc.setFill((r + c) % 2 == 0 ? LIGHT_SQUARE : DARK_SQUARE);
                gc.fillRect(colToX(c), rowToY(r), SQUARE_SIZE, SQUARE_SIZE);
            }
        }

        // ── Mode-specific highlights ──────────────────────────────────────────

        if (mode == Mode.PLACING_TRAP) {
            // Show all valid trap placement squares in red
            for (Position p : validTrapSquares)
                highlight(p, HIGHLIGHT_TRAP);
        }

        if (mode == Mode.CROWN_TRANSFER) {
            // Show crown transfer targets in purple
            for (Position p : crownTargets)
                highlight(p, HIGHLIGHT_CROWN);
            if (selectedSquare != null)
                highlight(selectedSquare, HIGHLIGHT_SELECT);
        }

        if (mode == Mode.NORMAL) {
            if (selectedSquare != null) highlight(selectedSquare, HIGHLIGHT_SELECT);
            for (Position lm : legalMoveTargets) highlight(lm, HIGHLIGHT_MOVE);
        }

        // Check highlight
        if (playerInCheck) {
            Position crown = game.getPlayer(myId).getCrownPosition();
            if (crown != null) highlight(crown, HIGHLIGHT_CHECK);
        }

        // ── Traps ─────────────────────────────────────────────────────────────
        for (Trap trap : board.getAllTraps()) {
            boolean isOwner  = trap.getOwnerId() == myId;
            boolean isSensed = crownPos != null &&
                               trap.getPosition().chebyshevDistance(crownPos) <= 1;

            if (isOwner || isSensed) {
                double sx = colToX(trap.getPosition().col);
                double sy = rowToY(trap.getPosition().row);
                double m  = SQUARE_SIZE * 0.2;

                // Pulsing visual: owner sees solid orange-red, sensed = gold
                gc.setFill(isOwner ? TRAP_OWNER_COLOR : TRAP_SENSE_COLOR);
                gc.fillRoundRect(sx + m, sy + m,
                    SQUARE_SIZE - 2*m, SQUARE_SIZE - 2*m, 8, 8);

                // Trap icon
                gc.setFont(Font.font("Segoe UI Symbol", FontWeight.BOLD, 20));
                gc.setFill(Color.WHITE);
                gc.fillText("🪤", sx + SQUARE_SIZE * 0.25, sy + SQUARE_SIZE * 0.68);

                // Label: "MINE" vs "!" for sensed
                gc.setFont(Font.font("Georgia", FontWeight.BOLD, 10));
                gc.setFill(Color.WHITE);
                gc.fillText(isOwner ? "MINE" : "!", sx + SQUARE_SIZE * 0.35,
                            sy + SQUARE_SIZE * 0.88);
            }
        }

        // ── Pieces ────────────────────────────────────────────────────────────
        for (Piece piece : board.getAllPieces()) {
            Position pos = piece.getPosition();
            if (isAnimating && animFrom != null && pos.equals(animFrom)) continue;
            drawPieceAt(piece, colToX(pos.col), rowToY(pos.row));
        }

        // Crown holder star marker
        for (Piece piece : board.getAllPieces()) {
            if (piece.isCrownHolder() && piece.getType() != Piece.Type.KING) {
                double sx = colToX(piece.getPosition().col);
                double sy = rowToY(piece.getPosition().row);
                gc.setFont(Font.font("Segoe UI Symbol", 14));
                gc.setFill(Color.GOLD);
                gc.fillText("★", sx + SQUARE_SIZE - 18, sy + 16);
            }
        }

        // Animating piece on top
        if (isAnimating && animFrom != null) {
            Piece ap = board.getPieceAt(animFrom);
            if (ap != null) drawPieceSymbol(getPieceSymbol(ap), ap.getOwnerId(),
                animOffsetX - SQUARE_SIZE / 2.0, animOffsetY - SQUARE_SIZE / 2.0);
        }

        // Coordinate labels
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, 11));
        for (int i = 0; i < 8; i++) {
            char file = flipped ? (char)('h' - i) : (char)('a' + i);
            gc.setFill(i % 2 == 0 ? DARK_SQUARE : LIGHT_SQUARE);
            gc.fillText(String.valueOf(file),
                i * SQUARE_SIZE + SQUARE_SIZE - 12, BOARD_SIZE - 4);
            int rank = flipped ? (i + 1) : (8 - i);
            gc.setFill(i % 2 == 0 ? LIGHT_SQUARE : DARK_SQUARE);
            gc.fillText(String.valueOf(rank), 3, i * SQUARE_SIZE + 14);
        }
    }

    private void highlight(Position p, Color c) {
        gc.setFill(c);
        gc.fillRect(colToX(p.col), rowToY(p.row), SQUARE_SIZE, SQUARE_SIZE);
    }

    private void drawPieceAt(Piece piece, double sx, double sy) {
        drawPieceSymbol(getPieceSymbol(piece), piece.getOwnerId(), sx, sy);
    }

    private void drawPieceSymbol(String symbol, int ownerId, double sx, double sy) {
        gc.setFont(Font.font("Segoe UI Symbol", FontWeight.BOLD, SQUARE_SIZE - 16));
        gc.setFill(Color.rgb(0, 0, 0, 0.35));
        gc.fillText(symbol, sx + 10, sy + SQUARE_SIZE - 8);
        gc.setFill(ownerId == 0 ? Color.web("#FFFFF0") : Color.web("#2B2B2B"));
        gc.fillText(symbol, sx + 8, sy + SQUARE_SIZE - 10);
    }

    private String getPieceSymbol(Piece piece) {
        int idx = switch (piece.getType()) {
            case KING -> 0; case QUEEN -> 1; case ROOK   -> 2;
            case BISHOP -> 3; case KNIGHT -> 4; case PAWN -> 5;
        };
        return piece.getOwnerId() == 0 ? WHITE_SYMBOLS[idx] : BLACK_SYMBOLS[idx];
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void updateClockLabels() {
        Game   game = controller.getGame();
        Player p0   = game.getPlayer(0);
        Player p1   = game.getPlayer(1);

        player0TimeLabel.setText(p0.getFormattedTime());
        player1TimeLabel.setText(p1.getFormattedTime());
        player0CoinsLabel.setText("Coins: " + p0.getCoins());
        player1CoinsLabel.setText("Coins: " + p1.getCoins());
        player0TrapsLabel.setText("Traps: " + p0.getTrapsUsed() + "/3");
        player1TrapsLabel.setText("Traps: " + p1.getTrapsUsed() + "/3");

        // Disable buttons if not your turn
        int myId    = controller.getLocalPlayerId();
        boolean myT = game.getCurrentPlayerId() == myId;
        Player  me  = game.getPlayer(myId);

        trapBtn.setDisable(!myT || me.getCoins() < 3 || !me.canPlaceTrap());
        crownBtn.setDisable(!myT || me.hasCrownTransferUsed());
        moveBtn.setDisable(!myT);
    }

    private void clearSelection() {
        selectedSquare   = null;
        legalMoveTargets = new ArrayList<>();
    }

    private void highlightButton(Button active) {
        String base = "-fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 8 14;";
        moveBtn.setStyle("-fx-background-color: #4A4A4A; " + base);
        trapBtn.setStyle("-fx-background-color: #8B4513; " + base);
        crownBtn.setStyle("-fx-background-color: #4B0082; " + base);
        active.setStyle("-fx-background-color: " + (
            active == trapBtn  ? "#D2691E" :
            active == crownBtn ? "#7B2FBE" : "#6A6A6A"
        ) + "; -fx-border-color: #FFD700; -fx-border-width: 2; " + base);
    }

    private void flashStatus(String hexColor) {
        statusLabel.setTextFill(Color.web(hexColor));
        new Timeline(new KeyFrame(Duration.seconds(2),
            e -> statusLabel.setTextFill(Color.web("#FFD700")))).play();
    }

    private Label styledLabel(String text, String color, int size) {
        Label l = new Label(text);
        l.setFont(Font.font("Georgia", FontWeight.BOLD, size));
        l.setTextFill(Color.web(color));
        return l;
    }

    private Button actionButton(String text, String bgColor) {
        Button b = new Button(text);
        b.setFont(Font.font("Georgia", FontWeight.BOLD, 13));
        b.setTextFill(Color.WHITE);
        String base = "-fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 8 14; -fx-cursor: hand;";
        b.setStyle("-fx-background-color: " + bgColor + "; " + base);
        b.setOnMouseEntered(e -> b.setOpacity(0.85));
        b.setOnMouseExited(e  -> b.setOpacity(1.0));
        return b;
    }

    private VBox buildPlayerPanel(String name, Label time, Label coins, Label traps) {
        Label nl = styledLabel(name, "#C0C0C0", 14);
        VBox panel = new VBox(6, nl, time, coins, traps);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(120);
        panel.setStyle("-fx-background-color: #2A2A2A; -fx-border-color: #4A4A4A;" +
                       "-fx-border-radius: 6; -fx-background-radius: 6;");
        return panel;
    }

    // ── GameListener ──────────────────────────────────────────────────────────

    @Override public void onMoveMade(Position from, Position to, Piece captured) {
        Platform.runLater(() -> { playerInCheck = false; redraw(); });
    }

    @Override public void onTrapPlaced(Trap trap) {
        Platform.runLater(this::redraw);
    }

    @Override public void onCrownTransferred(Position from, Position to) {
        Platform.runLater(() -> {
            statusLabel.setText("♛ Crown transferred! New crown holder at " + to);
            redraw();
        });
    }

    @Override public void onTurnChanged(int newId) {
        Platform.runLater(() -> {
            boolean myTurn = newId == controller.getLocalPlayerId();
            if (myTurn) {
                enterMoveMode();
                statusLabel.setText("Your turn! Move, buy a trap, or transfer crown.");
            } else {
                mode = Mode.WAITING;
                clearSelection();
                String name = controller.getGame().getPlayer(newId).getName();
                statusLabel.setText("Waiting for " + name + "...");
                redraw();
            }
        });
    }

    @Override public void onCheckDetected(int playerId) {
        Platform.runLater(() -> {
            playerInCheck = (playerId == controller.getLocalPlayerId());
            String name = controller.getGame().getPlayer(playerId).getName();
            statusLabel.setText("⚠  " + name + " is in CHECK!");
            flashStatus("#FF4444");
            redraw();
        });
    }

    @Override public void onGameOver(int winnerId, String reason) {
        Platform.runLater(() -> {
            clockTimer.stop();
            mode = Mode.WAITING;
            String name = controller.getGame().getPlayer(winnerId).getName();
            statusLabel.setText("♛  " + name + " wins by " + reason + "!  ♛");
            redraw();
        });
    }
}