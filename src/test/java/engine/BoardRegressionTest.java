package engine;

import engine.pieces.King;
import engine.pieces.Pawn;
import engine.pieces.Queen;
import org.junit.jupiter.api.Test;
import shared.Position;

import static org.junit.jupiter.api.Assertions.*;

class BoardRegressionTest {

    @Test
    void initialBoardContainsExpectedPieces() {
        Board board = new Board();

        board.setupInitialPosition();

        assertNotNull(board.getPieceAt(new Position(7, 4)));
        assertNotNull(board.getPieceAt(new Position(0, 4)));

        assertEquals(
                0,
                board.getPieceAt(new Position(7, 4)).getOwnerId()
        );

        assertEquals(
                1,
                board.getPieceAt(new Position(0, 4)).getOwnerId()
        );
    }

    @Test
    void applyMoveMovesPieceToDestination() {
        Board board = new Board();

        board.setupInitialPosition();

        Position from = new Position(6, 4);
        Position to = new Position(5, 4);

        Piece pawn = board.getPieceAt(from);

        assertNotNull(pawn);

        board.applyMove(from, to);

        assertNull(board.getPieceAt(from));
        assertSame(pawn, board.getPieceAt(to));
        assertEquals(to, pawn.getPosition());
    }

    @Test
    void applyMoveReturnsCapturedPiece() {
        Board board = new Board();

        Pawn whitePawn = new Pawn(
                0,
                new Position(4, 4)
        );

        Pawn blackPawn = new Pawn(
                1,
                new Position(3, 3)
        );

        board.placePiece(whitePawn);
        board.placePiece(blackPawn);

        Piece captured = board.applyMove(
                new Position(4, 4),
                new Position(3, 3)
        );

        assertSame(blackPawn, captured);
        assertSame(whitePawn, board.getPieceAt(new Position(3, 3)));
        assertNull(board.getPieceAt(new Position(4, 4)));
    }

    @Test
    void enemyTrapIsConsumedWhenPieceEntersIt() {
        Board board = new Board();

        Pawn pawn = new Pawn(
                0,
                new Position(5, 3)
        );

        Position destination = new Position(4, 3);

        board.placePiece(pawn);
        board.addTrap(new Trap(1, destination));

        board.applyMove(
                pawn.getPosition(),
                destination
        );

        assertNull(board.getTrapAt(destination));
    }

    @Test
    void normalPieceDiesOnEnemyTrap() {
        Board board = new Board();

        Pawn pawn = new Pawn(
                0,
                new Position(5, 3)
        );

        Position destination = new Position(4, 3);

        board.placePiece(pawn);
        board.addTrap(new Trap(1, destination));

        board.applyMove(
                pawn.getPosition(),
                destination
        );

        assertNull(board.getPieceAt(destination));
        assertNull(board.getTrapAt(destination));
    }

    @Test
    void kingSurvivesEnemyTrap() {
        Board board = new Board();

        King king = new King(
                0,
                new Position(5, 3)
        );

        Position destination = new Position(4, 3);

        board.placePiece(king);
        board.addTrap(new Trap(1, destination));

        board.applyMove(
                king.getPosition(),
                destination
        );

        assertSame(
                king,
                board.getPieceAt(destination)
        );

        assertNull(board.getTrapAt(destination));
    }

    @Test
    void crownHolderSurvivesEnemyTrap() {
        Board board = new Board();

        Queen queen = new Queen(
                0,
                new Position(5, 3)
        );

        queen.setCrownHolder(true);

        Position destination = new Position(4, 3);

        board.placePiece(queen);
        board.addTrap(new Trap(1, destination));

        board.applyMove(
                queen.getPosition(),
                destination
        );

        assertSame(
                queen,
                board.getPieceAt(destination)
        );

        assertTrue(
                board.getPieceAt(destination).isCrownHolder()
        );

        assertNull(board.getTrapAt(destination));
    }

    @Test
    void ownTrapDoesNotDestroyPiece() {
        Board board = new Board();

        Pawn pawn = new Pawn(
                0,
                new Position(5, 3)
        );

        Position destination = new Position(4, 3);

        board.placePiece(pawn);
        board.addTrap(new Trap(0, destination));

        board.applyMove(
                pawn.getPosition(),
                destination
        );

        assertSame(
                pawn,
                board.getPieceAt(destination)
        );

        assertNotNull(
                board.getTrapAt(destination)
        );
    }

    @Test
    void addingTrapMakesItRetrievable() {
        Board board = new Board();

        Position position = new Position(5, 3);

        Trap trap = new Trap(0, position);

        board.addTrap(trap);

        assertSame(
                trap,
                board.getTrapAt(position)
        );
    }

    @Test
    void removingTrapRemovesItFromBoard() {
        Board board = new Board();

        Position position = new Position(5, 3);

        Trap trap = new Trap(0, position);

        board.addTrap(trap);
        board.removeTrap(trap);

        assertNull(
                board.getTrapAt(position)
        );
    }

    @Test
    void promotionCreatesQueen() {
        Board board = new Board();

        Pawn pawn = new Pawn(
                0,
                new Position(1, 0)
        );

        board.placePiece(pawn);

        board.applyMove(
                new Position(1, 0),
                new Position(0, 0)
        );

        Piece promoted = board.getPieceAt(
                new Position(0, 0)
        );

        assertNotNull(promoted);
        assertEquals(
                Piece.Type.QUEEN,
                promoted.getType()
        );

        assertEquals(
                0,
                promoted.getOwnerId()
        );
    }

    @Test
    void crownHolderStatusSurvivesPromotion() {
        Board board = new Board();

        Pawn pawn = new Pawn(
                0,
                new Position(1, 0)
        );

        pawn.setCrownHolder(true);

        board.placePiece(pawn);

        board.applyMove(
                new Position(1, 0),
                new Position(0, 0)
        );

        Piece promoted = board.getPieceAt(
                new Position(0, 0)
        );

        assertNotNull(promoted);
        assertEquals(
                Piece.Type.QUEEN,
                promoted.getType()
        );

        assertTrue(
                promoted.isCrownHolder()
        );
    }

    @Test
    void boardCopyDoesNotSharePieceInstances() {
        Board original = new Board();

        original.setupInitialPosition();

        Board copy = new Board(original);

        Piece originalPiece =
                original.getPieceAt(new Position(6, 4));

        Piece copiedPiece =
                copy.getPieceAt(new Position(6, 4));

        assertNotNull(originalPiece);
        assertNotNull(copiedPiece);

        assertNotSame(
                originalPiece,
                copiedPiece
        );
    }
}