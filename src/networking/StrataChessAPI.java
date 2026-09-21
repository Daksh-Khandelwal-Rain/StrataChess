package networking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class StrataChessAPI {

    // Base target URL for local FastAPI instance
    private static final String BASE_URL = "http://127.0.0.1:8000";

    // Shared HttpClient instance across all static API calls
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();   


    // ── POST /players ─────────────────────────────────────────────

    /**
     * Registers a new player handle via FastAPI.
     * 
     * @param username The desired handle/username.
     * @return Raw JSON response string from server.
     */
    public static String createPlayer(String username) throws Exception {

        // Construct valid JSON string payload
        String body = String.format("{\"username\": \"%s\"}", username);

        System.out.println("JSON Payload: " + body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/players"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        // Blocking synchronous network send call
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("\n[API] Status Code: " + response.statusCode());
        System.out.println("[API] Response Body:\n" + response.body());

        return response.body();
    }


    // ── POST /games ───────────────────────────────────────────────

    /**
     * Initializes a game session between two players.
     * 
     * @param whitePlayerId Foreign key for player playing White.
     * @param blackPlayerId Foreign key for player playing Black.
     * @return Raw JSON response containing game metadata.
     */
    public static String createGame(int whitePlayerId, int blackPlayerId) throws Exception {

        // Java 15+ Text Block with .formatted() for multi-line JSON strings
        String body = """
            {
                "white_player_id": %d,
                "black_player_id": %d
            }
            """.formatted(
                whitePlayerId,
                blackPlayerId
            );

        System.out.println("JSON Payload: " + body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/games"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("\n[API] Status Code: " + response.statusCode());
        System.out.println("[API] Response Body:\n" + response.body());

        return response.body();
    }
    

    // ── POST /games/{game_id}/moves ───────────────────────────────

    /**
     * Records a move event for an active game session.
     * 
     * @param gameId Path parameter identifying target game.
     * @param playerId ID of player making the move.
     * @param fromSquare Starting chess board coordinate (e.g. "e2").
     * @param toSquare Destination chess board coordinate (e.g. "e4").
     * @param moveNumber Sequence turn number.
     * @return Raw JSON response containing created move telemetry.
     */
    public static String createMove(int gameId, int playerId, String fromSquare, String toSquare, int moveNumber) throws Exception {

        // FIX APPLIED: Quotes added to "%s" for string coordinates, removed from %d for integer move_number
        String body = """
            {
                "player_id": %d,
                "from_square": "%s",
                "to_square": "%s",
                "move_number": %d
            }
            """.formatted(
                playerId,
                fromSquare,
                toSquare,
                moveNumber
            );

        System.out.println("JSON Payload: " + body);

        // Path parameter injected dynamically into endpoint URI string
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/games/" + gameId + "/moves"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("\n[API] Status Code: " + response.statusCode());
        System.out.println("[API] Response Body:\n" + response.body());

        return response.body();
    }


    // ── TEST HARNESS ──────────────────────────────────────────────
    public static void main(String[] args) throws Exception {

        System.out.println("=================================");
        System.out.println("Testing Java → FastAPI → PostgreSQL Pipeline");
        System.out.println("=================================\n");

        // Step 1: Create White Player
        System.out.println("--- 1. Creating White Player ---");
        String p1Response = createPlayer("Daksh_Player1");
        
        // Step 2: Create Black Player
        System.out.println("\n--- 2. Creating Black Player ---");
        String p2Response = createPlayer("Helis_Player2");

        System.out.println("\n------------------------------------------------");
        System.out.println("⚠️  ACTION REQUIRED: Check PostgreSQL or API console logs above.");
        System.out.println("Verify the generated player IDs for Daksh_Player1 and Helis_Player2.");
        System.out.println("------------------------------------------------\n");

        // Step 3: Initialize Game (Replace 1 and 2 with the actual DB IDs returned above)
        int whiteId = 1; 
        int blackId = 2; 

        System.out.println("--- 3. Creating Game Session ---");
        String gameResponse = createGame(whiteId, blackId);

        // Step 4: Record a Move (Assuming Game ID = 1 and Player ID = 1)
        int gameId = 1;
        int playerId = whiteId;

        System.out.println("\n--- 4. Recording Move e2 -> e4 ---");
        String moveResponse = createMove(gameId, playerId, "e2", "e4", 1);

        System.out.println("\n=================================");
        System.out.println("Pipeline Test Execution Complete");
        System.out.println("=================================");
    }
}