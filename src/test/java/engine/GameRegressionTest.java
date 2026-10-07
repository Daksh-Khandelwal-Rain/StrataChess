package engine;
import org.junit.jupiter.api.Test;

import engine.pieces.King;
import engine.pieces.Queen;
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
}
