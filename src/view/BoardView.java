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

public class BoardView extends BorderPane implements Game.GameListener {

    private static final int SQUARE_SIZE = 80;
    private static final int BOARD_SIZE  = SQUARE_SIZE * 8;

    private static final Color LIGHT_SQUARE     = Color.web("#F0D9B5");
    private static final Color DARK_SQUARE      = Color.web("#B58863");
    private static final Color BOARD_BORDER     = Color.web("#4A2C0A");
    private static final Color HIGHLIGHT_SELECT = Color.web("#FFD700CC");
    private static final Color HIGHLIGHT_MOVE   = Color.web("#7FFF0080");
    private static final Color HIGHLIGHT_CHECK  = Color.web("#DC143C99");
    private static final Color TRAP_OWNER_COLOR = Color.web("#FF6B0099");
    private static final Color TRAP_SENSE_COLOR = Color.web("#FFD70066");

    private static final String[] WHITE_SYMBOLS = {"♔","♕","♖","♗","♘","♙"};
    private static final String[] BLACK_SYMBOLS = {"♚","♛","♜","♝","♞","♟"};

    private final GameController controller;

    /**
     * FIX: Board perspective flip.
     * White (player 0) sees row 7 at the bottom — standard chess view.
     * Black (player 1) sees row 0 at the bottom — their pieces are at bottom.
     * flipped = true means we render row 7 at top and row 0 at bottom.
     */
    private final boolean flipped;

    private Position       selectedSquare;
    private List<Position> legalMoveTargets = new ArrayList<>();
    private boolean        playerInCheck;

    // Animation
    private Position animFrom, animTo;
    private double   animOffsetX, animOffsetY;
    private boolean  isAnimating;

    private volatile boolean redrawPending = false;

    private final Canvas          boardCanvas;
    private final GraphicsContext gc;
    private final Label           statusLabel;
    private final Label           player0TimeLabel;
    private final Label           player1TimeLabel;
    private final Label           player0CoinsLabel;
    private final Label           player1CoinsLabel;
    private final Timeline        clockTimer;

    public BoardView(GameController controller) {
        this.controller = controller;

        // FIX: Black player (id=1) sees a flipped board so their pieces are at bottom
        this.flipped = (controller.getLocalPlayerId() == 1);

        controller.getGame().setListener(this);

        boardCanvas = new Canvas(BOARD_SIZE, BOARD_SIZE);
        gc = boardCanvas.getGraphicsContext2D();

        boardCanvas.setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));

        statusLabel       = styledLabel("StrataChess", "#FFD700", 18);
        player0TimeLabel  = styledLabel("07:00", "#FFD700", 22);
        player0CoinsLabel = styledLabel("Coins: 0", "#C0C0C0", 14);
        player1TimeLabel  = styledLabel("07:00", "#FFD700", 22);
        player1CoinsLabel = styledLabel("Coins: 0", "#C0C0C0", 14);

        VBox p0Panel = buildPlayerPanel("White ♙", player0TimeLabel, player0CoinsLabel);
        VBox p1Panel = buildPlayerPanel("Black ♟", player1TimeLabel, player1CoinsLabel);

        HBox statusBar = new HBox(statusLabel);
        statusBar.setAlignment(Pos.CENTER);
        statusBar.setPadding(new Insets(10));
        statusBar.setStyle("-fx-background-color: #1A1A1A;");

        HBox centerRow = new HBox(10, p1Panel, boardCanvas, p0Panel);
        centerRow.setAlignment(Pos.CENTER);
        centerRow.setPadding(new Insets(10));
        centerRow.setStyle("-fx-background-color: #1A1A1A;");

        setTop(statusBar);
        setCenter(centerRow);
        setStyle("-fx-background-color: #1A1A1A;");

        // Clock only updates labels — no canvas redraw
        clockTimer = new Timeline(new KeyFrame(Duration.seconds(1),
            e -> updateClockLabelsOnly()));
        clockTimer.setCycleCount(Animation.INDEFINITE);
        clockTimer.play();

        redraw();
    }

    // ── Coordinate conversion (handles board flip) ────────────────────────────

    /**
     * FIX: Convert pixel click to board Position, accounting for flip.
     * When flipped: clicking the bottom-left pixel means col=7, row=7 in screen
     * coords but that maps to col=0, row=0 in board coords (Black's home corner).
     */
    private Position screenToBoard(double px, double py) {
        int screenCol = (int)(px / SQUARE_SIZE);
        int screenRow = (int)(py / SQUARE_SIZE);
        if (flipped) {
            return new Position(7 - screenRow, 7 - screenCol);
        }
        return new Position(screenRow, screenCol);
    }

    /**
     * Convert board Position to screen pixel top-left corner.
     */
    private double boardColToScreenX(int col) {
        return flipped ? (7 - col) * SQUARE_SIZE : col * SQUARE_SIZE;
    }

    private double boardRowToScreenY(int row) {
        return flipped ? (7 - row) * SQUARE_SIZE : row * SQUARE_SIZE;
    }

    // ── Click Handler ─────────────────────────────────────────────────────────

    private void handleClick(double px, double py) {
        if (isAnimating) return;

        Position clicked = screenToBoard(px, py);
        if (!clicked.isOnBoard()) return;

        Game game = controller.getGame();
        int  myId = controller.getLocalPlayerId();

        // Only respond on your turn
        if (game.getCurrentPlayerId() != myId) {
            statusLabel.setText("Wait for your turn!");
            return;
        }

        Piece pieceAt = game.getBoard().getPieceAt(clicked);

        if (selectedSquare == null) {
            if (pieceAt != null && pieceAt.getOwnerId() == myId) {
                selectedSquare   = clicked;
                legalMoveTargets = controller.getLegalMovesFor(clicked);
                redraw();
            }
        } else {
            if (pieceAt != null && pieceAt.getOwnerId() == myId) {
                // Switch selection to new piece
                selectedSquare   = clicked;
                legalMoveTargets = controller.getLegalMovesFor(clicked);
                redraw();
            } else if (legalMoveTargets.contains(clicked)) {
                Position from = selectedSquare;
                Position to   = clicked;
                clearSelection();
                animateMove(from, to, () -> controller.onPlayerMove(from, to));
            } else {
                clearSelection();
                redraw();
            }
        }
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private void animateMove(Position from, Position to, Runnable onComplete) {
        isAnimating = true;
        animFrom    = from;
        animTo      = to;

        // Start and end pixel centers (using screen coordinates)
        double startX = boardColToScreenX(from.col) + SQUARE_SIZE / 2.0;
        double startY = boardRowToScreenY(from.row) + SQUARE_SIZE / 2.0;
        double endX   = boardColToScreenX(to.col)   + SQUARE_SIZE / 2.0;
        double endY   = boardRowToScreenY(to.row)   + SQUARE_SIZE / 2.0;

        javafx.beans.property.SimpleDoubleProperty xP =
            new javafx.beans.property.SimpleDoubleProperty(startX);
        javafx.beans.property.SimpleDoubleProperty yP =
            new javafx.beans.property.SimpleDoubleProperty(startY);

        xP.addListener((o,ov,nv) -> { animOffsetX = nv.doubleValue(); redraw(); });
        yP.addListener((o,ov,nv) -> { animOffsetY = nv.doubleValue(); redraw(); });

        Timeline anim = new Timeline(new KeyFrame(Duration.millis(500),
            new KeyValue(xP, endX, Interpolator.EASE_BOTH),
            new KeyValue(yP, endY, Interpolator.EASE_BOTH)));

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

        // ── Draw squares ──────────────────────────────────────────────────────
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                // Color based on logical (board) coords — flip doesn't change colors
                boolean isLight = (r + c) % 2 == 0;
                gc.setFill(isLight ? LIGHT_SQUARE : DARK_SQUARE);
                double sx = boardColToScreenX(c);
                double sy = boardRowToScreenY(r);
                gc.fillRect(sx, sy, SQUARE_SIZE, SQUARE_SIZE);
            }
        }

        // ── Highlights ────────────────────────────────────────────────────────
        if (selectedSquare != null)
            highlightSquare(selectedSquare, HIGHLIGHT_SELECT);
        for (Position lm : legalMoveTargets)
            highlightSquare(lm, HIGHLIGHT_MOVE);
        if (playerInCheck) {
            Position crown = game.getPlayer(myId).getCrownPosition();
            if (crown != null) highlightSquare(crown, HIGHLIGHT_CHECK);
        }

        // ── Traps ─────────────────────────────────────────────────────────────
        for (Trap trap : board.getAllTraps()) {
            if (trap.isVisibleTo(myId, crownPos)) {
                Color tc = trap.getOwnerId() == myId ? TRAP_OWNER_COLOR : TRAP_SENSE_COLOR;
                double sx = boardColToScreenX(trap.getPosition().col);
                double sy = boardRowToScreenY(trap.getPosition().row);
                double m  = SQUARE_SIZE * 0.25;
                gc.setFill(tc);
                gc.fillOval(sx + m, sy + m, SQUARE_SIZE - 2*m, SQUARE_SIZE - 2*m);
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Georgia", FontWeight.BOLD, 14));
                gc.fillText("T", sx + SQUARE_SIZE * 0.42, sy + SQUARE_SIZE * 0.62);
            }
        }

        // ── Pieces ────────────────────────────────────────────────────────────
        for (Piece piece : board.getAllPieces()) {
            Position pos = piece.getPosition();
            if (isAnimating && animFrom != null && pos.equals(animFrom)) continue;
            double sx = boardColToScreenX(pos.col);
            double sy = boardRowToScreenY(pos.row);
            drawPieceAt(piece, sx, sy);
        }

        // ── Animating piece on top ────────────────────────────────────────────
        if (isAnimating && animFrom != null) {
            Piece ap = board.getPieceAt(animFrom);
            if (ap != null) {
                drawPieceSymbolAt(getPieceSymbol(ap), ap.getOwnerId(),
                    animOffsetX - SQUARE_SIZE / 2.0,
                    animOffsetY - SQUARE_SIZE / 2.0);
            }
        }

        // ── Coordinate labels ─────────────────────────────────────────────────
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, 11));
        for (int i = 0; i < 8; i++) {
            // File labels (a-h) at bottom
            char file = flipped ? (char)('h' - i) : (char)('a' + i);
            gc.setFill(i % 2 == 0 ? DARK_SQUARE : LIGHT_SQUARE);
            gc.fillText(String.valueOf(file),
                i * SQUARE_SIZE + SQUARE_SIZE - 12, BOARD_SIZE - 4);

            // Rank labels (1-8) on left
            int rank = flipped ? (i + 1) : (8 - i);
            gc.setFill(i % 2 == 0 ? LIGHT_SQUARE : DARK_SQUARE);
            gc.fillText(String.valueOf(rank), 3, i * SQUARE_SIZE + 14);
        }
    }

    private void highlightSquare(Position boardPos, Color color) {
        double sx = boardColToScreenX(boardPos.col);
        double sy = boardRowToScreenY(boardPos.row);
        gc.setFill(color);
        gc.fillRect(sx, sy, SQUARE_SIZE, SQUARE_SIZE);
    }

    private void drawPieceAt(Piece piece, double sx, double sy) {
        drawPieceSymbolAt(getPieceSymbol(piece), piece.getOwnerId(), sx, sy);
    }

    private void drawPieceSymbolAt(String symbol, int ownerId, double sx, double sy) {
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

    private void updateClockLabelsOnly() {
        Game game = controller.getGame();
        player0TimeLabel.setText(game.getPlayer(0).getFormattedTime());
        player1TimeLabel.setText(game.getPlayer(1).getFormattedTime());
        player0CoinsLabel.setText("Coins: " + game.getPlayer(0).getCoins());
        player1CoinsLabel.setText("Coins: " + game.getPlayer(1).getCoins());
    }

    private void clearSelection() {
        selectedSquare   = null;
        legalMoveTargets = new ArrayList<>();
    }

    // ── GameListener ──────────────────────────────────────────────────────────

    @Override
    public void onMoveMade(Position from, Position to, Piece captured) {
        Platform.runLater(() -> { playerInCheck = false; redraw(); });
    }

    @Override public void onTrapPlaced(Trap trap) {
        Platform.runLater(this::redraw);
    }

    @Override public void onCrownTransferred(Position from, Position to) {
        Platform.runLater(() -> {
            statusLabel.setText("Crown transferred to " + to);
            redraw();
        });
    }

    @Override public void onTurnChanged(int newCurrentPlayerId) {
        Platform.runLater(() -> {
            String name = controller.getGame().getPlayer(newCurrentPlayerId).getName();
            boolean myTurn = newCurrentPlayerId == controller.getLocalPlayerId();
            statusLabel.setText(myTurn ? "Your turn!" : name + "'s turn...");
            redraw();
        });
    }

    @Override public void onCheckDetected(int playerId) {
        Platform.runLater(() -> {
            playerInCheck = (playerId == controller.getLocalPlayerId());
            String name = controller.getGame().getPlayer(playerId).getName();
            statusLabel.setText("⚠ " + name + " is in CHECK!");
            redraw();
        });
    }

    @Override public void onGameOver(int winnerId, String reason) {
        Platform.runLater(() -> {
            clockTimer.stop();
            String name = controller.getGame().getPlayer(winnerId).getName();
            statusLabel.setText("♛ " + name + " wins by " + reason + "!");
            redraw();
        });
    }

    private Label styledLabel(String text, String color, int size) {
        Label l = new Label(text);
        l.setFont(Font.font("Georgia", FontWeight.BOLD, size));
        l.setTextFill(Color.web(color));
        return l;
    }

    private VBox buildPlayerPanel(String name, Label timeLabel, Label coinsLabel) {
        Label nl = styledLabel(name, "#C0C0C0", 16);
        VBox panel = new VBox(8, nl, timeLabel, coinsLabel);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(120);
        panel.setStyle("-fx-background-color: #2A2A2A; -fx-border-color: #4A4A4A;" +
                       "-fx-border-radius: 6; -fx-background-radius: 6;");
        return panel;
    }
}