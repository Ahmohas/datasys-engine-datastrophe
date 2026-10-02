package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class FilterOperatorTest {

    private static final List<ColumnSpec> SCHEMA = List.of(
            new ColumnSpec("city", ColumnType.STRING),
            new ColumnSpec("distance", ColumnType.LONG),
            new ColumnSpec("price", ColumnType.DOUBLE));

    private static final List<Object[]> ROWS = List.of(
            new Object[] {"Copenhagen", 12L, 23.5},
            new Object[] {"Aarhus", 187L, 301.0},
            new Object[] {"Odense", 95L, 120.75},
            new Object[] {"Copenhagen", 140L, 210.0});

    @Test
    void emitsOnlyMatchingRowsInOrder() {
        FilterOperator filter = filter(new Predicate("distance", Comparison.GREATER_THAN, 100L));

        List<Object[]> out = drain(filter);

        assertEquals(2, out.size());
        assertEquals("Aarhus", out.get(0)[0]);
        assertEquals("Copenhagen", out.get(1)[0]);
        assertEquals(140L, out.get(1)[1]);
    }

    @Test
    void countsRowsInAndOut() {
        FilterOperator filter = filter(new Predicate("city", Comparison.EQUALS, "Copenhagen"));

        drain(filter);

        assertEquals(4, filter.rowsIn());
        assertEquals(2, filter.rowsOut());
    }

    @Test
    void supportsEveryComparisonAndType() {
        assertEquals(1, drain(filter(new Predicate("city", Comparison.LESS_THAN, "Copenhagen"))).size());
        assertEquals(1, drain(filter(new Predicate("distance", Comparison.EQUALS, 95L))).size());
        assertEquals(1, drain(filter(new Predicate("price", Comparison.LESS_THAN, 100.0))).size());
        assertEquals(2, drain(filter(new Predicate("price", Comparison.GREATER_THAN, 200.0))).size());
    }

    @Test
    void noMatchesReturnsNothing() {
        assertTrue(drain(filter(new Predicate("distance", Comparison.GREATER_THAN, 1000L))).isEmpty());
    }

    @Test
    void opensAndClosesChild() {
        TestListOperator child = new TestListOperator(SCHEMA, ROWS);
        FilterOperator filter = new FilterOperator(child, new Predicate("distance", Comparison.EQUALS, 12L));

        drain(filter);

        assertTrue(child.opened);
        assertTrue(child.closed);
    }

    @Test
    void unknownColumnThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> filter(new Predicate("missing", Comparison.EQUALS, 1L)));
    }

    private static FilterOperator filter(Predicate predicate) {
        return new FilterOperator(new TestListOperator(SCHEMA, ROWS), predicate);
    }

    static List<Object[]> drain(Operator operator) {
        List<Object[]> rows = new ArrayList<>();
        operator.open();
        Object[] row;
        while ((row = operator.next()) != null) {
            rows.add(row);
        }
        assertNull(operator.next());
        operator.close();
        return rows;
    }
}
