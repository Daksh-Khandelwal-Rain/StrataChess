package engine;
import org.junit.jupiter.api.Test;

import engine.pieces.Bishop;
import engine.pieces.King;
import engine.pieces.Knight;
import engine.pieces.Pawn;
import engine.pieces.Queen;
import engine.pieces.Rook;
import shared.Action;
import shared.Position;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameRegressionTest {
    
    @Test 
    void checkmateCannotContinueGameWhenCrownTransferIsAvailable(){
        Game game = new Game("White", "Black");
        game.start();

        // Give Black enough coins to trigger the broken
        // crown-transfer checkmate warning.
        game.getPlayer(1).addCoins(5);

         // 1. f3
        assertTrue(game.processAction(
                Action.move(
                        0,
                        new Position(6, 5),
                        new Position(5, 5)
                )
        ));

        // 1... e5
        assertTrue(game.processAction(
                Action.move(
                        1,
                        new Position(1, 4),
                        new Position(3, 4)
                )
        ));

        // 2. g4
        assertTrue(game.processAction(
                Action.move(
                        0,
                        new Position(6, 6),
                        new Position(4, 6)
                )
        ));

        // 2... Qh4#
        assertTrue(game.processAction(
                Action.move(
                        1,
                        new Position(0, 3),
                        new Position(4, 7)
                )
        ));

        // Black is checkmated.
        // The game MUST end immediately.
        assertEquals(Game.State.GAME_OVER, game.getState());

        // White delivered the checkmate.
        assertEquals(1, game.getWinnerId());
    }

    
    @Test
    void crownHolderSurvivesEnemyTrap() {
        Game game = new Game("White", "Black");
        game.start();

        Board board = game.getBoard();
        Player white = game.getPlayer(0);

        // Make White's queen the crown holder.
        Piece king = board.getPieceAt(new Position(7, 4));   // e1
        Piece queen = board.getPieceAt(new Position(7, 3));  // d1

        king.setCrownHolder(false);
        queen.setCrownHolder(true);
        white.setCrownPosition(new Position(7, 3));

        // Move the pawn from e2 so the crown holder can later move to e2.
        assertTrue(game.processAction(
            Action.move(
                0,
                new Position(6, 4),  // e2
                new Position(5, 4)   // e3
            )
        ));

        // Black makes a harmless move.
        assertTrue(game.processAction(
            Action.move(
                1,
                new Position(1, 0),  // a7
                new Position(2, 0)   // a6
            )
        ));

        // Black owns a trap on e2.
        Position trapPosition = new Position(6, 4);
        board.addTrap(new Trap(1, trapPosition));

        // Crown-holder Queen moves d1 -> e2.
        // As a crown holder, this is a King-style one-square move.
        assertTrue(game.processAction(
            Action.move(
                0,
                new Position(7, 3),
                trapPosition
            )
        ));

        // Crown holder must survive the enemy trap.
        Piece movedPiece = board.getPieceAt(trapPosition);

        assertNotNull(movedPiece);
        assertTrue(movedPiece.isCrownHolder());

        // Enemy trap should be consumed.
        assertNull(board.getTrapAt(trapPosition));

        // Crown position follows the new King.
        assertEquals(trapPosition, white.getCrownPosition());

        // Game should still be running.
        assertEquals(Game.State.PLAYING, game.getState());
    }
    @Test
    void ownTrapDoesNotDestroyOwnPiece() {
        Game game = new Game("White", "Black");
        game.start();

        Board board = game.getBoard();

        // White's pawn starts at d2.
        Position pawnStart = new Position(6, 3);

        // Place White's own trap at d3.
        Position trapPosition = new Position(5, 3);
        board.addTrap(new Trap(0, trapPosition));

        // White pawn moves d2 -> d3 onto its own trap.
        assertTrue(game.processAction(
            Action.move(
                0,
                pawnStart,
                trapPosition
            )
        ));

        // The pawn must survive.
        Piece pawn = board.getPieceAt(trapPosition);

        assertNotNull(pawn);
        assertEquals(0, pawn.getOwnerId());

        // The own trap must NOT be consumed.
        assertNotNull(board.getTrapAt(trapPosition));

        // Game should continue normally.
        assertEquals(Game.State.PLAYING, game.getState());
    }

    @Test
    void stalemateIsDetectedCorrectly() {
        Board board = new Board();

        // Black king on h8.
        Piece blackKing = new King(1, new Position(0, 7));
        board.placePiece(blackKing);

        // White king on f7.
        Piece whiteKing = new King(0, new Position(1, 5));
        board.placePiece(whiteKing);

        // White queen on g6.
        Piece whiteQueen = new Queen(0, new Position(2, 6));
        board.placePiece(whiteQueen);

        Player black = new Player(1, "Black");
        black.setCrownPosition(new Position(0, 7));
        blackKing.setCrownHolder(true);

        // Black is NOT in check.
        assertFalse(
            RulesEngine.isInCheck(
                board,
                1,
                black
            )
        );

        // But Black has no legal moves.
        assertTrue(
            RulesEngine.isStalemate(
                board,
                1,
                black
            )
        );
    } 

    @Test
    void crownHolderUsesKingMovementInsteadOfOriginalPieceMovement() {
        Board board = new Board();

        Piece knight = new engine.pieces.Knight(
                0,
                new Position(4, 4)
        );

        board.placePiece(knight);

        // Before crown transfer: normal Knight movement.
        List<Position> knightMoves = knight.getValidMoves(board);

        assertTrue(knightMoves.contains(new Position(2, 3)));
        assertTrue(knightMoves.contains(new Position(2, 5)));

        // Knight becomes the Crown Holder.
        knight.setCrownHolder(true);

        List<Position> crownMoves = knight.getValidMoves(board);

        // Crown Holder must now move like a King.
        assertTrue(crownMoves.contains(new Position(3, 3)));
        assertTrue(crownMoves.contains(new Position(3, 4)));
        assertTrue(crownMoves.contains(new Position(3, 5)));
        assertTrue(crownMoves.contains(new Position(4, 3)));
        assertTrue(crownMoves.contains(new Position(4, 5)));
        assertTrue(crownMoves.contains(new Position(5, 3)));
        assertTrue(crownMoves.contains(new Position(5, 4)));
        assertTrue(crownMoves.contains(new Position(5, 5)));

        // Knight movement must disappear.
        assertFalse(crownMoves.contains(new Position(2, 3)));
        assertFalse(crownMoves.contains(new Position(2, 5)));
        assertFalse(crownMoves.contains(new Position(6, 3)));
        assertFalse(crownMoves.contains(new Position(6, 5)));
    }

        // ============================================================
    // ECONOMY — TRIBUTE
    // ============================================================

    
    @Test
    void capturedKnightAwardsTwoTributeToLosingPlayer() {
        Board board = new Board();

        Piece whiteKnight = new Knight(
                0,
                new Position(4, 4)
        );

        Piece blackKnight = new Knight(
                1,
                new Position(3, 3)
        );

        board.placePiece(whiteKnight);
        board.placePiece(blackKnight);

        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        Player[] players = {white, black};

        /*
         * Directly test the authoritative economy calculation.
         * Knight value = 3.
         * Tribute = ceil(3 / 2) = 2.
         */
        int tribute = Economy.awardTribute(
                blackKnight,
                black
        );

        assertEquals(2, tribute);
        assertEquals(2, black.getCoins());
        assertEquals(0, white.getCoins());
    }


    @Test
    void capturedBishopAwardsTwoTributeToLosingPlayer() {
        Player black = new Player(1, "Black");

        Piece bishop = new Bishop(
                1,
                new Position(3, 3)
        );

        int tribute = Economy.awardTribute(
                bishop,
                black
        );

        assertEquals(2, tribute);
        assertEquals(2, black.getCoins());
    }


    @Test
    void capturedRookAwardsThreeTributeToLosingPlayer() {
        Player black = new Player(1, "Black");

        Piece rook = new Rook(
                1,
                new Position(3, 3)
        );

        int tribute = Economy.awardTribute(
                rook,
                black
        );

        assertEquals(3, tribute);
        assertEquals(3, black.getCoins());
    }


    @Test
    void capturedQueenAwardsFiveTributeToLosingPlayer() {
        Player black = new Player(1, "Black");

        Piece queen = new Queen(
                1,
                new Position(3, 3)
        );

        int tribute = Economy.awardTribute(
                queen,
                black
        );

        assertEquals(5, tribute);
        assertEquals(5, black.getCoins());
    }


    @Test
    void capturedKingAwardsNoTribute() {
        Player black = new Player(1, "Black");

        Piece king = new King(
                1,
                new Position(0, 4)
        );

        int tribute = Economy.awardTribute(
                king,
                black
        );

        assertEquals(0, tribute);
        assertEquals(0, black.getCoins());
    }


    // ============================================================
    // ECONOMY — STRATA
    // ============================================================

    @Test
    void strataSquareRewardsEligibleNormalPieceOneCoin() {
        StrataManager manager = new StrataManager();

        Position position = new Position(4, 3);

        manager.setSquares(
                List.of(
                        new StrataSquare(
                                position,
                                StrataSquare.SHARED
                        )
                )
        );

        Piece knight = new Knight(
                0,
                new Position(5, 2)
        );

        StrataManager.StrataCollection collection =
                manager.resolveEntry(position, knight);

        assertNotNull(collection);
        assertEquals(1, collection.coins());
        assertEquals(2, collection.chargesRemaining());
        assertFalse(collection.exhausted());
    }


    @Test
    void strataSquareRewardsCrownHolderTwoCoins() {
        StrataManager manager = new StrataManager();

        Position position = new Position(4, 3);

        manager.setSquares(
                List.of(
                        new StrataSquare(
                                position,
                                StrataSquare.SHARED
                        )
                )
        );

        Piece knight = new Knight(
                0,
                new Position(5, 2)
        );

        knight.setCrownHolder(true);

        StrataManager.StrataCollection collection =
                manager.resolveEntry(position, knight);

        assertNotNull(collection);
        assertEquals(2, collection.coins());
        assertEquals(2, collection.chargesRemaining());
        assertFalse(collection.exhausted());
    }


    @Test
    void pawnCannotCollectFromStrataSquare() {
        StrataManager manager = new StrataManager();

        Position position = new Position(4, 3);

        manager.setSquares(
                List.of(
                        new StrataSquare(
                                position,
                                StrataSquare.SHARED
                        )
                )
        );

        Piece pawn = new Pawn(
                0,
                new Position(5, 3)
        );

        StrataManager.StrataCollection collection =
                manager.resolveEntry(position, pawn);

        assertNull(collection);

        /*
         * Since the pawn is not eligible, the charge must not
         * be consumed.
         */
        assertEquals(
                3,
                manager.getActiveAt(position)
                        .getChargesRemaining()
        );
    }


    @Test
    void strataSquareHasExactlyThreeSuccessfulCollections() {
        StrataManager manager = new StrataManager();

        Position position = new Position(4, 3);

        manager.setSquares(
                List.of(
                        new StrataSquare(
                                position,
                                StrataSquare.SHARED
                        )
                )
        );

        Piece knight = new Knight(
                0,
                new Position(5, 2)
        );

        /*
         * Collection 1.
         */
        StrataManager.StrataCollection first =
                manager.resolveEntry(position, knight);

        assertNotNull(first);
        assertEquals(1, first.coins());
        assertEquals(2, first.chargesRemaining());
        assertFalse(first.exhausted());

        /*
         * Collection 2.
         */
        StrataManager.StrataCollection second =
                manager.resolveEntry(position, knight);

        assertNotNull(second);
        assertEquals(1, second.coins());
        assertEquals(1, second.chargesRemaining());
        assertFalse(second.exhausted());

        /*
         * Collection 3.
         */
        StrataManager.StrataCollection third =
                manager.resolveEntry(position, knight);

        assertNotNull(third);
        assertEquals(1, third.coins());
        assertEquals(0, third.chargesRemaining());
        assertTrue(third.exhausted());

        /*
         * The square permanently disappears.
         */
        assertNull(manager.getActiveAt(position));

        /*
         * Fourth collection is impossible.
         */
        assertNull(
                manager.resolveEntry(position, knight)
        );
    }


    @Test
    void privateStrataSquareIsVisibleOnlyToOwner() {
        StrataManager manager = new StrataManager();

        Position whitePosition = new Position(4, 3);
        Position blackPosition = new Position(3, 4);
        Position sharedPosition = new Position(4, 4);

        manager.setSquares(
                List.of(
                        new StrataSquare(whitePosition, 0),
                        new StrataSquare(blackPosition, 1),
                        new StrataSquare(
                                sharedPosition,
                                StrataSquare.SHARED
                        )
                )
        );

        List<StrataSquare> whiteVisible =
                manager.getActiveVisibleTo(0);

        List<StrataSquare> blackVisible =
                manager.getActiveVisibleTo(1);

        assertEquals(2, whiteVisible.size());
        assertEquals(2, blackVisible.size());

        assertTrue(
                whiteVisible.stream()
                        .anyMatch(s ->
                                s.getPosition().equals(whitePosition))
        );

        assertFalse(
                whiteVisible.stream()
                        .anyMatch(s ->
                                s.getPosition().equals(blackPosition))
        );

        assertTrue(
                blackVisible.stream()
                        .anyMatch(s ->
                                s.getPosition().equals(blackPosition))
        );

        assertFalse(
                blackVisible.stream()
                        .anyMatch(s ->
                                s.getPosition().equals(whitePosition))
        );

        assertTrue(
                whiteVisible.stream()
                        .anyMatch(s ->
                                s.getPosition().equals(sharedPosition))
        );

        assertTrue(
                blackVisible.stream()
                        .anyMatch(s ->
                                s.getPosition().equals(sharedPosition))
        );
    }


    // ============================================================
    // ECONOMY — GAME INTEGRATION
    // ============================================================

    @Test
    void capturedPieceAwardsTributeToOwnerNotCapturer() {
        Game game = new Game("White", "Black");
        game.start();

        Board board = game.getBoard();
        Player white = game.getPlayer(0);
        Player black = game.getPlayer(1);

        /*
         * Move White pawn e2 -> e4.
         */
        assertTrue(
                game.processAction(
                        Action.move(
                                0,
                                new Position(6, 4),
                                new Position(4, 4)
                        )
                )
        );

        /*
         * Black pawn d7 -> d5.
         */
        assertTrue(
                game.processAction(
                        Action.move(
                                1,
                                new Position(1, 3),
                                new Position(3, 3)
                        )
                )
        );

        int whiteCoinsBefore = white.getCoins();
        int blackCoinsBefore = black.getCoins();

        /*
         * White pawn e4 captures Black pawn d5.
         */
        assertTrue(
                game.processAction(
                        Action.move(
                                0,
                                new Position(4, 4),
                                new Position(3, 3)
                        )
                )
        );

        /*
         * Black lost a Pawn:
         * Pawn value = 1
         * Tribute = 1
         */
        assertEquals(
                blackCoinsBefore + 1,
                black.getCoins()
        );

        /*
         * White is the capturer.
         * White receives no capture bounty.
         */
        assertEquals(
                whiteCoinsBefore,
                white.getCoins()
        );
    }


    @Test
    void trapDestroyedPieceDoesNotGenerateTribute() {
        Game game = new Game("White", "Black");
        game.start();

        Board board = game.getBoard();
        Player white = game.getPlayer(0);
        Player black = game.getPlayer(1);

        /*
         * Move White e2 -> e3 so e2 becomes empty.
         */
        assertTrue(
                game.processAction(
                        Action.move(
                                0,
                                new Position(6, 4),
                                new Position(5, 4)
                        )
                )
        );

        /*
         * Black makes a harmless move.
         */
        assertTrue(
                game.processAction(
                        Action.move(
                                1,
                                new Position(1, 0),
                                new Position(2, 0)
                        )
                )
        );

        /*
         * Place an enemy trap directly on e2.
         */
        board.addTrap(
                new Trap(
                        1,
                        new Position(6, 4)
                )
        );

        int blackCoinsBefore = black.getCoins();

        /*
         * White Queen enters Black's trap and is destroyed.
         *
         * This is NOT a normal capture.
         * Therefore Black must receive NO Tribute.
         */
        assertTrue(
                game.processAction(
                        Action.move(
                                0,
                                new Position(7, 3),
                                new Position(6, 4)
                        )
                )
        );

        assertEquals(
                blackCoinsBefore,
                black.getCoins()
        );

        assertNull(
                board.getPieceAt(new Position(6, 4))
        );
    }
}

