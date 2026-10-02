package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanOperatorTest {

    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("city", ColumnType.STRING),
            new ColumnSpec("distance", ColumnType.LONG),
            new ColumnSpec("price", ColumnType.DOUBLE));

    @Test
    void returnsEveryRowOfEveryPartitionInOrder(@TempDir Path tempDir) {
        StorageEngine engine = goldenEngine(tempDir);
        List<PartitionMetadata> all = engine.tableMetadata("trips").partitions;

        List<Object[]> rows = FilterOperatorTest.drain(new ScanOperator(engine, "trips", all));

        assertEquals(8, rows.size());
        assertEquals("Copenhagen", rows.get(0)[0]);
        assertEquals("Esbjerg", rows.get(7)[0]);
    }

    @Test
    void returnsExactlyTheRowsOfTheHandedPartitions(@TempDir Path tempDir) {
        StorageEngine engine = goldenEngine(tempDir);
        List<PartitionMetadata> all = engine.tableMetadata("trips").partitions;

        // Partition 1 holds Odense,95 and Copenhagen,140; partition 3 holds Copenhagen,88 and Esbjerg,299
        List<Object[]> rows = FilterOperatorTest.drain(
                new ScanOperator(engine, "trips", List.of(all.get(1), all.get(3))));

        assertEquals(4, rows.size());
        assertEquals(95L, rows.get(0)[1]);
        assertEquals(140L, rows.get(1)[1]);
        assertEquals(88L, rows.get(2)[1]);
        assertEquals(299L, rows.get(3)[1]);
    }

    @Test
    void emptyPartitionListReadsNothingAndReturnsNothing(@TempDir Path tempDir) {
        StorageEngine engine = goldenEngine(tempDir);
        ScanOperator scan = new ScanOperator(engine, "trips", List.of());

        scan.open();
        assertNull(scan.next());
        scan.close();
    }

    @Test
    void schemaIsTheTableSchema(@TempDir Path tempDir) {
        StorageEngine engine = goldenEngine(tempDir);

        assertEquals(COLUMNS, new ScanOperator(engine, "trips", List.of()).schema());
    }

    private static StorageEngine goldenEngine(Path dir) {
        StorageEngine engine = new StorageEngine(dir, 2);
        engine.createTable("trips", COLUMNS);
        engine.copyFile("trips", Path.of("src", "test", "resources", "trips.csv").toString());
        return engine;
    }
}
