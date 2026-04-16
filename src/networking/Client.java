package networking;

import controller.GameController;

import java.io.*;
import java.net.Socket;

public class Client {

    private static final int PORT = 5000;

    private final GameController controller;
    private final String         hostIp;
    private Socket         socket;
    private PrintWriter    out;
    private BufferedReader in;

    private volatile boolean connected = false;

    public Client(GameController controller, String hostIp) {
        this.controller = controller;
        this.hostIp     = hostIp.trim(); // FIX: trim whitespace from IP input
    }

    public void connect() throws IOException {
        System.out.println("[Client] Connecting to " + hostIp + ":" + PORT);

        socket = new Socket();

        // FIX: explicit connect with timeout so the UI doesn't freeze forever
        // if the host IP is wrong — fails after 5 seconds with a clear error
        socket.connect(new java.net.InetSocketAddress(hostIp, PORT), 5000);

        // FIX: TCP_NODELAY — send moves immediately, don't wait to batch them
        socket.setTcpNoDelay(true);

        connected = true;
        System.out.println("[Client] Connected to " + hostIp);

        out = new PrintWriter(
            new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
            true
        );
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        startListening();
    }

    /**
     * FIX: send() now logs the message and checks connection state.
     * Previously moves were silently dropped if send() was called before
     * connect() completed.
     */
    public void send(String message) {
        if (out != null && connected) {
            out.println(message);
            System.out.println("[Client] Sent: " + message);
        } else {
            System.err.println("[Client] Cannot send — not connected.");
        }
    }

    private void startListening() {
        Thread listener = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    System.out.println("[Client] Received: " + msg);
                    // FIX: run on JavaFX thread so UI updates safely
                    javafx.application.Platform.runLater(() ->
                        controller.onRemoteAction(msg)
                    );
                }
            } catch (IOException e) {
                System.err.println("[Client] Connection lost: " + e.getMessage());
                connected = false;
            }
        });
        listener.setDaemon(true);
        listener.setName("Client-Listener");
        listener.start();
    }

    public void close() {
        connected = false;
        try {
            if (in     != null) in.close();
            if (out    != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("[Client] Close error: " + e.getMessage());
        }
    }
}