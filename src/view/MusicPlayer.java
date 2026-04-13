package view;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;
import java.net.URI;

/**
 * CONCEPT: Resource Loading + The Singleton-Adjacent Pattern
 * ─────────────────────────────────────────────────────────────────────────────
 * MusicPlayer wraps JavaFX's MediaPlayer to handle background music.
 *
 * WHY A SEPARATE CLASS?
 * You could write media-loading code directly in Main.java or BoardView.java,
 * but that would tightly couple audio logic to UI logic. If you ever want to
 * change the music system (e.g., add sound effects, crossfade tracks, adjust
 * volume dynamically), you'd be editing your GUI code. Encapsulating music here
 * follows the Single Responsibility Principle: this class knows about audio;
 * the rest of the code only calls play() and stop().
 *
 * HOW JAVAFX AUDIO WORKS:
 *   1. Create a Media object that points to an audio file (via a file:// URI).
 *   2. Wrap it in a MediaPlayer, which provides play/pause/loop controls.
 *   3. Set volume between 0.0 (silent) and 1.0 (full volume).
 *   4. Set the cycle count to INDEFINITE to loop endlessly.
 *
 * GOTCHA — File Paths as URIs:
 * MediaPlayer requires a URI string (e.g., "file:///home/user/theme.mp3"),
 * not a plain path. We use File.toURI().toString() to convert correctly.
 *
 * MUSIC RECOMMENDATION:
 * For the "Game of Thrones vibe" — look for royalty-free tracks with these
 * characteristics on sites like Pixabay.com or FreeMusicArchive.org:
 *   - Slow cello or violin as lead melody
 *   - Deep percussion (timpani or taiko drums)
 *   - No vocals
 *   - Key: D minor or E minor for maximum drama
 *   - Tags to search: "epic medieval", "dark orchestral", "cinematic battle"
 * Place your chosen file at: assets/music/theme.mp3
 */
public class MusicPlayer {

    // The expected path to the music file, relative to the project root.
    private static final String MUSIC_PATH = "assets/music/theme.mp3";

    private MediaPlayer mediaPlayer; // null if the file wasn't found or couldn't be loaded

    // ── Constructor ───────────────────────────────────────────────────────────
    public MusicPlayer() {
        File audioFile = new File(MUSIC_PATH);

        if (!audioFile.exists()) {
            // CONCEPT: Graceful Degradation
            // If the music file isn't found, we log a clear message and continue
            // without crashing. The game should work fine without music.
            // This is better than throwing an exception that halts the game.
            System.out.println("[MusicPlayer] Music file not found at: " + MUSIC_PATH);
            System.out.println("[MusicPlayer] Place a .mp3 file at assets/music/theme.mp3 to enable music.");
            return; // mediaPlayer stays null — all methods below check for null
        }

        try {
            // Convert the file path to a URI string that JavaFX can parse
            URI audioUri = audioFile.toURI();
            Media media  = new Media(audioUri.toString());

            mediaPlayer = new MediaPlayer(media);

            // Loop forever — background music should run for the entire game
            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

            // Volume at 35% — atmospheric background, not distracting
            // This is a good default; the player can adjust in a settings menu later
            mediaPlayer.setVolume(0.35);

            // CONCEPT: Error Callbacks
            // MediaPlayer loads asynchronously. If loading fails (wrong format, corrupt
            // file, codec missing), this callback fires. Without it, failures are silent.
            mediaPlayer.setOnError(() -> {
                System.err.println("[MusicPlayer] Playback error: " + mediaPlayer.getError());
            });

        } catch (Exception e) {
            System.err.println("[MusicPlayer] Failed to initialize: " + e.getMessage());
            mediaPlayer = null;
        }
    }

    // ── Public Controls ───────────────────────────────────────────────────────

    /** Starts playing the background music from wherever it is in the track. */
    public void play() {
        if (mediaPlayer != null) mediaPlayer.play();
    }

    /** Pauses the music without resetting position. Call play() to resume. */
    public void pause() {
        if (mediaPlayer != null) mediaPlayer.pause();
    }

    /** Stops and resets the music to the beginning. */
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    /**
     * Sets the volume level.
     * @param volume A value between 0.0 (silent) and 1.0 (full volume).
     */
    public void setVolume(double volume) {
        if (mediaPlayer != null) {
            // Clamp to [0.0, 1.0] to prevent invalid values
            mediaPlayer.setVolume(Math.max(0.0, Math.min(1.0, volume)));
        }
    }

    /** Returns true if music is currently loaded and playing. */
    public boolean isPlaying() {
        return mediaPlayer != null &&
               mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
    }
}