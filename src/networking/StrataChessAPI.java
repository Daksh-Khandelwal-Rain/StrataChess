package networking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class StrataChessAPI {
    // Creating a base url for Http connection
    private static final String BASE_URL ="http://localhost:8000";
    private static final HttpClient client = HttpClient.newHttpClient();

    // ── POST /players ─────────────────────────────────────────────
    public static String createPlayer(String username) throws Exception{
        String body = """
                {"username": "%s"}
                """.formatted(username);

        HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL+"/players"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("Create player response: " +response.body());
        return response.body();
    }

    // ── POST /games ─────────────────────────────────────────────
    public static String createGames(int WhitePlayerId,int BlackPlayerId) throws Exception{
        String body = """
                {White_player_id": %d, "Black_player_id":%d}
                """.formatted(WhitePlayerId,BlackPlayerId);
        
        HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("BASE_URL"+"/games"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

        HttpResponse<String> response= client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Create games response: " +response.body());
        return response.body();
    }
}
