package dk.itu.datasys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageEngineIT {

    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("city", ColumnType.STRING),
            new ColumnSpec("distance", ColumnType.LONG),
            new ColumnSpec("price", ColumnType.DOUBLE)
    );

    @Test
    void schemaPersistsAcrossRestart(@TempDir Path tempDir) {
        StorageEngine engineA = new StorageEngine(tempDir);

        engineA.createTable("trips", COLUMNS);

        StorageEngine engineB = new StorageEngine(tempDir);

        assertThrows(
                IllegalArgumentException.class,
                () -> engineB.createTable("trips", COLUMNS));
    }

    @Test
    void duplicateTableThrows(@TempDir Path tempDir) {
        StorageEngine engine = new StorageEngine(tempDir);

        engine.createTable("trips", COLUMNS);

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.createTable("trips", COLUMNS));
    }

    @Test
    void goldenCsvRoundTrips(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "distance",
                Comparison.GREATER_THAN,
                -1L);

        assertEquals(8, results.size());

        assertEquals("Copenhagen", results.get(0)[0]);
        assertEquals(12L, results.get(0)[1]);
        assertEquals(23.5, results.get(0)[2]);

        assertEquals("Esbjerg", results.get(7)[0]);
        assertEquals(299L, results.get(7)[1]);
        assertEquals(450.25, results.get(7)[2]);
    }

    // ---------- STRING ----------

    @Test
    void stringEquals(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "city",
                Comparison.EQUALS,
                "Copenhagen");

        assertEquals(3, results.size());
        assertEquals(12L, results.get(0)[1]);
        assertEquals(140L, results.get(1)[1]);
        assertEquals(88L, results.get(2)[1]);
    }

    @Test
    void stringLessThan(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "city",
                Comparison.LESS_THAN,
                "Copenhagen");

        assertEquals(2, results.size());
        assertEquals("Aarhus", results.get(0)[0]);
        assertEquals("Aalborg", results.get(1)[0]);
    }

    @Test
    void stringGreaterThan(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "city",
                Comparison.GREATER_THAN,
                "Copenhagen");

        assertEquals(3, results.size());
        assertEquals("Odense", results.get(0)[0]);
        assertEquals("Roskilde", results.get(1)[0]);
        assertEquals("Esbjerg", results.get(2)[0]);
    }

    // ---------- LONG ----------

    @Test
    void longEquals(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "distance",
                Comparison.EQUALS,
                140L);

        assertEquals(1, results.size());
        assertEquals("Copenhagen", results.get(0)[0]);
    }

    @Test
    void longLessThan(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "distance",
                Comparison.LESS_THAN,
                50L);

        assertEquals(2, results.size());
        assertEquals(12L, results.get(0)[1]);
        assertEquals(31L, results.get(1)[1]);
    }

    @Test
    void longGreaterThan(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "distance",
                Comparison.GREATER_THAN,
                200L);

        assertEquals(2, results.size());
        assertEquals(210L, results.get(0)[1]);
        assertEquals(299L, results.get(1)[1]);
    }

    // ---------- DOUBLE ----------

    @Test
    void doubleEquals(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "price",
                Comparison.EQUALS,
                45.0);

        assertEquals(1, results.size());
        assertEquals("Roskilde", results.get(0)[0]);
    }

    @Test
    void doubleLessThan(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "price",
                Comparison.LESS_THAN,
                50.0);

        assertEquals(2, results.size());
        assertEquals("Copenhagen", results.get(0)[0]);
        assertEquals("Roskilde", results.get(1)[0]);
    }

    @Test
    void doubleGreaterThan(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "price",
                Comparison.GREATER_THAN,
                400.0);

        assertEquals(1, results.size());
        assertEquals("Esbjerg", results.get(0)[0]);
    }

    // ---------- EMPTY / ERRORS ----------

    @Test
    void selectCanReturnEmptyResult(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = createGoldenEngine(tempDir);

        List<Object[]> results = engine.select(
                "trips",
                "distance",
                Comparison.EQUALS,
                999L);

        assertTrue(results.isEmpty());
    }

    @Test
    void selectUnknownTableThrows(@TempDir Path tempDir) {
        StorageEngine engine = new StorageEngine(tempDir);

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.select(
                        "missing",
                        "distance",
                        Comparison.EQUALS,
                        10L));
    }

    @Test
    void selectUnknownColumnThrows(@TempDir Path tempDir) {
        StorageEngine engine = new StorageEngine(tempDir);
        createTripsTable(engine);

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.select(
                        "trips",
                        "missing",
                        Comparison.EQUALS,
                        10L));
    }

    @Test
    void selectWrongConstantTypeThrows(@TempDir Path tempDir) {
        StorageEngine engine = new StorageEngine(tempDir);
        createTripsTable(engine);

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.select(
                        "trips",
                        "distance",
                        Comparison.EQUALS,
                        "100"));
    }

    // ---------- PARTITIONING ----------

    @Test
    void goldenFileIsSplitIntoFourPartitions(
            @TempDir Path tempDir) throws Exception {

        StorageEngine engine = new StorageEngine(tempDir, 2);
        createTripsTable(engine);

        Path csv = Path.of(
                "src", "test", "resources", "trips.csv");

        engine.copyFile("trips", csv.toString());

        for (int i = 0; i < 4; i++) {
            assertTrue(Files.exists(
                    tempDir.resolve(
                            "trips-partition-" + i + ".bin")));
        }
    }

    @Test
    void partitioningStoresMinMax(
            @TempDir Path tempDir) throws Exception {

        StorageEngine engine = new StorageEngine(tempDir, 2);
        createTripsTable(engine);

        Path csv = Path.of(
                "src", "test", "resources", "trips.csv");

        engine.copyFile("trips", csv.toString());

        TableMetadata table =
                engine.tableMetadata("trips");

        assertEquals(4, table.partitions.size());

        // Partition 0:
        // Copenhagen,12,23.5
        // Aarhus,187,301.0
        assertEquals("Aarhus",
                table.partitions.get(0).minValues.get(0));
        assertEquals("Copenhagen",
                table.partitions.get(0).maxValues.get(0));
        assertEquals(12L,
                table.partitions.get(0).minValues.get(1));
        assertEquals(187L,
                table.partitions.get(0).maxValues.get(1));
        assertEquals(23.5,
                table.partitions.get(0).minValues.get(2));
        assertEquals(301.0,
                table.partitions.get(0).maxValues.get(2));

        // Partition 1:
        // Odense,95,120.75
        // Copenhagen,140,210.0
        assertEquals("Copenhagen",
                table.partitions.get(1).minValues.get(0));
        assertEquals("Odense",
                table.partitions.get(1).maxValues.get(0));
        assertEquals(95L,
                table.partitions.get(1).minValues.get(1));
        assertEquals(140L,
                table.partitions.get(1).maxValues.get(1));
        assertEquals(120.75,
                table.partitions.get(1).minValues.get(2));
        assertEquals(210.0,
                table.partitions.get(1).maxValues.get(2));

        // Partition 2:
        // Aalborg,210,340.5
        // Roskilde,31,45.0
        assertEquals("Aalborg",
                table.partitions.get(2).minValues.get(0));
        assertEquals("Roskilde",
                table.partitions.get(2).maxValues.get(0));
        assertEquals(31L,
                table.partitions.get(2).minValues.get(1));
        assertEquals(210L,
                table.partitions.get(2).maxValues.get(1));
        assertEquals(45.0,
                table.partitions.get(2).minValues.get(2));
        assertEquals(340.5,
                table.partitions.get(2).maxValues.get(2));

        // Partition 3:
        // Copenhagen,88,99.99
        // Esbjerg,299,450.25
        assertEquals("Copenhagen",
                table.partitions.get(3).minValues.get(0));
        assertEquals("Esbjerg",
                table.partitions.get(3).maxValues.get(0));
        assertEquals(88L,
                table.partitions.get(3).minValues.get(1));
        assertEquals(299L,
                table.partitions.get(3).maxValues.get(1));
        assertEquals(99.99,
                table.partitions.get(3).minValues.get(2));
        assertEquals(450.25,
                table.partitions.get(3).maxValues.get(2));
    }

    // ---------- PRUNING ----------

    @Test
    void pruningSkipsAtLeastTwoPartitions(
            @TempDir Path tempDir) throws Exception {

        StorageEngine engine =
                new StorageEngine(tempDir, 2);

        createTripsTable(engine);

        Path csv = Path.of(
                "src", "test", "resources",
                "sorted-trips.csv");

        engine.copyFile("trips", csv.toString());

        List<Object[]> results = engine.select(
                "trips",
                "distance",
                Comparison.GREATER_THAN,
                200L);

        assertEquals(2, results.size());
        assertEquals(1, engine.getLastScanStats().partitionsRead());
        assertEquals(3, engine.getLastScanStats().partitionsPruned());
    }

    // ---------- RESTART ----------

    @Test
    void dataSurvivesRestart(@TempDir Path tempDir)
            throws Exception {

        Path csv = Path.of(
                "src", "test", "resources", "trips.csv");

        StorageEngine engineA =
                new StorageEngine(tempDir);

        createTripsTable(engineA);
        engineA.copyFile("trips", csv.toString());

        StorageEngine engineB =
                new StorageEngine(tempDir);

        List<Object[]> results = engineB.select(
                "trips",
                "distance",
                Comparison.GREATER_THAN,
                -1L);

        assertEquals(8, results.size());

        assertEquals("Copenhagen", results.get(0)[0]);
        assertEquals(12L, results.get(0)[1]);
        assertEquals(23.5, results.get(0)[2]);

        assertEquals("Esbjerg", results.get(7)[0]);
        assertEquals(299L, results.get(7)[1]);
        assertEquals(450.25, results.get(7)[2]);
    }

    private StorageEngine createGoldenEngine(Path directory)
            throws Exception {

        StorageEngine engine =
                new StorageEngine(directory);

        createTripsTable(engine);

        Path csv = Path.of(
                "src", "test", "resources", "trips.csv");

        engine.copyFile("trips", csv.toString());

        return engine;
    }

    private void createTripsTable(StorageEngine engine) {
        engine.createTable("trips", COLUMNS);
    }
}