package engine;

import engine.pieces.King;
import engine.pieces.Knight;
import engine.pieces.Pawn;
import engine.pieces.Queen;
import org.junit.jupiter.api.Test;
import shared.Action;
import shared.Position;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulesEngineRegressionTest {

    // ============================================================
    // BASIC MOVE VALIDATION
    // ============================================================

    @Test
    void legalPawnMoveIsAccepted() {
        Board board = new Board();
        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        Action action = Action.move(
                0,
                new Position(6, 4),
                new Position(5, 4)
        );

        assertTrue(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }

    @Test
    void pawnCannotMoveThreeSquares() {
        Board board = new Board();
        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        Action action = Action.move(
                0,
                new Position(6, 4),
                new Position(3, 4)
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }

    @Test
    void playerCannotMoveOpponentPiece() {
        Board board = new Board();
        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        Action action = Action.move(
                0,
                new Position(1, 0),
                new Position(2, 0)
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }

    @Test
    void moveFromEmptySquareIsIllegal() {
        Board board = new Board();
        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        Action action = Action.move(
                0,
                new Position(4, 4),
                new Position(3, 4)
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }

    // ============================================================
    // KNIGHT MOVEMENT
    // ============================================================

    @Test
    void knightHasLShapedMovement() {
        Board board = new Board();

        Knight knight = new Knight(
                0,
                new Position(4, 4)
        );

        board.placePiece(knight);

        List<Position> moves = knight.getValidMoves(board);

        assertTrue(moves.contains(new Position(2, 3)));
        assertTrue(moves.contains(new Position(2, 5)));
        assertTrue(moves.contains(new Position(3, 2)));
        assertTrue(moves.contains(new Position(3, 6)));
        assertTrue(moves.contains(new Position(5, 2)));
        assertTrue(moves.contains(new Position(5, 6)));
        assertTrue(moves.contains(new Position(6, 3)));
        assertTrue(moves.contains(new Position(6, 5)));
    }

    // ============================================================
    // CROWN HOLDER MOVEMENT
    // ============================================================

    @Test
    void crownHolderMovesLikeKing() {
        Board board = new Board();

        Knight knight = new Knight(
                0,
                new Position(4, 4)
        );

        board.placePiece(knight);

        knight.setCrownHolder(true);

        List<Position> moves = knight.getValidMoves(board);

        assertEquals(8, moves.size());

        assertTrue(moves.contains(new Position(3, 3)));
        assertTrue(moves.contains(new Position(3, 4)));
        assertTrue(moves.contains(new Position(3, 5)));
        assertTrue(moves.contains(new Position(4, 3)));
        assertTrue(moves.contains(new Position(4, 5)));
        assertTrue(moves.contains(new Position(5, 3)));
        assertTrue(moves.contains(new Position(5, 4)));
        assertTrue(moves.contains(new Position(5, 5)));

        assertFalse(moves.contains(new Position(2, 3)));
        assertFalse(moves.contains(new Position(2, 5)));
    }

    // ============================================================
    // CHECK DETECTION
    // ============================================================

    @Test
    void kingInDirectRookCheckIsDetected() {
        Board board = new Board();

        King king = new King(
                0,
                new Position(7, 4)
        );

        engine.pieces.Rook rook = new engine.pieces.Rook(
                1,
                new Position(0, 4)
        );

        board.placePiece(king);
        board.placePiece(rook);

        Player white = new Player(0, "White");
        white.setCrownPosition(new Position(7, 4));

        assertTrue(
                RulesEngine.isInCheck(
                        board,
                        0,
                        white
                )
        );
    }

    @Test
    void kingNotAttackedIsNotInCheck() {
        Board board = new Board();

        King king = new King(
                0,
                new Position(7, 4)
        );

        board.placePiece(king);

        Player white = new Player(0, "White");
        white.setCrownPosition(new Position(7, 4));

        assertFalse(
                RulesEngine.isInCheck(
                        board,
                        0,
                        white
                )
        );
    }

    // ============================================================
    // STALEMATE
    // ============================================================

    @Test
    void stalemateIsDetected() {
        Board board = new Board();

        King blackKing = new King(
                1,
                new Position(0, 7)
        );

        King whiteKing = new King(
                0,
                new Position(1, 5)
        );

        Queen whiteQueen = new Queen(
                0,
                new Position(2, 6)
        );

        board.placePiece(blackKing);
        board.placePiece(whiteKing);
        board.placePiece(whiteQueen);

        Player black = new Player(1, "Black");
        black.setCrownPosition(new Position(0, 7));
        blackKing.setCrownHolder(true);

        assertFalse(
                RulesEngine.isInCheck(
                        board,
                        1,
                        black
                )
        );

        assertTrue(
                RulesEngine.isStalemate(
                        board,
                        1,
                        black
                )
        );
    }

    // ============================================================
    // TRAP TERRITORY
    // ============================================================
@Test
void whiteStartsWithTwoTrapRows() {
    Board board = new Board();
    Player white = new Player(0, "White");
    Player black = new Player(1, "Black");

    white.addCoins(Economy.TRAP_COST);

    board.setupInitialPosition();

    Action inside = Action.placeTrap(
            0,
            new Position(5, 3)
    );

    Action outside = Action.placeTrap(
            0,
            new Position(4, 3)
    );

    assertTrue(
            RulesEngine.isLegalAction(
                    inside,
                    board,
                    new Player[]{white, black}
            )
    );

    assertFalse(
            RulesEngine.isLegalAction(
                    outside,
                    board,
                    new Player[]{white, black}
            )
    );
}
@Test
void whiteTrapTerritoryExpandsAfterEightTurns() {
    Board board = new Board();
    Player white = new Player(0, "White");
    Player black = new Player(1, "Black");

    white.addCoins(Economy.TRAP_COST);

    board.setupInitialPosition();

    for (int i = 0; i < 8; i++) {
        white.recordTurnsTaken();
    }

    Action action = Action.placeTrap(
            0,
            new Position(4, 3)
    );

    assertTrue(
            RulesEngine.isLegalAction(
                    action,
                    board,
                    new Player[]{white, black}
            )
    );
}

    @Test
    void whiteCannotPlaceTrapOutsideMaximumTerritory() {
        Board board = new Board();
        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        for (int i = 0; i < 100; i++) {
            white.recordTurnsTaken();
        }

        Action action = Action.placeTrap(
                0,
                new Position(0, 3)
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }

    // ============================================================
    // TRAP PLACEMENT VALIDATION
    // ============================================================

    @Test
    void trapCannotBePlacedOnPiece() {
        Board board = new Board();
        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        Action action = Action.placeTrap(
                0,
                new Position(6, 3)
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }

    @Test
    void ownTrapCannotBePlacedOnExistingOwnTrap() {
        Board board = new Board();

        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        Position position = new Position(5, 3);

        board.addTrap(new Trap(0, position));

        Action action = Action.placeTrap(
                0,
                position
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }
@Test
void enemyTrapCanBeTargetedForCollision() {
    Board board = new Board();

    Player white = new Player(0, "White");
    Player black = new Player(1, "Black");

    white.addCoins(Economy.TRAP_COST);

    board.setupInitialPosition();

    Position position = new Position(5, 3);

    board.addTrap(new Trap(1, position));

    Action action = Action.placeTrap(
            0,
            position
    );

    assertTrue(
            RulesEngine.isLegalAction(
                    action,
                    board,
                    new Player[]{white, black}
            )
    );
}
    // ============================================================
    // TRAP LIMIT
    // ============================================================

    @Test
    void playerCannotPlaceMoreThanThreeTraps() {
        Board board = new Board();

        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        white.recordTrapPlaced();
        white.recordTrapPlaced();
        white.recordTrapPlaced();

        Action action = Action.placeTrap(
                0,
                new Position(5, 3)
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }
    // ============================================================
    // CROWN TRANSFER
    // ============================================================

    @Test
    void crownTransferRequiresFiveCoins() {
        Board board = new Board();

        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        Position crownPosition = new Position(7, 4); // e1
        Position targetPosition = new Position(7, 3); // d1

        white.setCrownPosition(crownPosition);

        Action action = Action.crownTransfer(
                0,
                crownPosition,
                targetPosition
        );

        // Not enough coins.
        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );

        // Now afford the transfer.
        white.addCoins(5);

        assertTrue(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }

    @Test
    void crownCannotTransferToPawn() {
        Board board = new Board();

        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        board.setupInitialPosition();

        white.addCoins(5);

        Position crownPosition = new Position(7, 4); // e1
        Position pawnPosition = new Position(6, 3);  // d2

        white.setCrownPosition(crownPosition);

        Action action = Action.crownTransfer(
                0,
                crownPosition,
                pawnPosition
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }

    @Test
    void crownCannotTransferWhileInCheck() {
        Board board = new Board();

        King king = new King(
                0,
                new Position(7, 4)
        );

        Queen blackQueen = new Queen(
                1,
                new Position(0, 4)
        );

        Knight whiteKnight = new Knight(
                0,
                new Position(7, 3)
        );

        board.placePiece(king);
        board.placePiece(blackQueen);
        board.placePiece(whiteKnight);

        Player white = new Player(0, "White");
        Player black = new Player(1, "Black");

        white.addCoins(5);
        white.setCrownPosition(new Position(7, 4));

        Action action = Action.crownTransfer(
                0,
                new Position(7, 4),
                new Position(7, 3)
        );

        assertFalse(
                RulesEngine.isLegalAction(
                        action,
                        board,
                        new Player[]{white, black}
                )
        );
    }
}
