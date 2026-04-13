package view;

import controller.GameController;
import engine.Game;
import networking.Client;
import networking.Server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * CONCEPT: The Application Entry Point + Dependency Injection
 * ─────────────────────────────────────────────────────────────────────────────
 * In Java, execution begins at main(). In JavaFX, main() calls
 * Application.launch(), which bootstraps the JavaFX runtime and then
 * calls start(Stage) on the JavaFX Application Thread. You can think of
 * start() as JavaFX's version of main().
 *
 * WHY DOES JAVAFX HAVE THIS WEIRD STRUCTURE?
 * The JavaFX Application Thread is a dedicated thread for all UI work.
 * By routing everything through launch() and start(), JavaFX guarantees
 * that your UI code always runs on the correct thread. If you tried to
 * create a Stage from a random background thread, it would crash or
 * produce subtle race conditions. The framework enforces thread safety
 * by design.
 *
 * CONCEPT: Dependency Injection (DI)
 * Notice that Main.java creates all the major objects and PASSES them
 * into each other's constructors:
 *
 *   Game game = new Game(...)
 *   GameController ctrl = new GameController(game, playerId)
 *   BoardView view = new BoardView(ctrl)
 *
 * This is called "Dependency Injection" — instead of each class creating
 * its own dependencies (which leads to tight coupling), they RECEIVE them
 * from the outside. The result: any class can be swapped for a different
 * implementation by just changing what you pass in here. The GameController
 * doesn't care if the view is a JavaFX board or a terminal — it works
 * either way, because it only talks to the Game interface.
 *
 * CONCEPT: The Lobby / Setup Screen
 * Before the game starts, players need to choose a role (Host or Guest)
 * and enter connection details. We show a simple lobby screen first, then
 * swap to the game screen once connection is established. This is a common
 * GUI pattern: show screen A, collect info, replace with screen B.
 */
public class Main extends Application {

    private Stage primaryStage;
    private MusicPlayer musicPlayer;

    // ── JavaFX Entry Point ────────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("♟ StrataChess");
        stage.setResizable(false);

        // Start music immediately — it plays throughout lobby and game
        musicPlayer = new MusicPlayer();
        musicPlayer.play();

        // Show the lobby screen first
        showLobby();
    }

    // ── Standard Java Entry Point ─────────────────────────────────────────────
    /**
     * Java's standard main() method. For JavaFX, its only job is to call
     * Application.launch(), which starts the JavaFX runtime and eventually
     * calls start(Stage) above.
     *
     * CONCEPT: Why separate main() and start()?
     * Some environments (like IntelliJ pre-JavaFX 11 module system) have
     * trouble launching JavaFX applications if the main class extends
     * Application directly. A common workaround is to have a plain main()
     * in a separate "launcher" class. Here we keep it simple and put it
     * in the same class — that works fine for our setup.
     */
    public static void main(String[] args) {
        launch(args);
    }

    // ── Lobby Screen ──────────────────────────────────────────────────────────
    /**
     * The lobby screen is a dark, chess-themed panel where the player:
     *   1. Enters their name
     *   2. Chooses Host (create game) or Guest (join a game)
     *   3. If Guest, enters the host's IP address
     *
     * CONCEPT: Scene Graph Construction
     * JavaFX UIs are built as a TREE of Node objects (the "scene graph").
     * Layout containers (VBox, HBox, BorderPane) arrange their children.
     * Leaf nodes (Label, Button, TextField) are the visible UI elements.
     * We set styles via .setStyle() with CSS-like syntax, since JavaFX
     * supports a subset of CSS for styling.
     */
    private void showLobby() {
        // ── Title ─────────────────────────────────────────────────────────────
        Label title = styled("♟ StrataChess", "#FFD700", 42);
        Label sub   = styled("A Chess-Inspired Strategy Game", "#C0C0C0", 16);

        // ── Name Input ────────────────────────────────────────────────────────
        Label namePrompt = styled("Your Name:", "#C0C0C0", 14);
        TextField nameField = styledField("Enter your name...");

        // ── Role Buttons ──────────────────────────────────────────────────────
        Button hostBtn  = styledButton("Host Game (White)");
        Button guestBtn = styledButton("Join Game (Black)");

        // IP input shown only when joining
        Label ipPrompt = styled("Host IP Address:", "#C0C0C0", 14);
        TextField ipField = styledField("e.g. 192.168.1.42");
        ipPrompt.setVisible(false);
        ipField.setVisible(false);

        Button connectBtn = styledButton("Connect & Play");
        connectBtn.setVisible(false);

        // Status label for feedback (errors, connecting...)
        Label statusLabel = styled("", "#FF6B6B", 13);

        // ── Button Interaction ────────────────────────────────────────────────
        // CONCEPT: Mutable closure state
        // We use a single-element boolean array as a mutable flag inside lambdas.
        // Java lambdas can only capture 'effectively final' variables — a 1-element
        // array is a common trick to work around this when you need a mutable flag.
        boolean[] isHost = {false};

        hostBtn.setOnAction(e -> {
            isHost[0] = true;
            ipPrompt.setVisible(false);
            ipField.setVisible(false);
            connectBtn.setText("Host & Play");
            connectBtn.setVisible(true);
            statusLabel.setText("You will play as White. Share your IP with your opponent.");
            statusLabel.setTextFill(Color.web("#90EE90"));
        });

        guestBtn.setOnAction(e -> {
            isHost[0] = false;
            ipPrompt.setVisible(true);
            ipField.setVisible(true);
            connectBtn.setText("Connect & Play");
            connectBtn.setVisible(true);
            statusLabel.setText("You will play as Black. Enter the host's IP address.");
            statusLabel.setTextFill(Color.web("#C0C0C0"));
        });

        connectBtn.setOnAction(e -> {
            String playerName = nameField.getText().trim();
            if (playerName.isEmpty()) playerName = isHost[0] ? "White" : "Black";

            String ip = ipField.getText().trim();
            if (!isHost[0] && ip.isEmpty()) {
                statusLabel.setText("Please enter the host's IP address.");
                statusLabel.setTextFill(Color.web("#FF6B6B"));
                return;
            }

            // Disable the button while connecting to prevent double-clicks
            connectBtn.setDisable(true);
            statusLabel.setTextFill(Color.web("#FFD700"));
            statusLabel.setText(isHost[0] ? "Waiting for opponent to connect..." : "Connecting...");

            // CONCEPT: Background Thread for Blocking I/O
            // Network operations (accepting connections, connecting to a server)
            // can BLOCK — they pause execution until something happens. If we
            // ran this on the JavaFX Application Thread, the entire UI would
            // freeze until the connection is established. Instead, we run the
            // network setup on a background thread and then call
            // Platform.runLater() to switch back to the UI thread when done.
            String finalName = playerName;
            Thread networkThread = new Thread(() -> launchGame(isHost[0], finalName, ip, statusLabel));
            networkThread.setDaemon(true); // Daemon threads are killed when the main thread exits
            networkThread.start();
        });

        // ── Layout Assembly ───────────────────────────────────────────────────
        VBox root = new VBox(16,
            title, sub,
            new Separator(),
            namePrompt, nameField,
            new HBox(16, hostBtn, guestBtn),
            ipPrompt, ipField,
            connectBtn,
            statusLabel
        );
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(50));
        root.setStyle("-fx-background-color: #1A1A1A;");

        HBox center = new HBox(root);
        center.setAlignment(Pos.CENTER);
        center.setStyle("-fx-background-color: #1A1A1A;");

        Scene scene = new Scene(center, 540, 620);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // ── Game Launch (runs on background thread) ───────────────────────────────
    /**
     * Creates the Game, Controller, and network connection, then switches
     * the stage to the game screen. Runs on a background thread so the UI
     * doesn't freeze during network setup.
     */
    private void launchGame(boolean isHost, String playerName, String hostIp, Label statusLabel) {
        try {
            int localPlayerId = isHost ? 0 : 1;

            // ── Create Game and Controller ─────────────────────────────────────
            // White = host (player 0), Black = guest (player 1)
            String whiteName = isHost ? playerName : "Opponent";
            String blackName = isHost ? "Opponent" : playerName;
            Game game = new Game(whiteName, blackName);
            GameController controller = new GameController(game, localPlayerId);

            // ── Setup Network ──────────────────────────────────────────────────
            if (isHost) {
                // Host: start the server and wait for a client to connect
                Server server = new Server(controller);
                controller.attachServer(server);
                server.start(); // Blocks until client connects

                Platform.runLater(() ->
                    statusLabel.setText("Opponent connected! Starting game..."));

            } else {
                // Guest: connect to the host
                Client client = new Client(controller, hostIp);
                controller.attachClient(client);
                client.connect(); // Blocks until connected

                Platform.runLater(() ->
                    statusLabel.setText("Connected! Starting game..."));
            }

            // Brief pause so the player can see the "connected" message
            Thread.sleep(800);

            // Start the game engine
            game.start();

            // ── Switch to Game Screen (must be on JavaFX thread) ──────────────
            Platform.runLater(() -> showGameScreen(controller));

        } catch (Exception ex) {
            Platform.runLater(() -> {
                statusLabel.setText("Connection failed: " + ex.getMessage());
                statusLabel.setTextFill(Color.web("#FF6B6B"));
            });
        }
    }

    // ── Game Screen ───────────────────────────────────────────────────────────
    /**
     * Replaces the lobby scene with the game board.
     * CONCEPT: Scene Switching
     * In JavaFX, a Stage (window) shows one Scene at a time. Switching scenes
     * is as simple as calling stage.setScene(newScene). The old scene and all
     * its nodes are automatically garbage-collected when no longer referenced.
     */
    private void showGameScreen(GameController controller) {
        BoardView boardView = new BoardView(controller);

        Scene gameScene = new Scene(boardView, 900, 720);
        gameScene.setFill(Color.web("#1A1A1A"));

        primaryStage.setScene(gameScene);
        primaryStage.sizeToScene();
        primaryStage.setTitle("♟ StrataChess — " +
            controller.getGame().getPlayer(controller.getLocalPlayerId()).getName());
    }

    // ── Style Helpers ─────────────────────────────────────────────────────────
    // CONCEPT: Helper methods eliminate repetition. Every label and button in
    // the lobby uses the same styling — rather than copy-pasting 4 lines of
    // styling code six times, we write it once in a helper and call it.

    private Label styled(String text, String color, int size) {
        Label l = new Label(text);
        l.setFont(Font.font("Georgia", FontWeight.BOLD, size));
        l.setTextFill(Color.web(color));
        return l;
    }

    private TextField styledField(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setMaxWidth(280);
        f.setStyle("-fx-background-color: #2A2A2A; -fx-text-fill: #F0F0F0; " +
                   "-fx-border-color: #555; -fx-border-radius: 4; " +
                   "-fx-background-radius: 4; -fx-font-size: 14px; -fx-padding: 6 10;");
        return f;
    }

    private Button styledButton(String text) {
        Button b = new Button(text);
        b.setFont(Font.font("Georgia", FontWeight.BOLD, 14));
        b.setTextFill(Color.web("#1A1A1A"));
        b.setStyle("-fx-background-color: #FFD700; -fx-border-radius: 5; " +
                   "-fx-background-radius: 5; -fx-padding: 8 18; -fx-cursor: hand;");
        // Hover effect — darken the gold slightly on hover
        b.setOnMouseEntered(e -> b.setStyle(
            "-fx-background-color: #E6C200; -fx-border-radius: 5; " +
            "-fx-background-radius: 5; -fx-padding: 8 18; -fx-cursor: hand;"));
        b.setOnMouseExited(e -> b.setStyle(
            "-fx-background-color: #FFD700; -fx-border-radius: 5; " +
            "-fx-background-radius: 5; -fx-padding: 8 18; -fx-cursor: hand;"));
        return b;
    }
}