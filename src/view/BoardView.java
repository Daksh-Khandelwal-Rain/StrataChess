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
 * MODES:
 *   NORMAL        — clicking a piece selects it and shows legal moves
 *   PLACING_TRAP  — board shows red squares; next click places a mine there
 *   CROWN_TRANSFER — board shows purple squares; click crown holder then target
 *   WAITING       — not your turn, all clicks ignored
 *
 * ACTION BAR:
 *   [♟ Move]   — return to normal select-and-move mode
 *   [🏪 Store] — opens the Store panel (ALWAYS accessible on your turn)
 *
 * STORE PANEL (two columns):
 *   Left  — 🪤 MINES: cost, limit, rules, [Place Mine] button
 *   Right — ♛ CROWN TRANSFER: cost, rules, status, [Transfer Crown] button
 *
 * FIX SUMMARY:
 *   1. Store always accessible on your turn (was coin-gated = unclickable early game)
 *   2. King sensing: inline Chebyshev math — no external method dependency
 *   3. Game over overlay: full-board semi-transparent screen with winner info
 *   4. Crown transfer costs 5 coins (Economy.CROWN_TRANSFER_COST)
 *   5. Trap limit shows /2 everywhere (Player.MAX_TRAPS = 2)
 */
public class BoardView extends BorderPane implements Game.GameListener {

    private static final int SQUARE_SIZE = 80;
    private static final int BOARD_SIZE  = SQUARE_SIZE * 8;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color LIGHT_SQUARE     = Color.web("#F0D9B5");
    private static final Color DARK_SQUARE      = Color.web("#B58863");
    private static final Color BOARD_BORDER     = Color.web("#4A2C0A");
    private static final Color HIGHLIGHT_SELECT = Color.web("#FFD700CC");
    private static final Color HIGHLIGHT_MOVE   = Color.web("#7FFF0080");
    private static final Color HIGHLIGHT_CHECK  = Color.web("#DC143C99");
    private static final Color HIGHLIGHT_TRAP   = Color.web("#FF450088");
    private static final Color HIGHLIGHT_CROWN  = Color.web("#9B59B688");
    private static final Color TRAP_MINE_COLOR  = Color.web("#CC4400CC");
    private static final Color TRAP_SENSE_COLOR = Color.web("#FFD700BB");

    private static final String[] WHITE_SYMBOLS = {"♔","♕","♖","♗","♘","♙"};
    private static final String[] BLACK_SYMBOLS = {"♚","♛","♜","♝","♞","♟"};

    // ── Interaction mode ──────────────────────────────────────────────────────
    private enum Mode { NORMAL, PLACING_TRAP, CROWN_TRANSFER, WAITING }
    private Mode mode = Mode.NORMAL;

    // ── Core state ────────────────────────────────────────────────────────────
    private final GameController controller;
    private final boolean        flipped;

    private Position       selectedSquare;
    private List<Position> legalMoveTargets = new ArrayList<>();
    private List<Position> validTrapSquares = new ArrayList<>();
    private List<Position> crownTargets     = new ArrayList<>();
    private boolean        playerInCheck;
    private volatile boolean redrawPending  = false;

    // Animation
    private Position animFrom, animTo;
    private double   animOffsetX, animOffsetY;
    private boolean  isAnimating;

    // ── UI Components ─────────────────────────────────────────────────────────
    private final Canvas          boardCanvas;
    private final GraphicsContext gc;
    private final Label           statusLabel;

    private final Label player0TimeLabel;
    private final Label player1TimeLabel;
    private final Label player0CoinsLabel;
    private final Label player1CoinsLabel;
    private final Label player0TrapsLabel;
    private final Label player1TrapsLabel;

    private final Button moveBtn;
    private final Button storeBtn;

    private final VBox  storePanel;
    private final Timeline clockTimer;

    // Store internal labels — updated when store opens and every clock tick
    private Label  storeTrapCostLabel;
    private Label  storeTrapStatusLabel;
    private Label  storeCrownCostLabel;
    private Label  storeCrownStatusLabel;
    private Button placeMineBtn;
    private Button transferCrownBtn;

    // Game over overlay (on top of board canvas via StackPane)
    private final VBox  gameOverOverlay;
    private final Label winnerNameLabel;
    private final Label winnerReasonLabel;

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
        player0TrapsLabel = styledLabel("Mines: 0/" + Player.MAX_TRAPS, "#FFA07A", 13);
        player1TimeLabel  = styledLabel("07:00", "#FFD700", 20);
        player1CoinsLabel = styledLabel("Coins: 0", "#C0C0C0", 13);
        player1TrapsLabel = styledLabel("Mines: 0/" + Player.MAX_TRAPS, "#FFA07A", 13);

        VBox p0Panel = buildPlayerPanel("White ♙", player0TimeLabel, player0CoinsLabel, player0TrapsLabel);
        VBox p1Panel = buildPlayerPanel("Black ♟", player1TimeLabel, player1CoinsLabel, player1TrapsLabel);

        // ── Action buttons ────────────────────────────────────────────────────
        moveBtn  = actionButton("♟  Move", "#4A4A4A");
        storeBtn = actionButton("🏪  Store", "#2C5F2E");

        moveBtn.setOnAction(e  -> enterMoveMode());
        storeBtn.setOnAction(e -> toggleStore());

        HBox actionBar = new HBox(12, moveBtn, storeBtn);
        actionBar.setAlignment(Pos.CENTER);
        actionBar.setPadding(new Insets(10, 10, 12, 10));
        actionBar.setStyle("-fx-background-color: #1A1A1A;");

        // ── Store panel ───────────────────────────────────────────────────────
        storePanel = buildStorePanel();
        storePanel.setVisible(false);
        storePanel.setManaged(false);

        // ── Game over overlay ─────────────────────────────────────────────────
        winnerNameLabel   = styledLabel("", "#FFD700", 30);
        winnerReasonLabel = styledLabel("", "#FFFFFF", 18);
        gameOverOverlay   = buildGameOverOverlay();

        // Wrap board canvas + overlay in a StackPane so the overlay sits on top
        StackPane boardStack = new StackPane(boardCanvas, gameOverOverlay);
        boardStack.setPrefSize(BOARD_SIZE, BOARD_SIZE);
        boardStack.setMaxSize(BOARD_SIZE, BOARD_SIZE);

        // ── Layout ────────────────────────────────────────────────────────────
        HBox centerRow = new HBox(10, p1Panel, boardStack, p0Panel);
        centerRow.setAlignment(Pos.CENTER);
        centerRow.setPadding(new Insets(10));
        centerRow.setStyle("-fx-background-color: #1A1A1A;");

        VBox mainLayout = new VBox(0, centerRow, storePanel, actionBar);
        mainLayout.setStyle("-fx-background-color: #1A1A1A;");

        setTop(statusBar);
        setCenter(mainLayout);
        setStyle("-fx-background-color: #1A1A1A;");

        // ── Clock timer ───────────────────────────────────────────────────────
        clockTimer = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> updateClockLabels()));
        clockTimer.setCycleCount(Animation.INDEFINITE);
        clockTimer.play();

        redraw();
    }

    // ── Store Panel ───────────────────────────────────────────────────────────

    /**
     * FIX: Completely rebuilt store panel.
     *
     * Two-column layout: Mines on the left, Crown Transfer on the right.
     * The store button is ALWAYS clickable on your turn — you can browse rules
     * and check availability even with 0 coins. Only the buy buttons inside
     * are disabled when you can't afford them.
     */
    private VBox buildStorePanel() {
        Label title = styledLabel("🏪  StrataChess Store", "#FFD700", 16);

        // ── LEFT COLUMN: Mines ────────────────────────────────────────────────
        storeTrapCostLabel   = styledLabel("Cost: " + Economy.TRAP_COST + " coins per mine", "#C0C0C0", 12);
        storeTrapStatusLabel = styledLabel("Mines remaining: " + Player.MAX_TRAPS + "/" + Player.MAX_TRAPS, "#FFA07A", 12);

        VBox trapRules = new VBox(3,
            styledLabel("• Placed invisibly on empty squares in YOUR territory", "#AAAAAA", 11),
            styledLabel("• Destroys any opponent piece that steps on it", "#AAAAAA", 11),
            styledLabel("• The King SURVIVES — trap is removed instead", "#AAAAAA", 11),
            styledLabel("• YOUR King glows ✦ gold when sensing a nearby mine", "#FFD700", 11),
            styledLabel("• Opponent's other pieces see nothing", "#AAAAAA", 11),
            styledLabel("• Placement zone shrinks every 15 turns", "#AAAAAA", 11),
            styledLabel("• Trap kills award NO coins (direct captures do)", "#AAAAAA", 11)
        );
        trapRules.setPadding(new Insets(4, 0, 4, 0));

        placeMineBtn = actionButton("🪤  Place Mine", "#8B4513");
        placeMineBtn.setMaxWidth(Double.MAX_VALUE);
        placeMineBtn.setOnAction(e -> enterTrapPlacementMode());

        VBox trapSection = new VBox(6,
            styledLabel("🪤  MINES", "#FFA07A", 14),
            new Separator(),
            storeTrapCostLabel,
            storeTrapStatusLabel,
            new Separator(),
            trapRules,
            new Separator(),
            placeMineBtn
        );
        trapSection.setPadding(new Insets(10));
        trapSection.setPrefWidth(330);
        trapSection.setStyle(
            "-fx-background-color: #200A00;" +
            "-fx-border-color: #8B4513;" +
            "-fx-border-radius: 5; -fx-background-radius: 5;");

        // ── RIGHT COLUMN: Crown Transfer ──────────────────────────────────────
        storeCrownCostLabel   = styledLabel("Cost: " + Economy.CROWN_TRANSFER_COST + " coins (one-time use)", "#C0C0C0", 12);
        storeCrownStatusLabel = styledLabel("Status: ✓ Available", "#00FF88", 12);

        VBox crownRules = new VBox(3,
            styledLabel("• Moves the crown (win condition) onto another piece", "#AAAAAA", 11),
            styledLabel("• Target must be a non-pawn piece you own", "#AAAAAA", 11),
            styledLabel("• New crown holder moves like a King", "#AAAAAA", 11),
            styledLabel("• Original King becomes a regular capturable piece", "#AAAAAA", 11),
            styledLabel("• Cannot be used while your crown holder is in check", "#AAAAAA", 11),
            styledLabel("• Can escape checkmate if used before being mated!", "#FFD700", 11),
            styledLabel("• One-time only — spend it wisely", "#FF8888", 11)
        );
        crownRules.setPadding(new Insets(4, 0, 4, 0));

        transferCrownBtn = actionButton("♛  Transfer Crown", "#4B0082");
        transferCrownBtn.setMaxWidth(Double.MAX_VALUE);
        transferCrownBtn.setOnAction(e -> enterCrownMode());

        VBox crownSection = new VBox(6,
            styledLabel("♛  CROWN TRANSFER", "#BB88FF", 14),
            new Separator(),
            storeCrownCostLabel,
            storeCrownStatusLabel,
            new Separator(),
            crownRules,
            new Separator(),
            transferCrownBtn
        );
        crownSection.setPadding(new Insets(10));
        crownSection.setPrefWidth(330);
        crownSection.setStyle(
            "-fx-background-color: #0A0020;" +
            "-fx-border-color: #4B0082;" +
            "-fx-border-radius: 5; -fx-background-radius: 5;");

        // ── Assemble ──────────────────────────────────────────────────────────
        HBox columns = new HBox(12, trapSection, crownSection);
        columns.setAlignment(Pos.TOP_CENTER);

        Button closeBtn = actionButton("✕  Close Store", "#4A4A4A");
        closeBtn.setOnAction(e -> { hideStore(); enterMoveMode(); });

        HBox closeRow = new HBox(closeBtn);
        closeRow.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(10, title, columns, closeRow);
        panel.setPadding(new Insets(12, 16, 12, 16));
        panel.setStyle(
            "-fx-background-color: #12080A;" +
            "-fx-border-color: #6B3A1F;" +
            "-fx-border-width: 2 0 0 0;");
        return panel;
    }

    /**
     * FIX: Game over overlay — a semi-transparent panel that slides over the board.
     * Shows winner name, win reason, and a clear ♛ GAME OVER header.
     * Triggered by onGameOver(), sits in a StackPane above the board canvas.
     */
    private VBox buildGameOverOverlay() {
        Label title = new Label("♛  GAME OVER  ♛");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 38));
        title.setTextFill(Color.GOLD);
        title.setStyle("-fx-effect: dropshadow(gaussian, gold, 24, 0.9, 0, 0);");

        VBox box = new VBox(20, title, winnerNameLabel, winnerReasonLabel);
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(BOARD_SIZE, BOARD_SIZE);
        box.setMaxSize(BOARD_SIZE, BOARD_SIZE);
        // Dark translucent background over the board
        box.setStyle("-fx-background-color: rgba(0,0,0,0.84);");
        box.setVisible(false);
        return box;
    }

    // ── Mode Transitions ──────────────────────────────────────────────────────

    private void enterMoveMode() {
        mode             = Mode.NORMAL;
        selectedSquare   = null;
        legalMoveTargets = new ArrayList<>();
        validTrapSquares = new ArrayList<>();
        crownTargets     = new ArrayList<>();
        highlightButton(moveBtn);
        hideStore();
        statusLabel.setText("Your turn — select a piece to move");
        redraw();
    }

    private void toggleStore() {
        if (storePanel.isVisible()) {
            hideStore();
            enterMoveMode();
        } else {
            showStore();
        }
    }

    /**
     * FIX: Store always opens on your turn — no coin check here.
     * Coin/availability checks only affect the BUY BUTTONS inside the store.
     * This way players can always read the rules and see what they'll be able
     * to buy as they earn more coins.
     */
    private void showStore() {
        refreshStoreLabels();
        storePanel.setVisible(true);
        storePanel.setManaged(true);
        highlightButton(storeBtn);
        statusLabel.setText("🏪 Store — choose Mines or Crown Transfer");
    }

    private void hideStore() {
        storePanel.setVisible(false);
        storePanel.setManaged(false);
    }

    /**
     * Refreshes all dynamic labels and button states inside the store.
     * Called on store open and every clock tick while store is visible.
     */
    private void refreshStoreLabels() {
        Game   game = controller.getGame();
        int    myId = controller.getLocalPlayerId();
        Player me   = game.getPlayer(myId);

        // Mine section
        int minesLeft = Player.MAX_TRAPS - me.getTrapsUsed();
        storeTrapStatusLabel.setText("Mines remaining: " + minesLeft + "/" + Player.MAX_TRAPS);
        storeTrapCostLabel.setText("Cost: " + Economy.TRAP_COST + " coins per mine  |  You have: " + me.getCoins());

        boolean canAffordMine = me.getCoins() >= Economy.TRAP_COST;
        boolean hasMinesLeft  = me.canPlaceTrap();
        boolean canBuyMine    = canAffordMine && hasMinesLeft;

        placeMineBtn.setDisable(!canBuyMine);
        placeMineBtn.setOpacity(canBuyMine ? 1.0 : 0.40);
        if (!hasMinesLeft) {
            placeMineBtn.setText("🪤 Mine Limit Reached (0/" + Player.MAX_TRAPS + " left)");
        } else if (!canAffordMine) {
            placeMineBtn.setText("🪤 Need " + Economy.TRAP_COST + " coins to place");
        } else {
            placeMineBtn.setText("🪤  Place Mine  (" + Economy.TRAP_COST + " coins)");
        }

        // Crown Transfer section
        boolean crownUsed    = me.hasCrownTransferUsed();
        boolean canAffordCrown = me.getCoins() >= Economy.CROWN_TRANSFER_COST;
        boolean canTransfer  = !crownUsed && canAffordCrown;
        storeCrownCostLabel.setText("Cost: " + Economy.CROWN_TRANSFER_COST + " coins  |  You have: " + me.getCoins());

        if (crownUsed) {
            storeCrownStatusLabel.setText("Status: ✗ Already used this game");
            storeCrownStatusLabel.setTextFill(Color.web("#FF4444"));
        } else if (!canAffordCrown) {
            storeCrownStatusLabel.setText("Status: Need " + Economy.CROWN_TRANSFER_COST + " coins  (earn " +
                (Economy.CROWN_TRANSFER_COST - me.getCoins()) + " more)");
            storeCrownStatusLabel.setTextFill(Color.web("#FF8C00"));
        } else {
            storeCrownStatusLabel.setText("Status: ✓ Available — ready to use");
            storeCrownStatusLabel.setTextFill(Color.web("#00FF88"));
        }

        transferCrownBtn.setDisable(!canTransfer);
        transferCrownBtn.setOpacity(canTransfer ? 1.0 : 0.40);
    }

    private void enterTrapPlacementMode() {
        Game   game = controller.getGame();
        Player me   = game.getPlayer(controller.getLocalPlayerId());

        if (me.getCoins() < Economy.TRAP_COST) {
            statusLabel.setText("Not enough coins — need " + Economy.TRAP_COST + " to place a mine.");
            return;
        }
        if (!me.canPlaceTrap()) {
            statusLabel.setText("Mine limit reached — you've placed all " + Player.MAX_TRAPS + " lifetime mines.");
            return;
        }

        mode = Mode.PLACING_TRAP;
        hideStore();
        validTrapSquares = getValidTrapSquares();

        if (validTrapSquares.isEmpty()) {
            statusLabel.setText("No valid squares for mine placement! Territory may be fully occupied.");
            enterMoveMode();
            return;
        }

        statusLabel.setText("🪤 Click a RED square to place your mine  |  Click elsewhere to cancel");
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
        if (me.getCoins() < Economy.CROWN_TRANSFER_COST) {
            statusLabel.setText("Need " + Economy.CROWN_TRANSFER_COST + " coins to transfer the crown! You have " + me.getCoins() + ".");
            flashStatus("#FF8C00");
            return;
        }
        if (RulesEngine.isInCheck(game.getBoard(), controller.getLocalPlayerId(), me)) {
            statusLabel.setText("Cannot transfer crown while your crown holder is in check!");
            flashStatus("#FF4444");
            return;
        }

        hideStore();
        mode           = Mode.CROWN_TRANSFER;
        selectedSquare = null;
        crownTargets   = getEligibleCrownTargets();

        if (crownTargets.isEmpty()) {
            statusLabel.setText("No eligible pieces for crown transfer!");
            enterMoveMode();
            return;
        }

        highlightButton(storeBtn);
        statusLabel.setText("♛ Click YOUR crown holder (★), then click a purple target piece");
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
            default             -> {}
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
        // FIX: Clicking an invalid square now CANCELS placement (not just an error message).
        // This matches the expected UX: "click anywhere else to cancel".
        if (!validTrapSquares.contains(clicked)) {
            statusLabel.setText("Not a valid mine square — placement cancelled.");
            enterMoveMode();
            return;
        }
        boolean ok = controller.onPlaceTrap(clicked);
        if (ok) {
            statusLabel.setText("🪤 Mine placed! Turn ended.");
            enterMoveMode();
        } else {
            statusLabel.setText("Couldn't place mine there — try again.");
        }
    }

    private void handleCrownClick(Position clicked) {
        Game  game    = controller.getGame();
        int   myId    = controller.getLocalPlayerId();
        Piece pieceAt = game.getBoard().getPieceAt(clicked);

        if (selectedSquare == null) {
            if (pieceAt != null && pieceAt.getOwnerId() == myId && pieceAt.isCrownHolder()) {
                selectedSquare = clicked;
                statusLabel.setText("Crown holder selected — now click a purple target piece");
                redraw();
            } else {
                statusLabel.setText("Click YOUR current crown holder first (the piece marked with ★)");
            }
        } else {
            if (crownTargets.contains(clicked)) {
                boolean ok = controller.onCrownTransfer(selectedSquare, clicked);
                if (ok) {
                    statusLabel.setText("♛ Crown transferred! New crown holder: " + clicked);
                    enterMoveMode();
                } else {
                    statusLabel.setText("Crown transfer failed — make sure you're not in check and can afford it.");
                    enterMoveMode();
                }
            } else {
                statusLabel.setText("Invalid target — must click a purple-highlighted non-pawn friendly piece");
            }
        }
    }

    // ── Valid Square Calculators ──────────────────────────────────────────────

    private List<Position> getValidTrapSquares() {
        Game         game  = controller.getGame();
        Board        board = game.getBoard();
        int          myId  = controller.getLocalPlayerId();
        int          turns = game.getTotalTurns();
        List<Position> valid = new ArrayList<>();

        int phase       = Math.min(turns / 15, 3);
        int rowsAllowed = 4 - phase;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position p = new Position(r, c);
                boolean inTerritory = (myId == 0)
                    ? r >= (8 - rowsAllowed)
                    : r < rowsAllowed;
                if (!inTerritory)              continue;
                if (board.getPieceAt(p) != null) continue;
                if (board.getTrapAt(p)  != null) continue;
                valid.add(p);
            }
        }
        return valid;
    }

    private List<Position> getEligibleCrownTargets() {
        Game         game  = controller.getGame();
        Board        board = game.getBoard();
        int          myId  = controller.getLocalPlayerId();
        List<Position> targets = new ArrayList<>();

        for (Piece p : board.getPiecesFor(myId)) {
            if (p.getType() == Piece.Type.PAWN) continue;
            if (p.isCrownHolder())              continue;
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
        Game     game     = controller.getGame();
        Board    board    = game.getBoard();
        int      myId     = controller.getLocalPlayerId();
        Position myKingPos = game.getPlayer(myId).getCrownPosition();

        // Board border + squares
        gc.setFill(BOARD_BORDER);
        gc.fillRect(-4, -4, BOARD_SIZE + 8, BOARD_SIZE + 8);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                gc.setFill((r + c) % 2 == 0 ? LIGHT_SQUARE : DARK_SQUARE);
                gc.fillRect(colToX(c), rowToY(r), SQUARE_SIZE, SQUARE_SIZE);
            }
        }

        // Mode-specific highlights
        if (mode == Mode.PLACING_TRAP) {
            for (Position p : validTrapSquares) highlight(p, HIGHLIGHT_TRAP);
        }
        if (mode == Mode.CROWN_TRANSFER) {
            for (Position p : crownTargets) highlight(p, HIGHLIGHT_CROWN);
            if (selectedSquare != null) highlight(selectedSquare, HIGHLIGHT_SELECT);
        }
        if (mode == Mode.NORMAL) {
            if (selectedSquare != null) highlight(selectedSquare, HIGHLIGHT_SELECT);
            for (Position lm : legalMoveTargets) highlight(lm, HIGHLIGHT_MOVE);
        }

        // Check highlight on crown holder
        if (playerInCheck && myKingPos != null) {
            highlight(myKingPos, HIGHLIGHT_CHECK);
        }

        // ── Traps / Mines ──────────────────────────────────────────────────────
        /**
         * FIX: King sensing logic implemented with inline Chebyshev distance.
         *
         * Chebyshev distance = max(|rowDiff|, |colDiff|)
         * Distance 0 = same square (trap already triggered in that case)
         * Distance 1 = any of the 8 adjacent squares (exactly where sensing fires)
         *
         * This replaces the call to position.chebyshevDistance() which may not
         * exist in all versions of Position.java, making sensing unreliable.
         *
         * Visibility rules:
         *   (a) I always see MY OWN mines (show as orange-red "🪤 MINE")
         *   (b) I see OPPONENT mines if MY king/crown is within 1 square
         *       (show as gold "!" glow — "DANGER")
         *   (c) Everything else is invisible
         */
        for (Trap trap : board.getAllTraps()) {
            boolean isMine   = (trap.getOwnerId() == myId);
            boolean isSensed = false;

            if (!isMine && myKingPos != null) {
                // Inline Chebyshev distance — no method dependency needed
                int rowDiff = Math.abs(trap.getPosition().row - myKingPos.row);
                int colDiff = Math.abs(trap.getPosition().col - myKingPos.col);
                isSensed = Math.max(rowDiff, colDiff) <= 1;
            }

            if (!isMine && !isSensed) continue; // Invisible to this player

            double tx = colToX(trap.getPosition().col);
            double ty = rowToY(trap.getPosition().row);
            double m  = SQUARE_SIZE * 0.14;

            if (isMine) {
                // Owner view: solid orange-red mine marker
                gc.setFill(TRAP_MINE_COLOR);
                gc.fillRoundRect(tx + m, ty + m, SQUARE_SIZE - 2*m, SQUARE_SIZE - 2*m, 10, 10);
                gc.setFont(Font.font("Segoe UI Symbol", FontWeight.BOLD, 22));
                gc.setFill(Color.WHITE);
                gc.fillText("🪤", tx + SQUARE_SIZE * 0.20, ty + SQUARE_SIZE * 0.70);
                gc.setFont(Font.font("Georgia", FontWeight.BOLD, 9));
                gc.setFill(Color.WHITE);
                gc.fillText("MINE", tx + SQUARE_SIZE * 0.26, ty + SQUARE_SIZE * 0.90);
            } else {
                // King sensing: gold glow with danger "!" — opponent can't see the mine itself
                gc.setFill(TRAP_SENSE_COLOR);
                gc.fillRoundRect(tx + m, ty + m, SQUARE_SIZE - 2*m, SQUARE_SIZE - 2*m, 14, 14);
                gc.setFont(Font.font("Georgia", FontWeight.BOLD, 30));
                gc.setFill(Color.web("#FFD700"));
                gc.fillText("!", tx + SQUARE_SIZE * 0.37, ty + SQUARE_SIZE * 0.76);
                gc.setFont(Font.font("Georgia", FontWeight.BOLD, 8));
                gc.setFill(Color.web("#FFD700"));
                gc.fillText("DANGER", tx + SQUARE_SIZE * 0.18, ty + SQUARE_SIZE * 0.91);
            }
        }

        // ── Pieces ────────────────────────────────────────────────────────────
        for (Piece piece : board.getAllPieces()) {
            Position pos = piece.getPosition();
            if (isAnimating && animFrom != null && pos.equals(animFrom)) continue;
            drawPieceAt(piece, colToX(pos.col), rowToY(pos.row));
        }

        // Crown holder ★ star marker (shown on non-King crown holders)
        for (Piece piece : board.getAllPieces()) {
            if (piece.isCrownHolder() && piece.getType() != Piece.Type.KING) {
                double sx = colToX(piece.getPosition().col);
                double sy = rowToY(piece.getPosition().row);
                gc.setFont(Font.font("Segoe UI Symbol", 14));
                gc.setFill(Color.GOLD);
                gc.fillText("★", sx + SQUARE_SIZE - 18, sy + 16);
            }
        }

        // Animating piece drawn on top
        if (isAnimating && animFrom != null) {
            Piece ap = board.getPieceAt(animFrom);
            if (ap != null) drawPieceSymbol(getPieceSymbol(ap), ap.getOwnerId(),
                animOffsetX - SQUARE_SIZE / 2.0, animOffsetY - SQUARE_SIZE / 2.0);
        }

        // Coordinate labels (a-h, 1-8)
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, 11));
        for (int i = 0; i < 8; i++) {
            char file = flipped ? (char)('h' - i) : (char)('a' + i);
            gc.setFill(i % 2 == 0 ? DARK_SQUARE : LIGHT_SQUARE);
            gc.fillText(String.valueOf(file), i * SQUARE_SIZE + SQUARE_SIZE - 12, BOARD_SIZE - 4);
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
            case KING   -> 0; case QUEEN  -> 1; case ROOK   -> 2;
            case BISHOP -> 3; case KNIGHT -> 4; case PAWN   -> 5;
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
        player0TrapsLabel.setText("Mines: " + p0.getTrapsUsed() + "/" + Player.MAX_TRAPS);
        player1TrapsLabel.setText("Mines: " + p1.getTrapsUsed() + "/" + Player.MAX_TRAPS);

        int     myId = controller.getLocalPlayerId();
        boolean myT  = game.getCurrentPlayerId() == myId;

        moveBtn.setDisable(!myT);
        // FIX: Store button only disabled when NOT your turn — never coin-gated here.
        // Coin checks happen inside the store, not at the door.
        storeBtn.setDisable(!myT);

        // Keep store labels fresh if it's currently open
        if (storePanel.isVisible()) {
            refreshStoreLabels();
        }
    }

    private void clearSelection() {
        selectedSquare   = null;
        legalMoveTargets = new ArrayList<>();
    }

    private void highlightButton(Button active) {
        String base = "-fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 8 14;";
        moveBtn.setStyle("-fx-background-color: #4A4A4A; " + base);
        storeBtn.setStyle("-fx-background-color: #2C5F2E; " + base);
        active.setStyle("-fx-background-color: " +
            (active == storeBtn ? "#3A8040" : "#6A6A6A") +
            "; -fx-border-color: #FFD700; -fx-border-width: 2; " + base);
    }

    private void flashStatus(String hexColor) {
        statusLabel.setTextFill(Color.web(hexColor));
        new Timeline(new KeyFrame(Duration.seconds(2.5),
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
        String base = "-fx-border-radius: 5; -fx-background-radius: 5;" +
                      "-fx-padding: 8 14; -fx-cursor: hand;";
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

    // ── GameListener Implementation ───────────────────────────────────────────

    @Override
    public void onMoveMade(Position from, Position to, Piece captured) {
        Platform.runLater(() -> { playerInCheck = false; redraw(); });
    }

    @Override
    public void onTrapPlaced(Trap trap) {
        Platform.runLater(this::redraw);
    }

    @Override
    public void onCrownTransferred(Position from, Position to) {
        Platform.runLater(() -> {
            statusLabel.setText("♛ Crown transferred! New crown holder is at " + to);
            redraw();
        });
    }

    @Override
    public void onTurnChanged(int newId) {
        Platform.runLater(() -> {
            boolean myTurn = newId == controller.getLocalPlayerId();
            if (myTurn) {
                enterMoveMode();
                statusLabel.setText("Your turn! Move a piece or visit the 🏪 Store.");
            } else {
                mode = Mode.WAITING;
                clearSelection();
                hideStore();
                String name = controller.getGame().getPlayer(newId).getName();
                statusLabel.setText("Waiting for " + name + "...");
                redraw();
            }
        });
    }

    @Override
    public void onCheckDetected(int playerId) {
        Platform.runLater(() -> {
            playerInCheck = (playerId == controller.getLocalPlayerId());
            String name = controller.getGame().getPlayer(playerId).getName();
            statusLabel.setText("⚠  " + name + " is in CHECK!");
            flashStatus("#FF4444");
            redraw();
        });
    }

    /**
     * FIX: Checkmate warning — the player is in checkmate but still has crown
     * transfer available as an escape. The game doesn't end yet.
     * We alert them loudly to open the Store and use Crown Transfer NOW.
     */
    @Override
    public void onCheckmateWarning(int playerId) {
        Platform.runLater(() -> {
            String name = controller.getGame().getPlayer(playerId).getName();
            if (playerId == controller.getLocalPlayerId()) {
                playerInCheck = true;
                statusLabel.setText(
                    "☠  CHECKMATE WARNING!  Open 🏪 Store → Crown Transfer to escape NOW!");
                flashStatus("#FF0000");
            } else {
                statusLabel.setText(
                    "♛  " + name + " is in checkmate — they may escape with Crown Transfer!");
            }
            redraw();
        });
    }

    /**
     * FIX: Game over screen — shows a full-board translucent overlay with:
     *   ♛ GAME OVER ♛  (large gold header)
     *   Winner name + symbol
     *   Win reason (CHECKMATE / TIMEOUT)
     *
     * The overlay is a VBox in a StackPane above the board canvas,
     * so it doesn't replace the board — it darkens and overlays it.
     */
    @Override
    public void onGameOver(int winnerId, String reason) {
        Platform.runLater(() -> {
            clockTimer.stop();
            mode = Mode.WAITING;
            hideStore();

            String name   = controller.getGame().getPlayer(winnerId).getName();
            String symbol = (winnerId == 0) ? "♙" : "♟";

            winnerNameLabel.setText(symbol + "  " + name + "  wins!");
            winnerReasonLabel.setText("— by " + reason.toUpperCase() + " —");
            winnerReasonLabel.setTextFill(
                reason.equalsIgnoreCase("checkmate") ? Color.web("#FF6B6B")
              : reason.equalsIgnoreCase("timeout")   ? Color.web("#FFD700")
              : Color.WHITE
            );

            // Reveal the overlay on top of the board
            gameOverOverlay.setVisible(true);

            statusLabel.setText("Game Over — " + name + " wins by " + reason + "!");
            redraw();
        });
    }
}