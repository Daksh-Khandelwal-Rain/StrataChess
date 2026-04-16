package networking;

import controller.GameController;

import java.io.*;
import java.net.*;

public class Server {

    private static final int PORT = 5000;

    private final GameController controller;
    private ServerSocket serverSocket;
    private Socket       clientSocket;
    private PrintWriter  out;
    private BufferedReader in;

    // Track if a client is actually connected
    private volatile boolean clientConnected = false;

    public Server(GameController controller) {
        this.controller = controller;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(PORT);
        serverSocket.setReuseAddress(true); // FIX: allow port reuse if restarting

        printLocalIP();
        System.out.println("[Server] Listening on port " + PORT + "...");

        clientSocket = serverSocket.accept(); // blocks until client connects
        clientConnected = true;

        System.out.println("[Server] Client connected: " + clientSocket.getInetAddress());

        // FIX: set TCP_NODELAY so moves are sent immediately, not buffered
        clientSocket.setTcpNoDelay(true);

        out = new PrintWriter(
            new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())),
            true // autoFlush
        );
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        startListening();
    }

    /**
     * FIX: broadcast now checks if client is connected before sending.
     * Previously it would silently fail if called before connection was ready.
     */
    public void broadcast(String message) {
        if (out != null && clientConnected) {
            out.println(message);
            System.out.println("[Server] Sent: " + message);
        } else {
            System.err.println("[Server] Cannot send — no client connected yet.");
        }
    }

    private void startListening() {
        Thread listener = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    System.out.println("[Server] Received: " + msg);
                    // FIX: apply to local game state on JavaFX thread
                    javafx.application.Platform.runLater(() ->
                        controller.onRemoteAction(msg)
                    );
                }
            } catch (IOException e) {
                System.err.println("[Server] Connection lost: " + e.getMessage());
                clientConnected = false;
            }
        });
        listener.setDaemon(true);
        listener.setName("Server-Listener");
        listener.start();
    }

    public void close() {
        clientConnected = false;
        try {
            if (in           != null) in.close();
            if (out          != null) out.close();
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            System.err.println("[Server] Close error: " + e.getMessage());
        }
    }

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
                    if (addr instanceof Inet4Address) {
                        System.out.println("[Server] Your IP: " + addr.getHostAddress());
                        System.out.println("[Server] Share this with your opponent.");
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("[Server] Could not determine IP.");
        }
    }
}