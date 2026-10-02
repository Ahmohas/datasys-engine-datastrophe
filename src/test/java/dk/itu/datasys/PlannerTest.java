package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlannerTest {

    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("city", ColumnType.STRING),
            new ColumnSpec("distance", ColumnType.LONG),
            new ColumnSpec("price", ColumnType.DOUBLE));

    // sorted-trips.csv with 2 rows per partition gives distance ranges
    // p0 [12, 31], p1 [88, 95], p2 [140, 187], p3 [210, 299]

    @Test
    void greaterThanKeepsOnlyTheLastPartition(@TempDir Path tempDir) {
        Plan plan = plan(tempDir, new Predicate("distance", Comparison.GREATER_THAN, 200L));

        assertStats(plan, 1, 3);
        assertEquals(List.of("trips-partition-3.bin"), scannedFiles(plan));
    }

    @Test
    void lessThanKeepsTheFirstTwoPartitions(@TempDir Path tempDir) {
        Plan plan = plan(tempDir, new Predicate("distance", Comparison.LESS_THAN, 90L));

        assertStats(plan, 2, 2);
        assertEquals(List.of("trips-partition-0.bin", "trips-partition-1.bin"), scannedFiles(plan));
    }

    @Test
    void equalsKeepsTheOnePartitionWhoseRangeContainsTheConstant(@TempDir Path tempDir) {
        Plan plan = plan(tempDir, new Predicate("distance", Comparison.EQUALS, 150L));

        assertStats(plan, 1, 3);
        assertEquals(List.of("trips-partition-2.bin"), scannedFiles(plan));
    }

    @Test
    void equalsOnStringPrunesByRange(@TempDir Path tempDir) {
        // city ranges: p0 [Copenhagen, Roskilde], p1 [Copenhagen, Odense],
        // p2 [Aarhus, Copenhagen], p3 [Aalborg, Esbjerg]
        Plan plan = plan(tempDir, new Predicate("city", Comparison.EQUALS, "Roskilde"));

        assertStats(plan, 1, 3);
        assertEquals(List.of("trips-partition-0.bin"), scannedFiles(plan));
    }

    @Test
    void predicateMatchingNothingPrunesEverything(@TempDir Path tempDir) {
        Plan plan = plan(tempDir, new Predicate("distance", Comparison.GREATER_THAN, 1000L));

        assertStats(plan, 0, 4);
        assertEquals(List.of(), scannedFiles(plan));
        assertEquals(List.of(), FilterOperatorTest.drain(plan.root()));
    }

    @Test
    void whereBuildsFilterOverScan(@TempDir Path tempDir) {
        Predicate predicate = new Predicate("distance", Comparison.GREATER_THAN, 200L);
        Plan plan = plan(tempDir, predicate);

        FilterOperator filter = assertInstanceOf(FilterOperator.class, plan.root());
        assertEquals(predicate, filter.predicate());
        ScanOperator scan = assertInstanceOf(ScanOperator.class, filter.child());
        assertEquals("trips", scan.tableName());
    }

    @Test
    void noWhereBuildsBareScanOverAllPartitions(@TempDir Path tempDir) {
        StorageEngine engine = sortedEngine(tempDir);
        Plan plan = new Planner(engine).plan(new SelectStatement("trips", Optional.empty()));

        ScanOperator scan = assertInstanceOf(ScanOperator.class, plan.root());
        assertEquals(4, scan.partitions().size());
        assertStats(plan, 4, 0);
        assertEquals(8, FilterOperatorTest.drain(plan.root()).size());
    }

    @Test
    void unknownTableThrows(@TempDir Path tempDir) {
        Planner planner = new Planner(new StorageEngine(tempDir));

        assertThrows(
                IllegalArgumentException.class,
                () -> planner.plan(new SelectStatement("missing", Optional.empty())));
    }

    private static Plan plan(Path dir, Predicate predicate) {
        return new Planner(sortedEngine(dir))
                .plan(new SelectStatement("trips", Optional.of(predicate)));
    }

    private static void assertStats(Plan plan, int read, int pruned) {
        assertEquals(4, plan.stats().partitionsTotal());
        assertEquals(read, plan.stats().partitionsRead());
        assertEquals(pruned, plan.stats().partitionsPruned());
    }

    private static List<String> scannedFiles(Plan plan) {
        Operator root = plan.root();
        ScanOperator scan = (ScanOperator) (root instanceof FilterOperator f ? f.child() : root);
        return scan.partitions().stream().map(p -> p.file).toList();
    }

    private static StorageEngine sortedEngine(Path dir) {
        StorageEngine engine = new StorageEngine(dir, 2);
        engine.createTable("trips", COLUMNS);
        engine.copyFile("trips", Path.of("src", "test", "resources", "sorted-trips.csv").toString());
        return engine;
    }
}
