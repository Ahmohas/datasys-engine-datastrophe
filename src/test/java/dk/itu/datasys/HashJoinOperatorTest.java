package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class HashJoinOperatorTest {

    private static final List<ColumnSpec> BUILD = List.of(
            new ColumnSpec("b_key", ColumnType.STRING),
            new ColumnSpec("b_value", ColumnType.LONG));
    private static final List<ColumnSpec> PROBE = List.of(
            new ColumnSpec("p_value", ColumnType.LONG),
            new ColumnSpec("p_key", ColumnType.STRING));

    @Test
    void duplicatesOnBothSidesMultiply() {
        // 2 build rows and 3 probe rows with key x: 2 x 3 = 6 output rows
        HashJoinOperator join = join(
                rows(row("x", 1L), row("x", 2L)),
                rows(row(10L, "x"), row(20L, "x"), row(30L, "x")));

        List<Object[]> out = FilterOperatorTest.drain(join);

        assertEquals(6, out.size());
        assertEquals(
                List.of("1/10", "1/20", "1/30", "2/10", "2/20", "2/30"),
                out.stream().map(r -> r[1] + "/" + r[2]).sorted().toList());
    }

    @Test
    void outputIsBuildColumnsFollowedByProbeColumns() {
        HashJoinOperator join = join(rows(row("x", 1L)), rows(row(10L, "x")));

        List<Object[]> out = FilterOperatorTest.drain(join);

        assertEquals(1, out.size());
        assertArrayEquals(new Object[] {"x", 1L, 10L, "x"}, out.get(0));
        assertEquals(
                List.of("b_key", "b_value", "p_value", "p_key"),
                join.schema().stream().map(ColumnSpec::name).toList());
    }

    @Test
    void unmatchedRowsOnEitherSideAreDropped() {
        HashJoinOperator join = join(
                rows(row("x", 1L), row("only-build", 2L)),
                rows(row(10L, "x"), row(20L, "only-probe"), row(30L, "x")));

        List<Object[]> out = FilterOperatorTest.drain(join);

        assertEquals(List.of("1/10", "1/30"), out.stream().map(r -> r[1] + "/" + r[2]).toList());
    }

    @Test
    void emptyBuildSideJoinsNothingButStillDrainsTheProbe() {
        TestListOperator probe = new TestListOperator(PROBE, rows(row(10L, "x"), row(20L, "y")));
        TestListOperator build = new TestListOperator(BUILD, rows());

        List<Object[]> out = FilterOperatorTest.drain(new HashJoinOperator(build, probe, 0, 1));

        assertEquals(List.of(), out);
        assertTrue(build.opened && build.closed);
        assertTrue(probe.opened && probe.closed);
    }

    @Test
    void emptyProbeSideJoinsNothing() {
        HashJoinOperator join = join(rows(row("x", 1L), row("y", 2L)), rows());

        assertEquals(List.of(), FilterOperatorTest.drain(join));
    }

    @Test
    void cursorSurvivesBetweenCallsAndAcrossProbeRows() {
        // Probe rows with 0, 2, 0 and 1 partners: next() must resume mid-list and skip misses
        HashJoinOperator join = join(
                rows(row("a", 1L), row("a", 2L), row("b", 3L)),
                rows(row(10L, "z"), row(20L, "a"), row(30L, "z"), row(40L, "b")));

        join.open();
        List<String> out = new ArrayList<>();
        Object[] r;
        while ((r = join.next()) != null) {
            out.add(r[1] + "/" + r[2]);
        }
        assertNull(join.next());
        join.close();

        assertEquals(List.of("1/20", "2/20", "3/40"), out);
    }

    @Test
    void keysCompareByValueNotByIdentity() {
        // Two distinct Long objects with the same value must match
        HashJoinOperator join = new HashJoinOperator(
                new TestListOperator(PROBE, rows(row(Long.valueOf(1000L), "x"))),
                new TestListOperator(BUILD, rows(row("y", Long.valueOf(1000L)))),
                0, 1);

        assertEquals(1, FilterOperatorTest.drain(join).size());
    }

    @Test
    void reopeningRunsTheJoinAgain() {
        HashJoinOperator join = join(rows(row("x", 1L)), rows(row(10L, "x")));

        assertEquals(1, FilterOperatorTest.drain(join).size());
        assertEquals(1, FilterOperatorTest.drain(join).size());
    }

    private static HashJoinOperator join(List<Object[]> buildRows, List<Object[]> probeRows) {
        // build key: b_key (0); probe key: p_key (1)
        return new HashJoinOperator(
                new TestListOperator(BUILD, buildRows),
                new TestListOperator(PROBE, probeRows),
                0,
                1);
    }

    private static Object[] row(Object... values) {
        return values;
    }

    @SafeVarargs
    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }
}
