package networking;

import controller.GameController;

import java.io.*;
import java.net.*;

/**
 * CONCEPT: TCP Sockets — Two Programs Talking Over a Network
 * ─────────────────────────────────────────────────────────────────────────────
 * A SOCKET is an endpoint for communication between two programs.
 * Think of it like a phone call: one side "picks up" (ServerSocket.accept()),
 * the other side "dials" (new Socket(host, port)), and once connected you
 * have a two-way channel to exchange data.
 *
 * TCP (Transmission Control Protocol) guarantees:
 *   - Messages arrive in order (no reordering)
 *   - Messages arrive complete (no partial data)
 *   - Lost packets are automatically retransmitted
 * This makes TCP ideal for turn-based games where every action must arrive
 * exactly once and in the right order.
 *
 * HOW IT WORKS IN STRATACHESS:
 *   1. The Host creates a ServerSocket on port 5000.
 *      ServerSocket.accept() BLOCKS until a client connects.
 *   2. Once connected, accept() returns a regular Socket.
 *   3. From the Socket, we get:
 *        - An InputStream (bytes coming IN from the client)
 *        - An OutputStream (bytes going OUT to the client)
 *   4. We wrap these in BufferedReader and PrintWriter for line-by-line text I/O.
 *   5. A background thread runs in an infinite loop, reading lines (actions)
 *      from the client and passing them to the controller.
 *
 * CONCEPT: Server Authority
 * In a real multiplayer game, the server VALIDATES all moves. In StrataChess,
 * the host's GameController acts as the authority — it calls RulesEngine to
 * validate all actions (including its own) before applying them. The guest's
 * moves arrive as serialized Action strings; the server validates them before
 * applying them to the shared game state.
 *
 * CONCEPT: Separation of Concerns in Networking
 * The networking layer (Server, Client) does NOT know about game rules.
 * It only knows how to:
 *   a) Send a string to the other player: broadcast(String)
 *   b) Receive a string and hand it to the controller: onRemoteAction(String)
 * All rule logic stays in the engine. Network = pipes. Rules = engine.
 */
public class Server {

    // ── Constants ─────────────────────────────────────────────────────────────
    // Port 5000: a common choice for custom LAN applications.
    // Ports 0–1023 are "well-known" (reserved for HTTP, FTP, etc.).
    // Ports 1024–49151 are "registered" — 5000 is commonly used for dev apps.
    private static final int PORT = 5000;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final GameController controller;

    // ── Socket State ──────────────────────────────────────────────────────────
    private ServerSocket serverSocket; // Listens for incoming connections
    private Socket       clientSocket; // The connected guest's socket
    private PrintWriter  out;          // Output stream TO the guest
    private BufferedReader in;         // Input stream FROM the guest

    // ── Constructor ───────────────────────────────────────────────────────────
    public Server(GameController controller) {
        this.controller = controller;
    }

    // ── Startup ───────────────────────────────────────────────────────────────
    /**
     * Starts the server: opens a ServerSocket, waits for one client to connect,
     * then starts listening for messages from that client.
     *
     * This method BLOCKS until a client connects — call it from a background thread
     * (Main.java does this). Once connected, it returns so the game can begin.
     *
     * @throws IOException if the port is already in use or network fails.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(PORT);

        // Print the host's local IP so they can share it with the opponent
        printLocalIP();

        System.out.println("[Server] Listening on port " + PORT + "...");
        System.out.println("[Server] Waiting for opponent to connect...");

        // accept() BLOCKS until a client connects — this is why we run on a background thread
        clientSocket = serverSocket.accept();

        System.out.println("[Server] Opponent connected from: " + clientSocket.getInetAddress());

        // Set up the text-based I/O streams
        // PrintWriter with autoFlush=true: every println() is immediately sent
        out = new PrintWriter(
            new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())),
            true // autoFlush: flushes after every println()
        );
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        // Start the listening loop on yet another background thread
        startListening();
    }

    // ── Message Sending ───────────────────────────────────────────────────────
    /**
     * Sends a serialized action string to the connected guest.
     * Called by GameController.broadcast() after the host makes a move.
     *
     * PrintWriter.println() sends the string followed by a newline character.
     * The client reads lines with BufferedReader.readLine(), which reads up to
     * the newline. This is the simplest possible framing protocol: one action
     * per line. No headers, no length prefixes needed for our use case.
     *
     * CONCEPT: Framing
     * Raw TCP sends a stream of bytes — it doesn't know about "messages."
     * "Framing" is how you tell the receiver where one message ends and the
     * next begins. We use newlines as delimiters — the simplest approach.
     * HTTP uses "\r\n\r\n" to end headers. JSON-over-TCP often uses
     * length-prefixed framing. Newlines work perfectly for our short action strings.
     */
    public void broadcast(String message) {
        if (out != null) {
            out.println(message); // println adds '\n' which readLine() uses as delimiter
        }
    }

    // ── Message Receiving (background loop) ──────────────────────────────────
    /**
     * CONCEPT: Blocking I/O on a Dedicated Background Thread
     * BufferedReader.readLine() BLOCKS — it waits until a complete line
     * (ending with '\n') arrives. If we ran this on the JavaFX thread, the
     * entire UI would freeze while waiting for the opponent to move.
     *
     * By putting the loop on a daemon thread, the UI stays responsive while
     * the server silently waits in the background. When a message arrives,
     * it's passed to the controller, which updates the game state and calls
     * Platform.runLater() to update the UI.
     */
    private void startListening() {
        Thread listener = new Thread(() -> {
            try {
                String line;
                // readLine() returns null when the connection closes
                while ((line = in.readLine()) != null) {
                    final String action = line; // must be effectively final for lambda
                    System.out.println("[Server] Received from client: " + action);

                    // Hand the action to the controller — it will validate and apply it
                    // Note: we're on a background thread here, so controller must use
                    // Platform.runLater() for any UI updates (BoardView.onMoveMade() does this)
                    controller.onRemoteAction(action);
                }
            } catch (IOException e) {
                System.err.println("[Server] Connection closed: " + e.getMessage());
            }
        });

        // CONCEPT: Daemon Thread
        // A daemon thread is a "background service" thread. When the main application
        // thread exits (user closes the window), daemon threads are automatically killed.
        // Without setDaemon(true), the JVM would stay alive even after the window closes,
        // because the listener thread is still blocked on readLine().
        listener.setDaemon(true);
        listener.setName("Server-Listener");
        listener.start();
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    /**
     * CONCEPT: Resource Cleanup — Always Close What You Open
     * Sockets and streams hold operating system resources (file descriptors).
     * If you don't close them, they leak. The OS has a limit on how many open
     * file descriptors a process can hold — leaking them causes failures.
     *
     * In production code, you'd use try-with-resources for automatic cleanup.
     * Here we call close() manually when the game ends.
     */
    public void close() {
        try {
            if (in           != null) in.close();
            if (out          != null) out.close();
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            System.err.println("[Server] Error during close: " + e.getMessage());
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────
    /**
     * Prints the machine's local (LAN) IP address to the console.
     * The host shares this with the guest so they can connect.
     *
     * CONCEPT: Network Interface Enumeration
     * A machine can have multiple network interfaces (Wi-Fi, Ethernet, VPN, loopback).
     * We iterate through them looking for a non-loopback IPv4 address
     * (127.x.x.x is loopback — only accessible from the same machine).
     */
    private void printLocalIP() {
        try {
            java.util.Enumeration<NetworkInterface> ifaces =
                NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                java.util.Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) { // Only IPv4 for simplicity
                        System.out.println("[Server] Your IP address: " + addr.getHostAddress());
                        System.out.println("[Server] Share this with your opponent.");
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("[Server] Could not determine local IP. Check network settings.");
        }
    }
}