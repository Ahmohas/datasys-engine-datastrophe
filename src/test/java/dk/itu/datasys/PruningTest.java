package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PruningTest {

    @Test
    void equalsOutsideRangeCanBePruned() {
        assertTrue(
                Pruning.canPrune(
                        ColumnType.LONG,
                        10L,
                        20L,
                        Comparison.EQUALS,
                        25L));
    }

    @Test
    void equalsInsideRangeCannotBePruned() {
        assertFalse(
                Pruning.canPrune(
                        ColumnType.LONG,
                        10L,
                        20L,
                        Comparison.EQUALS,
                        15L));
    }

    @Test
    void lessThanCanBePrunedWhenMinimumIsTooLarge() {
        assertTrue(
                Pruning.canPrune(
                        ColumnType.LONG,
                        10L,
                        20L,
                        Comparison.LESS_THAN,
                        10L));
    }

    @Test
    void lessThanCannotBePrunedWhenRangeMayMatch() {
        assertFalse(
                Pruning.canPrune(
                        ColumnType.LONG,
                        10L,
                        20L,
                        Comparison.LESS_THAN,
                        15L));
    }

    @Test
    void greaterThanCanBePrunedWhenMaximumIsTooSmall() {
        assertTrue(
                Pruning.canPrune(
                        ColumnType.LONG,
                        10L,
                        20L,
                        Comparison.GREATER_THAN,
                        20L));
    }

    @Test
    void greaterThanCannotBePrunedWhenRangeMayMatch() {
        assertFalse(
                Pruning.canPrune(
                        ColumnType.LONG,
                        10L,
                        20L,
                        Comparison.GREATER_THAN,
                        15L));
    }
}