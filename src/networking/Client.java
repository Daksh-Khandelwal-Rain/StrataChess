package networking;

import controller.GameController;

import java.io.*;
import java.net.Socket;

/**
 * CONCEPT: Client-Side TCP — Dialing the Server
 * ─────────────────────────────────────────────────────────────────────────────
 * The Client is the guest's side of the network connection.
 * While the Server calls ServerSocket.accept() and WAITS for a caller,
 * the Client calls new Socket(host, port) and actively CONNECTS to the server.
 *
 * Once connected, the Client and Server are symmetric: both have an InputStream
 * and OutputStream pointing at each other. Data written to one end's
 * OutputStream comes out of the other end's InputStream. The flow for a
 * guest making a move looks like:
 *
 *   Guest clicks       → GameController.onPlayerMove(from, to)
 *   Controller builds  → Action.move(1, from, to)
 *   Controller calls   → client.send(action.serialize())
 *   Client writes      → "MOVE|1|6,4|4,4\n" to output stream
 *   ──── network ────────────────────────────────────────────────────────────
 *   Server reads       → "MOVE|1|6,4|4,4" from input stream
 *   Server calls       → controller.onRemoteAction("MOVE|1|6,4|4,4")
 *   Controller parses  → Action.deserialize(...)
 *   Controller calls   → game.processAction(action)
 *   Game validates     → RulesEngine.isLegalAction(...)
 *   Game applies       → board.applyMove(from, to)
 *   Game notifies      → listener.onMoveMade(from, to, captured)
 *   BoardView redraws  → Platform.runLater(() -> redraw())
 *
 * This full round-trip is how every networked game action works.
 * Understanding this flow is the single most important thing in this file.
 *
 * CONCEPT: Symmetry in Network Code
 * Notice that Client and Server are nearly identical in structure. This
 * happens because TCP is a symmetric protocol — both ends have the same
 * capabilities. The only asymmetry is connection setup (one listens, one dials).
 * Once connected, they're equal peers.
 */
public class Client {

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final GameController controller;
    private final String         hostIp;      // The host's IP address to connect to
    private static final int     PORT = 5000; // Must match Server.PORT

    // ── Socket State ──────────────────────────────────────────────────────────
    private Socket         socket; // Our connection to the server
    private PrintWriter    out;    // Bytes going TO the server
    private BufferedReader in;     // Bytes coming FROM the server

    // ── Constructor ───────────────────────────────────────────────────────────
    public Client(GameController controller, String hostIp) {
        this.controller = controller;
        this.hostIp     = hostIp;
    }

    // ── Connection ────────────────────────────────────────────────────────────
    /**
     * Connects to the server at the stored IP and port.
     * This method BLOCKS until the connection succeeds or fails.
     * Call it from a background thread (Main.java does this).
     *
     * CONCEPT: Three-Way Handshake (brief)
     * When new Socket(host, port) runs, TCP performs a "three-way handshake"
     * behind the scenes: the client sends SYN, the server replies SYN-ACK,
     * the client sends ACK. After this, the connection is established and
     * data can flow. From your code's perspective, this all happens inside
     * new Socket() — when it returns, you're connected.
     *
     * @throws IOException if the connection fails (server not running, wrong IP, etc.)
     */
    public void connect() throws IOException {
        System.out.println("[Client] Connecting to " + hostIp + ":" + PORT + "...");

        // This line performs the TCP handshake and blocks until connected or fails
        socket = new Socket(hostIp, PORT);

        System.out.println("[Client] Connected to server at " + hostIp);

        // Set up the same streams as the Server — same framing, same protocol
        out = new PrintWriter(
            new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
            true // autoFlush — every println() immediately sends
        );
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Start the background listening loop
        startListening();
    }

    // ── Message Sending ───────────────────────────────────────────────────────
    /**
     * Sends a serialized action to the server (host).
     * Called by GameController.broadcast() after the guest makes a move.
     *
     * The method name is 'send' instead of 'broadcast' to emphasize that
     * the client sends to ONE recipient (the server), not "broadcasting" to
     * multiple listeners. The server would broadcast to multiple clients if
     * this were a multiplayer game with more than two players.
     */
    public void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // ── Message Receiving ─────────────────────────────────────────────────────
    /**
     * Exactly the same pattern as Server.startListening():
     * a daemon background thread blocking on readLine(), forwarding received
     * messages to the controller.
     *
     * CONCEPT: Code Duplication vs. Abstraction
     * This is almost identical to Server.startListening(). In a larger project,
     * you might extract this into a shared base class or utility method.
     * For a learning project, keeping it duplicated and explicit in both places
     * is actually better — you can read each class independently and understand
     * it fully without cross-referencing a parent class.
     * The trade-off: DRY (Don't Repeat Yourself) vs. DAMP (Descriptive And
     * Meaningful Phrases). For teaching code, DAMP often wins.
     */
    private void startListening() {
        Thread listener = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String action = line;
                    System.out.println("[Client] Received from server: " + action);
                    controller.onRemoteAction(action);
                }
            } catch (IOException e) {
                System.err.println("[Client] Connection closed: " + e.getMessage());
            }
        });
        listener.setDaemon(true);
        listener.setName("Client-Listener");
        listener.start();
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    /**
     * Closes all streams and the socket when the game ends.
     * Same cleanup discipline as Server.close().
     */
    public void close() {
        try {
            if (in     != null) in.close();
            if (out    != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("[Client] Error during close: " + e.getMessage());
        }
    }
}