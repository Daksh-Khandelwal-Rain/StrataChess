package engine;

import shared.Position;

/**
 * CONCEPT: Single Responsibility Principle (SRP)
 * ─────────────────────────────────────────────────────────────────────────────
 * The SRP says: a class should have ONE reason to change.
 * Player.java's only job is to hold and manage per-player state:
 *   - Who am I? (id, name)
 *   - How much time do I have left? (timer)
 *   - How many coins do I have? (economy)
 *   - Where is my crown? (crown tracking)
 *   - Have I used my crown transfer? (one-time ability)
 *   - How many traps have I placed? (trap limit)
 *
 * If we added coin logic to Board.java, or timer logic to Game.java, we'd have
 * scattered state that's hard to find and easy to corrupt. Keeping it here
 * means there is always ONE place to look for anything player-related.
 */
public class Player {

    // ── Constants ─────────────────────────────────────────────────────────────
    /** Each player starts with 7 minutes = 420 seconds on their clock. */
    public static final long INITIAL_TIME_MS = 7 * 60 * 1000L; // milliseconds

    /** Maximum number of traps a player may place across the entire game. */
    public static final int MAX_TRAPS = 3;

    // ── Identity ──────────────────────────────────────────────────────────────
    private final int    id;   // 0 = White (bottom), 1 = Black (top)
    private final String name; // Display name, e.g. "Daksh"

    // ── Economy ───────────────────────────────────────────────────────────────
    private int coins;      // Current coin balance (earn by capturing, spend on traps)
    private int trapsUsed;  // Lifetime traps placed — capped at MAX_TRAPS

    // ── Timer ─────────────────────────────────────────────────────────────────
    // CONCEPT: We store time as milliseconds (long) because System.currentTimeMillis()
    // returns a long. Using int would overflow after ~25 days of milliseconds.
    private long timeRemainingMs;   // How much clock time this player has left
    private long turnStartMs;       // When did this player's current turn begin?
    private boolean clockRunning;   // Is the clock currently ticking?

    // ── Crown Tracking ────────────────────────────────────────────────────────
    // CONCEPT: We store the POSITION of the crown holder, not a reference to
    // the Piece object itself. Why? Because if we stored a Piece reference, and
    // the crown transferred to a new piece, we'd need to update the reference
    // everywhere. A Position is a stable coordinate — the Board always knows
    // what piece is at that position. This is called "referencing by key"
    // rather than "referencing by object."
    private Position crownPosition;       // Where is the current crown holder?
    private boolean  crownTransferUsed;   // Has this player used their one-time transfer?

    // ── Constructor ───────────────────────────────────────────────────────────
    /**
     * Creates a new Player with full time, zero coins, and crown transfer available.
     *
     * @param id   0 for White, 1 for Black.
     * @param name Display name for the player.
     */
    public Player(int id, String name) {
        this.id                = id;
        this.name              = name;
        this.coins             = 0;
        this.trapsUsed         = 0;
        this.timeRemainingMs   = INITIAL_TIME_MS;
        this.turnStartMs       = 0L;
        this.clockRunning      = false;
        this.crownTransferUsed = false;
        this.crownPosition     = null; // Set by Game.java after board setup
    }

    // ── Timer Methods ─────────────────────────────────────────────────────────

    /**
     * CONCEPT: Stateful Timer
     * Instead of decrementing a counter every second (which requires a background
     * thread and causes sync issues), we store the SNAPSHOT of when the turn
     * started. When we need to know time remaining, we compute:
     *   remaining = initialRemaining - (now - turnStartTime)
     *
     * This is more accurate because it's not affected by how often we poll it.
     * The same pattern is used in stopwatches, game engines, and servers.
     */

    /** Call this when it becomes this player's turn. Starts the clock. */
    public void startClock() {
        turnStartMs  = System.currentTimeMillis();
        clockRunning = true;
    }

    /**
     * Call this when this player's turn ends. Stops the clock and deducts
     * the elapsed time from their remaining balance.
     */
    public void stopClock() {
        if (!clockRunning) return; // Guard: don't double-stop
        long elapsed  = System.currentTimeMillis() - turnStartMs;
        timeRemainingMs -= elapsed;
        clockRunning     = false;
    }

    /**
     * Returns the time remaining on the clock in milliseconds.
     * If the clock is running, includes the time elapsed in the current turn.
     * If the clock is stopped, returns the stored remaining time.
     */
    public long getTimeRemainingMs() {
        if (clockRunning) {
            long elapsed = System.currentTimeMillis() - turnStartMs;
            return timeRemainingMs - elapsed;
        }
        return timeRemainingMs;
    }

    /** Returns true if this player's clock has run out — they lose immediately. */
    public boolean isOutOfTime() {
        return getTimeRemainingMs() <= 0;
    }

    // ── Economy Methods ───────────────────────────────────────────────────────

    /** Award coins to this player (called by Economy.java after a capture). */
    public void addCoins(int amount) {
        this.coins += amount;
    }

    /**
     * Attempt to spend coins. Returns false without deducting if insufficient funds.
     * CONCEPT: Returning a boolean instead of throwing an exception here is
     * intentional — "not enough coins" is a normal game event, not an error.
     * Exceptions should be reserved for truly unexpected, exceptional situations.
     */
    public boolean spendCoins(int amount) {
        if (coins < amount) return false;
        coins -= amount;
        return true;
    }

    /** Returns the current coin balance. */
    public int getCoins() { return coins; }

    // ── Trap Methods ──────────────────────────────────────────────────────────

    /**
     * Returns true if this player can still place more traps.
     * Remember: the limit is LIFETIME traps placed, not traps currently on board.
     */
    public boolean canPlaceTrap() {
        return trapsUsed < MAX_TRAPS;
    }

    /** Record that this player placed a trap. Increments the lifetime counter. */
    public void recordTrapPlaced() {
        trapsUsed++;
    }

    /** Returns how many traps this player has placed in total. */
    public int getTrapsUsed() { return trapsUsed; }

    // ── Crown Methods ─────────────────────────────────────────────────────────

    /** Returns the position of this player's current crown holder. */
    public Position getCrownPosition() { return crownPosition; }

    /** Updates the crown position (called during setup and after crown transfer). */
    public void setCrownPosition(Position pos) { this.crownPosition = pos; }

    /** Returns true if this player has already used their crown transfer. */
    public boolean hasCrownTransferUsed() { return crownTransferUsed; }

    /** Marks the crown transfer as used. Called by GameController after execution. */
    public void useCrownTransfer() { this.crownTransferUsed = true; }

    // ── Identity Getters ──────────────────────────────────────────────────────

    public int    getId()   { return id; }
    public String getName() { return name; }

    // ── Formatted Time for Display ────────────────────────────────────────────
    /**
     * Returns a "MM:SS" formatted string of remaining time, for display in the GUI.
     * CONCEPT: Formatting vs. Logic Separation
     * We could do this formatting in BoardView.java, but it's cleaner here
     * because Player already "owns" the time data. The view just calls this
     * and displays the result — it doesn't need to understand milliseconds.
     */
    public String getFormattedTime() {
        long ms      = Math.max(0, getTimeRemainingMs()); // don't show negative
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long secs    = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    @Override
    public String toString() {
        return String.format("Player[%d:%s | coins=%d | time=%s | crownAt=%s]",
                id, name, coins, getFormattedTime(), crownPosition);
    }
}