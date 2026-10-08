package engine;

import shared.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The single authoritative owner of Strata state and collection resolution.
 *
 * GENERATION (balanced randomness):
 *   - Shared square: random from the 4 center squares (d4, e4, d5, e5).
 *   - White-private: random on row 4 (White's 4th row), files c-f.
 *   - Black-private: the MIRROR of White's square on row 3 (Black's 4th row),
 *     either same file or the file-flipped file (c<->f, d<->e). Both are
 *     equivalent, and the coin flip stops Black from deducing White's exact
 *     square from its own.
 *   All three are distinct, central, and outside the home rows.
 *   Note: an 8-row board has no square equidistant from both sides, so the
 *   shared pool is symmetric instead, and neither color is systematically favored.
 */
public class StrataManager {

    private static final int   WHITE_ROW    = 4;
    private static final int   BLACK_ROW    = 3;
    private static final int[] CENTRAL_COLS = {2, 3, 4, 5};

    private final List<StrataSquare> squares = new ArrayList<>();

    /** Result of one successful collection. */
    public record StrataCollection(Position position, int visibleTo,
                                   int coins, int chargesRemaining, boolean exhausted) {}

    // ── Generation ────────────────────────────────────────────────────────────

    public void generate(Random rng) {
        squares.clear();

        Position[] sharedPool = {
            new Position(BLACK_ROW, 3), new Position(BLACK_ROW, 4),
            new Position(WHITE_ROW, 3), new Position(WHITE_ROW, 4)
        };
        Position shared = sharedPool[rng.nextInt(sharedPool.length)];

        List<Position> whiteCandidates = new ArrayList<>();
        for (int c : CENTRAL_COLS) {
            Position p = new Position(WHITE_ROW, c);
            if (!p.equals(shared)) whiteCandidates.add(p);
        }
        Position white = whiteCandidates.get(rng.nextInt(whiteCandidates.size()));

        List<Position> blackCandidates = new ArrayList<>();
        Position sameFile    = new Position(BLACK_ROW, white.col);
        Position flippedFile = new Position(BLACK_ROW, 7 - white.col);
        if (!sameFile.equals(shared))    blackCandidates.add(sameFile);
        if (!flippedFile.equals(shared)) blackCandidates.add(flippedFile);
        Position black = blackCandidates.get(rng.nextInt(blackCandidates.size()));

        squares.add(new StrataSquare(white,  0));
        squares.add(new StrataSquare(black,  1));
        squares.add(new StrataSquare(shared, StrataSquare.SHARED));
    }

    /** Replaces all squares (intended for tests and state restoration). */
    public void setSquares(List<StrataSquare> newSquares) {
        squares.clear();
        squares.addAll(newSquares);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** All active squares (engine-side view; the UI should use getActiveVisibleTo). */
    public List<StrataSquare> getActiveSquares() {
        List<StrataSquare> result = new ArrayList<>();
        for (StrataSquare s : squares) if (s.isActive()) result.add(s);
        return result;
    }

    /** Active squares this player is allowed to see. */
    public List<StrataSquare> getActiveVisibleTo(int playerId) {
        List<StrataSquare> result = new ArrayList<>();
        for (StrataSquare s : squares)
            if (s.isActive() && s.isVisibleTo(playerId)) result.add(s);
        return result;
    }

    public StrataSquare getActiveAt(Position pos) {
        for (StrataSquare s : squares)
            if (s.isActive() && s.getPosition().equals(pos)) return s;
        return null;
    }

    // ── Collection ────────────────────────────────────────────────────────────

    /**
     * Resolves a piece ENTERING a square. Returns null if there is no active
     * square there or the piece is ineligible (no charge consumed). Otherwise
     * consumes exactly one charge, removes the square if exhausted, and
     * returns what was earned. The caller credits the coins.
     */
    public StrataCollection resolveEntry(Position destination, Piece piece) {
        StrataSquare square = getActiveAt(destination);
        if (square == null) return null;

        int reward = Economy.strataRewardFor(piece);
        if (reward <= 0) return null;

        int remaining = square.consumeCharge();
        boolean exhausted = remaining == 0;
        if (exhausted) squares.remove(square);

        return new StrataCollection(square.getPosition(), square.getVisibleTo(),
                                    reward, remaining, exhausted);
    }
}