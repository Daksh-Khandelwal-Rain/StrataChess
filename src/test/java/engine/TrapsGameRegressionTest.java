package engine;

import org.junit.jupiter.api.Test;
import shared.Action;
import shared.Position;

import static org.junit.jupiter.api.Assertions.*;

class TrapsGameRegressionTest {

    /*
     * ============================================================
     * TEST HELPERS
     * ============================================================
     */

    private Game createStartedGame() {
        Game game = new Game("White", "Black");
        game.start();

        /*
         * Players normally start with 0 coins.
         * Trap tests need coins because TRAP_COST is 3.
         */
        game.getPlayers()[0].addCoins(100);
        game.getPlayers()[1].addCoins(100);

        return game;
    }

    private Position pos(int row, int col) {
        return new Position(row, col);
    }

    private Action trap(int playerId, int row, int col) {
        return Action.placeTrap(playerId, pos(row, col));
    }

    private Action move(
            int playerId,
            int fromRow,
            int fromCol,
            int toRow,
            int toCol
    ) {
        return Action.move(
                playerId,
                pos(fromRow, fromCol),
                pos(toRow, toCol)
        );
    }

    /*
     * ============================================================
     * BASIC TRAP PLACEMENT
     * ============================================================
     */

    @Test
    void validTrapPlacementChargesTrapCost() {
        Game game = createStartedGame();

        Player white = game.getPlayers()[0];

        int coinsBefore = white.getCoins();

        assertTrue(
                game.processAction(trap(0, 5, 0)),
                "White should be able to place a trap on row 5"
        );

        assertEquals(
                coinsBefore - Economy.TRAP_COST,
                white.getCoins()
        );

        assertEquals(1, white.getTrapsUsed());

        assertNotNull(
                game.getBoard().getTrapAt(pos(5, 0))
        );
    }

    @Test
    void validTrapPlacementConsumesTurn() {
        Game game = createStartedGame();

        assertEquals(0, game.getPlayers()[0].getTurnsTaken());

        assertTrue(
                game.processAction(trap(0, 5, 0))
        );

        assertEquals(
                1,
                game.getPlayers()[0].getTurnsTaken()
        );

        assertEquals(
                1,
                game.getTotalTurns()
        );

        assertEquals(
                1,
                game.getCurrentPlayerId()
        );
    }

    @Test
    void invalidTrapPlacementDoesNotConsumeTurn() {
        Game game = createStartedGame();

        Player white = game.getPlayers()[0];

        int coinsBefore = white.getCoins();

        /*
         * White initially controls rows 5-6.
         * Row 4 is outside the initial territory.
         */
        assertFalse(
                game.processAction(trap(0, 4, 0))
        );

        assertEquals(0, white.getTurnsTaken());
        assertEquals(0, game.getTotalTurns());
        assertEquals(0, game.getCurrentPlayerId());

        assertEquals(
                coinsBefore,
                white.getCoins()
        );

        assertNull(
                game.getBoard().getTrapAt(pos(4, 0))
        );
    }

    /*
     * ============================================================
     * INITIAL TERRITORY
     * ============================================================
     */

    @Test
    void whiteInitiallyCanPlaceTrapInsideRowsFiveAndSix() {
        Game game = createStartedGame();

        Board board = game.getBoard();
        Player[] players = game.getPlayers();

        /*
         * Row 6 contains White's starting pawns, so we use
         * an empty square on row 5 to test valid territory.
         */
        assertTrue(
                RulesEngine.isLegalAction(
                        trap(0, 5, 0),
                        board,
                        players
                )
        );

        /*
         * Row 4 is outside White's initial territory.
         */
        assertFalse(
                RulesEngine.isLegalAction(
                        trap(0, 4, 0),
                        board,
                        players
                )
        );
    }

    @Test
    void blackInitiallyCanPlaceTrapInsideRowsOneAndTwo() {
        Game game = createStartedGame();

        Board board = game.getBoard();
        Player[] players = game.getPlayers();

        /*
         * Row 1 contains Black's starting pawns.
         * Row 2 is empty, so use row 2 to test valid territory.
         */
        assertTrue(
                RulesEngine.isLegalAction(
                        trap(1, 2, 0),
                        board,
                        players
                )
        );

        /*
         * Row 3 is outside Black's initial territory.
         */
        assertFalse(
                RulesEngine.isLegalAction(
                        trap(1, 3, 0),
                        board,
                        players
                )
        );
    }

    /*
     * ============================================================
     * TRAP LIMIT
     * ============================================================
     */

    @Test
    void playerCanDeployExactlyThreeTraps() {
        Game game = createStartedGame();

        Player white = game.getPlayers()[0];

        /*
         * White territory initially includes row 5.
         * Use three different empty squares.
         */

        assertTrue(
                game.processAction(trap(0, 5, 0))
        );

        // Black turn.
        assertTrue(
                game.processAction(
                        move(1, 1, 0, 2, 0)
                )
        );

        assertTrue(
                game.processAction(trap(0, 5, 1))
        );

        // Black turn.
        assertTrue(
                game.processAction(
                        move(1, 1, 1, 2, 1)
                )
        );

        assertTrue(
                game.processAction(trap(0, 5, 2))
        );

        assertEquals(3, white.getTrapsUsed());
    }

    @Test
    void fourthTrapDeploymentIsRejected() {
        Game game = createStartedGame();

        Player white = game.getPlayers()[0];

        // Trap 1
        assertTrue(
                game.processAction(trap(0, 5, 0))
        );

        // Black move
        assertTrue(
                game.processAction(
                        move(1, 1, 0, 2, 0)
                )
        );

        // Trap 2
        assertTrue(
                game.processAction(trap(0, 5, 1))
        );

        // Black move
        assertTrue(
                game.processAction(
                        move(1, 1, 1, 2, 1)
                )
        );

        // Trap 3
        assertTrue(
                game.processAction(trap(0, 5, 2))
        );

        // Black move
        assertTrue(
                game.processAction(
                        move(1, 1, 2, 2, 2)
                )
        );

        int coinsBefore = white.getCoins();
        int turnsBefore = white.getTurnsTaken();

        /*
         * Fourth lifetime deployment must fail.
         */
        assertFalse(
                game.processAction(trap(0, 5, 3))
        );

        assertEquals(
                3,
                white.getTrapsUsed()
        );

        assertEquals(
                coinsBefore,
                white.getCoins()
        );

        assertEquals(
                turnsBefore,
                white.getTurnsTaken()
        );

        assertNull(
                game.getBoard().getTrapAt(pos(5, 3))
        );
    }

    @Test
    void destroyingPreviousTrapsDoesNotResetLifetimeTrapLimit() {
        Game game = createStartedGame();

        Player white = game.getPlayers()[0];

        // Trap 1
        assertTrue(
                game.processAction(trap(0, 5, 0))
        );

        // Black move
        assertTrue(
                game.processAction(
                        move(1, 1, 0, 2, 0)
                )
        );

        // Trap 2
        assertTrue(
                game.processAction(trap(0, 5, 1))
        );

        // Black move
        assertTrue(
                game.processAction(
                        move(1, 1, 1, 2, 1)
                )
        );

        // Trap 3
        assertTrue(
                game.processAction(trap(0, 5, 2))
        );

        assertEquals(3, white.getTrapsUsed());

        /*
         * Destroy all currently active traps manually.
         * This must NOT reset the lifetime counter.
         */
        game.getBoard().removeTrap(
                game.getBoard().getTrapAt(pos(5, 0))
        );

        game.getBoard().removeTrap(
                game.getBoard().getTrapAt(pos(5, 1))
        );

        game.getBoard().removeTrap(
                game.getBoard().getTrapAt(pos(5, 2))
        );

        assertNull(
                game.getBoard().getTrapAt(pos(5, 0))
        );

        assertNull(
                game.getBoard().getTrapAt(pos(5, 1))
        );

        assertNull(
                game.getBoard().getTrapAt(pos(5, 2))
        );

        // Black move
        assertTrue(
                game.processAction(
                        move(1, 1, 2, 2, 2)
        ));

        /*
         * Still cannot place a fourth trap.
         */
        assertFalse(
                game.processAction(trap(0, 5, 3))
        );

        assertEquals(
                3,
                white.getTrapsUsed()
        );
    }

    /*
     * ============================================================
     * OWN TRAP
     * ============================================================
     */

    @Test
    void placingTrapOnOwnTrapIsRejected() {
        Game game = createStartedGame();

        Player white = game.getPlayers()[0];

        assertTrue(
                game.processAction(trap(0, 5, 0))
        );

        // Black move.
        assertTrue(
                game.processAction(
                        move(1, 1, 0, 2, 0)
                )
        );

        int coinsBefore = white.getCoins();
        int turnsBefore = white.getTurnsTaken();
        int trapsUsedBefore = white.getTrapsUsed();

        /*
         * Same player's trap already exists.
         */
        assertFalse(
                game.processAction(trap(0, 5, 0))
        );

        assertEquals(
                coinsBefore,
                white.getCoins()
        );

        assertEquals(
                turnsBefore,
                white.getTurnsTaken()
        );

        assertEquals(
                trapsUsedBefore,
                white.getTrapsUsed()
        );

        assertNotNull(
                game.getBoard().getTrapAt(pos(5, 0))
        );
    }

    /*
     * ============================================================
     * PIECE OCCUPANCY
     * ============================================================
     */

    @Test
    void trapCannotBePlacedOnOwnPiece() {
        Game game = createStartedGame();

        /*
         * White pawn initially occupies row 6, col 0.
         */
        assertFalse(
                game.processAction(trap(0, 6, 0))
        );

        assertEquals(
                0,
                game.getPlayers()[0].getTrapsUsed()
        );

        assertEquals(
                0,
                game.getPlayers()[0].getTurnsTaken()
        );
    }

    @Test
    void trapCannotBePlacedOnEnemyPiece() {
        Game game = createStartedGame();

        Board board = game.getBoard();

        /*
         * Move Black's pawn directly from row 1 to row 5.
         *
         * We are deliberately bypassing Game here because this test
         * is testing trap placement validation, not pawn movement.
         */
        board.applyMove(pos(1, 0), pos(2, 0));
        board.applyMove(pos(2, 0), pos(3, 0));
        board.applyMove(pos(3, 0), pos(4, 0));
        board.applyMove(pos(4, 0), pos(5, 0));

        assertNotNull(
                board.getPieceAt(pos(5, 0))
        );

        /*
         * White's territory includes row 5, but an enemy piece occupies it.
         */
        assertFalse(
                game.processAction(trap(0, 5, 0))
        );

        assertEquals(
                0,
                game.getPlayers()[0].getTrapsUsed()
        );

        assertNull(
                board.getTrapAt(pos(5, 0))
        );
    }

    /*
     * ============================================================
     * OWN PIECE ENTERING OWN TRAP
     * ============================================================
     */

    @Test
    void ownPieceEnteringOwnTrapSurvivesAndTrapRemains() {
        Game game = createStartedGame();

        /*
         * White places trap on d3 = row 5, col 3.
         */
        assertTrue(
                game.processAction(trap(0, 5, 3))
        );

        // Black move.
        assertTrue(
                game.processAction(
                        move(1, 1, 0, 2, 0)
                )
        );

        /*
         * White pawn d2 -> d3.
         */
        assertTrue(
                game.processAction(
                        move(0, 6, 3, 5, 3)
                )
        );

        Piece pawn = game.getBoard().getPieceAt(
                pos(5, 3)
        );

        assertNotNull(pawn);
        assertEquals(0, pawn.getOwnerId());

        /*
         * Own trap is NOT consumed.
         */
        assertNotNull(
                game.getBoard().getTrapAt(pos(5, 3))
        );
    }

    /*
     * ============================================================
     * ENEMY TRAP — NORMAL PIECE
     * ============================================================
     */

    @Test
    void normalPieceEnteringEnemyTrapIsDestroyed() {
        Game game = createStartedGame();

        Board board = game.getBoard();

        /*
         * Move White's e-pawn away so e2 becomes empty.
         */
        assertTrue(
                game.processAction(
                        move(0, 6, 4, 5, 4)
                )
        );

        /*
         * Black must make a legal move because turns alternate.
         */
        assertTrue(
                game.processAction(
                        move(1, 1, 0, 2, 0)
                )
        );

        /*
         * Seed an enemy trap directly.
         *
         * We are testing trap ACTIVATION here, not trap placement.
         */
        board.addTrap(
                new Trap(
                        1,
                        pos(6, 4)
                )
        );

        assertNotNull(
                board.getTrapAt(pos(6, 4))
        );

        /*
         * White Queen d1 -> e2.
         *
         * Queen is NOT King and NOT crown holder.
         */
        assertTrue(
                game.processAction(
                        move(0, 7, 3, 6, 4)
                )
        );

        /*
         * Queen must be destroyed.
         */
        assertNull(
                board.getPieceAt(pos(6, 4))
        );

        /*
         * Enemy trap is consumed.
         */
        assertNull(
                board.getTrapAt(pos(6, 4))
        );
    }

    /*
     * ============================================================
     * ENEMY TRAP — KING
     * ============================================================
     */

    @Test
    void kingSurvivesEnemyTrap() {
        Game game = createStartedGame();

        Board board = game.getBoard();

        /*
         * Move White pawn from e2 -> e3.
         * This makes e2 empty.
         */
        assertTrue(
                game.processAction(
                        move(0, 6, 4, 5, 4)
                )
        );

        // Black move.
        assertTrue(
                game.processAction(
                        move(1, 1, 0, 2, 0)
                )
        );

        /*
         * Seed enemy trap on e2.
         */
        board.addTrap(
                new Trap(
                        1,
                        pos(6, 4)
                )
        );

        /*
         * White King e1 -> e2.
         */
        assertTrue(
                game.processAction(
                        move(0, 7, 4, 6, 4)
                )
        );

        Piece king = board.getPieceAt(
                pos(6, 4)
        );

        assertNotNull(king);

        assertEquals(
                Piece.Type.KING,
                king.getType()
        );

        assertEquals(
                0,
                king.getOwnerId()
        );

        /*
         * Trap is consumed.
         */
        assertNull(
                board.getTrapAt(pos(6, 4))
        );

        /*
         * Crown position should follow the King.
         */
        assertEquals(
                pos(6, 4),
                game.getPlayers()[0].getCrownPosition()
        );
    }

    /*
     * ============================================================
     * ENEMY TRAP — CROWN HOLDER
     * ============================================================
     */

    @Test
    void crownHolderSurvivesEnemyTrap() {
        Game game = createStartedGame();

        Board board = game.getBoard();

        Player white = game.getPlayers()[0];

        /*
         * Move White pawn d2 -> d3.
         * d2 becomes empty.
         */
        assertTrue(
                game.processAction(
                        move(0, 6, 3, 5, 3)
                )
        );

        // Black move.
        assertTrue(
                game.processAction(
                        move(1, 1, 0, 2, 0)
                )
        );

        /*
         * Make White Queen the crown holder.
         */
        Piece king = board.getPieceAt(
                pos(7, 4)
        );

        Piece queen = board.getPieceAt(
                pos(7, 3)
        );

        assertNotNull(king);
        assertNotNull(queen);

        king.setCrownHolder(false);
        queen.setCrownHolder(true);

        white.setCrownPosition(
                pos(7, 3)
        );

        /*
         * Seed enemy trap on d2.
         */
        board.addTrap(
                new Trap(
                        1,
                        pos(6, 3)
                )
        );

        /*
         * Crowned Queen d1 -> d2.
         */
        assertTrue(
                game.processAction(
                        move(0, 7, 3, 6, 3)
                )
        );

        Piece movedQueen = board.getPieceAt(
                pos(6, 3)
        );

        assertNotNull(movedQueen);

        assertEquals(
                0,
                movedQueen.getOwnerId()
        );

        assertEquals(
                Piece.Type.QUEEN,
                movedQueen.getType()
        );

        assertTrue(
                movedQueen.isCrownHolder()
        );

        /*
         * Enemy trap is consumed.
         */
        assertNull(
                board.getTrapAt(pos(6, 3))
        );

        /*
         * Crown position follows the crowned piece.
         */
        assertEquals(
                pos(6, 3),
                white.getCrownPosition()
        );
    }

    /*
     * ============================================================
     * TRAP TERRITORY EXPANSION
     * ============================================================
     */

    @Test
    void whiteTerritoryExpandsAfterEightWhiteTurns() {
        Game game = createStartedGame();

        Board board = game.getBoard();
        Player[] players = game.getPlayers();

        Player white = players[0];

        /*
         * At 0 turns:
         * row 5 is valid
         * row 4 is invalid
         */
        assertTrue(
                RulesEngine.isLegalAction(
                        trap(0, 5, 0),
                        board,
                        players
                )
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        trap(0, 4, 0),
                        board,
                        players
                )
        );

        /*
         * 8 White turns completed.
         */
        for (int i = 0; i < 8; i++) {
            white.recordTurnsTaken();
        }

        /*
         * Territory expands to row 4.
         */
        assertTrue(
                RulesEngine.isLegalAction(
                        trap(0, 4, 0),
                        board,
                        players
                )
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        trap(0, 3, 0),
                        board,
                        players
                )
        );

        /*
         * 16 White turns.
         */
        for (int i = 0; i < 8; i++) {
            white.recordTurnsTaken();
        }

        assertTrue(
                RulesEngine.isLegalAction(
                        trap(0, 3, 0),
                        board,
                        players
                )
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        trap(0, 2, 0),
                        board,
                        players
                )
        );

        /*
         * 24 White turns.
         */
        for (int i = 0; i < 8; i++) {
            white.recordTurnsTaken();
        }

        assertTrue(
                RulesEngine.isLegalAction(
                        trap(0, 2, 0),
                        board,
                        players
                )
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        trap(0, 1, 0),
                        board,
                        players
                )
        );

        /*
         * 32 White turns.
         */
        for (int i = 0; i < 8; i++) {
            white.recordTurnsTaken();
        }

        /*
         * At this point the formula reaches row 1.
         *
         * We need to clear row 1 col 0 because Black's pawn
         * initially occupies it.
         */
        board.applyMove(
                pos(1, 0),
                pos(2, 0)
        );

        assertTrue(
                RulesEngine.isLegalAction(
                        trap(0, 1, 0),
                        board,
                        players
                )
        );
    }

    @Test
    void blackTerritoryExpandsAfterEightBlackTurns() {
        Game game = createStartedGame();

        Board board = game.getBoard();
        Player[] players = game.getPlayers();

        Player black = players[1];

        /*
         * At 0 turns:
         * row 2 is valid
         * row 3 is invalid
         */
        assertTrue(
                RulesEngine.isLegalAction(
                        trap(1, 2, 0),
                        board,
                        players
                )
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        trap(1, 3, 0),
                        board,
                        players
                )
        );

        /*
         * 8 Black turns.
         */
        for (int i = 0; i < 8; i++) {
            black.recordTurnsTaken();
        }

        assertTrue(
                RulesEngine.isLegalAction(
                        trap(1, 3, 0),
                        board,
                        players
                )
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        trap(1, 4, 0),
                        board,
                        players
                )
        );

        /*
         * 16 Black turns.
         */
        for (int i = 0; i < 8; i++) {
            black.recordTurnsTaken();
        }

        assertTrue(
                RulesEngine.isLegalAction(
                        trap(1, 4, 0),
                        board,
                        players
                )
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        trap(1, 5, 0),
                        board,
                        players
                )
        );

        /*
         * 24 Black turns.
         */
        for (int i = 0; i < 8; i++) {
            black.recordTurnsTaken();
        }

        assertTrue(
                RulesEngine.isLegalAction(
                        trap(1, 5, 0),
                        board,
                        players
                )
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        trap(1, 6, 0),
                        board,
                        players
                )
        );

        /*
         * 32 Black turns.
         */
        for (int i = 0; i < 8; i++) {
            black.recordTurnsTaken();
        }

        /*
         * Clear White's pawn from row 6 col 0 so we can
         * test the actual territory boundary.
         */
        board.applyMove(
                pos(6, 0),
                pos(5, 0)
        );

        assertTrue(
                RulesEngine.isLegalAction(
                        trap(1, 6, 0),
                        board,
                        players
                )
        );
    }

    /*
     * ============================================================
     * TERRITORY DEPENDS ON OWNER'S TURNS
     * ============================================================
     */

    @Test
    void territoryExpansionUsesPlayersOwnTurnsNotTotalTurns() {
        Game game = createStartedGame();

        Board board = game.getBoard();
        Player[] players = game.getPlayers();

        Player white = players[0];
        Player black = players[1];

        /*
         * Give Black 24 turns without giving White any.
         */
        for (int i = 0; i < 24; i++) {
            black.recordTurnsTaken();
        }

        /*
         * Black should have reached row 5.
         */
        assertTrue(
                RulesEngine.isLegalAction(
                        trap(1, 5, 0),
                        board,
                        players
                )
        );

        /*
         * White has still taken 0 turns.
         * Therefore White must NOT have reached row 2.
         */
        assertFalse(
                RulesEngine.isLegalAction(
                        trap(0, 2, 0),
                        board,
                        players
                )
        );

        assertEquals(0, white.getTurnsTaken());
        assertEquals(24, black.getTurnsTaken());
    }

    /*
     * ============================================================
     * ENEMY TRAP COLLISION
     * ============================================================
     */

    @Test
    void enemyTrapCollisionDestroysBothTraps() {
        Game game = createStartedGame();

        Board board = game.getBoard();

        Player white = game.getPlayers()[0];
        Player black = game.getPlayers()[1];

        /*
         * White places a trap at row 5, col 0.
         */
        assertTrue(
                game.processAction(
                        trap(0, 5, 0)
                )
        );

        assertNotNull(
                board.getTrapAt(pos(5, 0))
        );

        /*
         * Black needs 24 OWN turns before row 5 becomes
         * part of Black's territory.
         *
         * Use knights moving back and forth so we don't
         * permanently alter the board.
         *
         * After White's trap placement it is Black's turn.
         */
        for (int i = 0; i < 24; i++) {

            /*
             * Black knight:
             *
             * b8 -> c6
             * c6 -> b8
             */
            if (i % 2 == 0) {
                assertTrue(
                        game.processAction(
                                move(1, 0, 1, 2, 2)
                        )
                );
            } else {
                assertTrue(
                        game.processAction(
                                move(1, 2, 2, 0, 1)
                        )
                );
            }

            /*
             * White knight:
             *
             * b1 -> c3
             * c3 -> b1
             */
            if (i % 2 == 0) {
                assertTrue(
                        game.processAction(
                                move(0, 7, 1, 5, 2)
                        )
                );
            } else {
                assertTrue(
                        game.processAction(
                                move(0, 5, 2, 7, 1)
                        )
                );
            }
        }

        assertEquals(
                24,
                black.getTurnsTaken()
        );

        /*
         * It is now Black's turn and Black's territory
         * includes row 5.
         */
        assertEquals(
                1,
                game.getCurrentPlayerId()
        );

        /*
         * Black probes White's trap by placing its own trap
         * on exactly the same square.
         *
         * Collision rule:
         * - White trap disappears.
         * - Black trap disappears.
         * - Black still pays.
         * - Black consumes one lifetime deployment.
         */
        int blackCoinsBefore = black.getCoins();

        assertTrue(
                game.processAction(
                        trap(1, 5, 0)
                )
        );

        assertEquals(
                blackCoinsBefore - Economy.TRAP_COST,
                black.getCoins()
        );

        assertEquals(
                1,
                black.getTrapsUsed()
        );

        assertEquals(
                1,
                white.getTrapsUsed()
        );

        /*
         * Neither trap remains.
         */
        assertNull(
                board.getTrapAt(pos(5, 0))
        );
    }

    /*
     * ============================================================
     * LIFETIME LIMIT AFTER ENEMY COLLISION
     * ============================================================
     */

    @Test
    void enemyTrapCollisionStillConsumesLifetimeDeployment() {
        Game game = createStartedGame();

        Board board = game.getBoard();

        Player white = game.getPlayers()[0];
        Player black = game.getPlayers()[1];

        /*
         * White uses one lifetime deployment.
         */
        assertTrue(
                game.processAction(
                        trap(0, 5, 0)
                )
        );

        /*
         * Advance Black to row 5 territory using knight moves.
         */
        for (int i = 0; i < 24; i++) {

            if (i % 2 == 0) {
                assertTrue(
                        game.processAction(
                                move(1, 0, 1, 2, 2)
                        )
                );
            } else {
                assertTrue(
                        game.processAction(
                                move(1, 2, 2, 0, 1)
                        )
                );
            }

            if (i % 2 == 0) {
                assertTrue(
                        game.processAction(
                                move(0, 7, 1, 5, 2)
                        )
                );
            } else {
                assertTrue(
                        game.processAction(
                                move(0, 5, 2, 7, 1)
                        )
                );
            }
        }

        /*
         * Black destroys White's trap through collision.
         */
        assertTrue(
                game.processAction(
                        trap(1, 5, 0)
                )
        );

        assertNull(
                board.getTrapAt(pos(5, 0))
        );

        /*
         * The destroyed trap still counts against White's
         * lifetime limit.
         */
        assertEquals(
                1,
                white.getTrapsUsed()
        );

        assertEquals(
                1,
                black.getTrapsUsed()
        );
    }
}