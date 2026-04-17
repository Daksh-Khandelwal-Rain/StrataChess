package engine;

import shared.Position;

/**
 * CONCEPT: Single Responsibility Principle (SRP)
 * Player.java's only job is to hold and manage per-player state.
 */
public class Player {

    // ── Constants ─────────────────────────────────────────────────────────────
    public static final long INITIAL_TIME_MS = 7 * 60 * 1000L;

    /**
     * FIX: Maximum lifetime traps reduced from 3 → 2.
     * Fewer mines = each placement is a bigger strategic decision.
     */
    public static final int MAX_TRAPS = 2;

    // ── Identity ──────────────────────────────────────────────────────────────
    private final int    id;
    private final String name;

    // ── Economy ───────────────────────────────────────────────────────────────
    private int coins;
    private int trapsUsed;

    // ── Timer ─────────────────────────────────────────────────────────────────
    private long    timeRemainingMs;
    private long    turnStartMs;
    private boolean clockRunning;

    // ── Crown Tracking ────────────────────────────────────────────────────────
    private Position crownPosition;
    private boolean  crownTransferUsed;

    // ── Constructor ───────────────────────────────────────────────────────────
    public Player(int id, String name) {
        this.id                = id;
        this.name              = name;
        this.coins             = 0;
        this.trapsUsed         = 0;
        this.timeRemainingMs   = INITIAL_TIME_MS;
        this.turnStartMs       = 0L;
        this.clockRunning      = false;
        this.crownTransferUsed = false;
        this.crownPosition     = null;
    }

    // ── Timer Methods ─────────────────────────────────────────────────────────

    public void startClock() {
        turnStartMs  = System.currentTimeMillis();
        clockRunning = true;
    }

    public void stopClock() {
        if (!clockRunning) return;
        long elapsed  = System.currentTimeMillis() - turnStartMs;
        timeRemainingMs -= elapsed;
        clockRunning     = false;
    }

    public long getTimeRemainingMs() {
        if (clockRunning) {
            long elapsed = System.currentTimeMillis() - turnStartMs;
            return timeRemainingMs - elapsed;
        }
        return timeRemainingMs;
    }

    public boolean isOutOfTime() {
        return getTimeRemainingMs() <= 0;
    }

    // ── Economy Methods ───────────────────────────────────────────────────────

    public void addCoins(int amount) {
        this.coins += amount;
    }

    public boolean spendCoins(int amount) {
        if (coins < amount) return false;
        coins -= amount;
        return true;
    }

    public int getCoins() { return coins; }

    // ── Trap Methods ──────────────────────────────────────────────────────────

    public boolean canPlaceTrap() {
        return trapsUsed < MAX_TRAPS;
    }

    public void recordTrapPlaced() {
        trapsUsed++;
    }

    public int getTrapsUsed() { return trapsUsed; }

    // ── Crown Methods ─────────────────────────────────────────────────────────

    public Position getCrownPosition()          { return crownPosition; }
    public void     setCrownPosition(Position p){ this.crownPosition = p; }
    public boolean  hasCrownTransferUsed()      { return crownTransferUsed; }
    public void     useCrownTransfer()          { this.crownTransferUsed = true; }

    // ── Identity ──────────────────────────────────────────────────────────────

    public int    getId()   { return id; }
    public String getName() { return name; }

    // ── Formatted Time ────────────────────────────────────────────────────────

    public String getFormattedTime() {
        long ms      = Math.max(0, getTimeRemainingMs());
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