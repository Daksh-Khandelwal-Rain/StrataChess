package engine;

import engine.pieces.Bishop;
import engine.pieces.Knight;
import engine.pieces.Pawn;
import engine.pieces.Queen;
import engine.pieces.Rook;
import org.junit.jupiter.api.Test;
import shared.Position;

import static org.junit.jupiter.api.Assertions.*;

class EconomyRegressionTest {

    // ============================================================
    // PIECE VALUES
    // ============================================================

    @Test
    void pieceValuesAreCorrect() {
        assertEquals(
                1,
                Economy.getValueOf(Piece.Type.PAWN)
        );

        assertEquals(
                3,
                Economy.getValueOf(Piece.Type.KNIGHT)
        );

        assertEquals(
                3,
                Economy.getValueOf(Piece.Type.BISHOP)
        );

        assertEquals(
                5,
                Economy.getValueOf(Piece.Type.ROOK)
        );

        assertEquals(
                9,
                Economy.getValueOf(Piece.Type.QUEEN)
        );

        assertEquals(
                0,
                Economy.getValueOf(Piece.Type.KING)
        );
    }

    // ============================================================
    // TRIBUTE
    // ============================================================

    @Test
    void pawnTributeIsOne() {
        assertEquals(
                1,
                Economy.tributeFor(Piece.Type.PAWN)
        );
    }

    @Test
    void knightTributeIsTwo() {
        assertEquals(
                2,
                Economy.tributeFor(Piece.Type.KNIGHT)
        );
    }

    @Test
    void bishopTributeIsTwo() {
        assertEquals(
                2,
                Economy.tributeFor(Piece.Type.BISHOP)
        );
    }

    @Test
    void rookTributeIsThree() {
        assertEquals(
                3,
                Economy.tributeFor(Piece.Type.ROOK)
        );
    }

    @Test
    void queenTributeIsFive() {
        assertEquals(
                5,
                Economy.tributeFor(Piece.Type.QUEEN)
        );
    }

    @Test
    void kingTributeIsZero() {
        assertEquals(
                0,
                Economy.tributeFor(Piece.Type.KING)
        );
    }

    // ============================================================
    // TRIBUTE AWARD
    // ============================================================

    @Test
    void awardingTributeAddsCoinsToPlayer() {
        Player player = new Player(
                0,
                "White"
        );

        Pawn pawn = new Pawn(
                1,
                new Position(3, 3)
        );

        assertEquals(
                0,
                player.getCoins()
        );

        int awarded =
                Economy.awardTribute(
                        pawn,
                        player
                );

        assertEquals(
                1,
                awarded
        );

        assertEquals(
                1,
                player.getCoins()
        );
    }

    @Test
    void queenCaptureAwardsFiveTribute() {
        Player player = new Player(
                0,
                "White"
        );

        Queen queen = new Queen(
                1,
                new Position(3, 3)
        );

        int awarded =
                Economy.awardTribute(
                        queen,
                        player
                );

        assertEquals(
                5,
                awarded
        );

        assertEquals(
                5,
                player.getCoins()
        );
    }

    // ============================================================
    // TRAP COST
    // ============================================================

    @Test
    void trapCostIsThree() {
        assertEquals(
                3,
                Economy.TRAP_COST
        );
    }

    @Test
    void trapCanBePurchasedWithEnoughCoins() {
        Player player = new Player(
                0,
                "White"
        );

        player.addCoins(5);

        assertTrue(
                Economy.chargeTrapCost(player)
        );

        assertEquals(
                2,
                player.getCoins()
        );
    }

    @Test
    void trapCannotBePurchasedWithoutEnoughCoins() {
        Player player = new Player(
                0,
                "White"
        );

        player.addCoins(2);

        assertFalse(
                Economy.chargeTrapCost(player)
        );

        assertEquals(
                2,
                player.getCoins()
        );
    }

    // ============================================================
    // CROWN TRANSFER COST
    // ============================================================

    @Test
    void crownTransferCostIsFive() {
        assertEquals(
                5,
                Economy.CROWN_TRANSFER_COST
        );
    }

    @Test
    void crownTransferCanBePurchasedWithEnoughCoins() {
        Player player = new Player(
                0,
                "White"
        );

        player.addCoins(7);

        assertTrue(
                Economy.chargeCrownTransferCost(player)
        );

        assertEquals(
                2,
                player.getCoins()
        );
    }

    @Test
    void crownTransferCannotBePurchasedWithoutEnoughCoins() {
        Player player = new Player(
                0,
                "White"
        );

        player.addCoins(4);

        assertFalse(
                Economy.chargeCrownTransferCost(player)
        );

        assertEquals(
                4,
                player.getCoins()
        );
    }

    // ============================================================
    // STRATA REWARDS
    // ============================================================

    @Test
    void knightGetsOneStrataCoin() {
        Knight knight = new Knight(
                0,
                new Position(4, 4)
        );

        assertEquals(
                1,
                Economy.strataRewardFor(knight)
        );
    }

    @Test
    void bishopGetsOneStrataCoin() {
        Bishop bishop = new Bishop(
                0,
                new Position(4, 4)
        );

        assertEquals(
                1,
                Economy.strataRewardFor(bishop)
        );
    }

    @Test
    void rookGetsOneStrataCoin() {
        Rook rook = new Rook(
                0,
                new Position(4, 4)
        );

        assertEquals(
                1,
                Economy.strataRewardFor(rook)
        );
    }

    @Test
    void queenGetsOneStrataCoin() {
        Queen queen = new Queen(
                0,
                new Position(4, 4)
        );

        assertEquals(
                1,
                Economy.strataRewardFor(queen)
        );
    }

    @Test
    void pawnGetsNoStrataReward() {
        Pawn pawn = new Pawn(
                0,
                new Position(4, 4)
        );

        assertEquals(
                0,
                Economy.strataRewardFor(pawn)
        );
    }

        @Test
        void kingGetsNoNormalStrataRewardWhenNotCrownHolder() {
        engine.pieces.King king =
                new engine.pieces.King(
                        0,
                        new Position(4, 4)
                );

        king.setCrownHolder(false);

        assertEquals(
                0,
                Economy.strataRewardFor(king)
        );
        }

    @Test
    void crownHolderGetsTwoStrataCoins() {
        Queen queen = new Queen(
                0,
                new Position(4, 4)
        );

        queen.setCrownHolder(true);

        assertEquals(
                2,
                Economy.strataRewardFor(queen)
        );
    }
}