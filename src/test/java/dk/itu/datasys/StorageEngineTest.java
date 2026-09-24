package dk.itu.datasys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageEngineTest {

    @Test
    void createTablePersistsCatalog(@TempDir Path tempDir) throws Exception {
        StorageEngine engine = new StorageEngine(tempDir);

        engine.createTable(
                "trips",
                List.of(
                        new ColumnSpec("city", ColumnType.STRING),
                        new ColumnSpec("distance", ColumnType.LONG),
                        new ColumnSpec("price", ColumnType.DOUBLE)
                )
        );

        assertTrue(Files.exists(tempDir.resolve("catalog.json")));

        String catalog = Files.readString(tempDir.resolve("catalog.json"));

        assertTrue(catalog.contains("\"trips\""));
        assertTrue(catalog.contains("\"city\""));
        assertTrue(catalog.contains("\"distance\""));
        assertTrue(catalog.contains("\"price\""));
    }

    @Test
    void schemaSurvivesRestart(@TempDir Path tempDir) {
        StorageEngine engine = new StorageEngine(tempDir);

        engine.createTable(
                "trips",
                List.of(
                        new ColumnSpec("city", ColumnType.STRING),
                        new ColumnSpec("distance", ColumnType.LONG)
                )
        );

        assertDoesNotThrow(() -> new StorageEngine(tempDir));
    }

    @Test
    void duplicateTableThrows(@TempDir Path tempDir) {
        StorageEngine engine = new StorageEngine(tempDir);

        List<ColumnSpec> columns = List.of(
                new ColumnSpec("city", ColumnType.STRING)
        );

        engine.createTable("trips", columns);

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.createTable("trips", columns)
        );
    }

    @Test
    void emptyColumnsThrow(@TempDir Path tempDir) {
        StorageEngine engine = new StorageEngine(tempDir);

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.createTable("trips", List.of())
        );
    }

    @Test
    void duplicateColumnNamesThrow(@TempDir Path tempDir) {
        StorageEngine engine = new StorageEngine(tempDir);

        List<ColumnSpec> columns = List.of(
                new ColumnSpec("city", ColumnType.STRING),
                new ColumnSpec("city", ColumnType.STRING)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.createTable("trips", columns)
        );
    }
    
    @Test
    void copyFileCreatesPartitionAndUpdatesCatalog(
            @TempDir Path tempDir) throws Exception {

    StorageEngine engine = new StorageEngine(tempDir);

    engine.createTable(
            "trips",
            List.of(
                    new ColumnSpec("city", ColumnType.STRING),
                    new ColumnSpec("distance", ColumnType.LONG),
                    new ColumnSpec("price", ColumnType.DOUBLE)
            )
    );

    Path csv = tempDir.resolve("trips.csv");

    Files.writeString(
            csv,
            """
            Copenhagen,100,20.5
            Aarhus,50,30.0
            Odense,75,10.5
            """
    );

    engine.copyFile("trips", csv.toString());

    assertTrue(
            Files.exists(
                    tempDir.resolve("trips-partition-0.bin")
            )
    );

    String catalog =
            Files.readString(tempDir.resolve("catalog.json"));

    assertTrue(catalog.contains("trips-partition-0.bin"));
    }

    @Test
    void copyFileSplitsLargeInputIntoPartitions(
            @TempDir Path tempDir) throws Exception {

        StorageEngine engine = new StorageEngine(tempDir);

        engine.createTable(
                "trips",
                List.of(
                        new ColumnSpec("city", ColumnType.STRING),
                        new ColumnSpec("distance", ColumnType.LONG),
                        new ColumnSpec("price", ColumnType.DOUBLE)
                )
        );

        Path csv = tempDir.resolve("trips.csv");

        StringBuilder content = new StringBuilder();

        for (int i = 0; i < 2500; i++) {
            content
                    .append("City")
                    .append(i)
                    .append(",")
                    .append(i)
                    .append(",")
                    .append(i * 1.5)
                    .append("\n");
        }

        Files.writeString(csv, content);

        engine.copyFile("trips", csv.toString());

        assertTrue(
                Files.exists(
                        tempDir.resolve("trips-partition-0.bin")));

        assertTrue(
                Files.exists(
                        tempDir.resolve("trips-partition-1.bin")));

        assertTrue(
                Files.exists(
                        tempDir.resolve("trips-partition-2.bin")));

        String catalog =
                Files.readString(tempDir.resolve("catalog.json"));

        assertTrue(catalog.contains("trips-partition-0.bin"));
        assertTrue(catalog.contains("trips-partition-1.bin"));
        assertTrue(catalog.contains("trips-partition-2.bin"));
    }

    private void createTripsTable(StorageEngine engine) {
        engine.createTable(
                "trips",
                List.of(
                        new ColumnSpec("city", ColumnType.STRING),
                        new ColumnSpec("distance", ColumnType.LONG),
                        new ColumnSpec("price", ColumnType.DOUBLE)
                )
        );
    }

    @Test
    void selectEqualsReturnsMatchingRows(
            @TempDir Path tempDir) throws Exception {

        StorageEngine engine = new StorageEngine(tempDir);
        createTripsTable(engine);

        Path csv = tempDir.resolve("trips.csv");

        Files.writeString(
                csv,
                """
                Copenhagen,100,20.5
                Aarhus,50,30.0
                Odense,75,10.5
                """);

        engine.copyFile("trips", csv.toString());

        List<Object[]> results =
                engine.select(
                        "trips",
                        "distance",
                        Comparison.EQUALS,
                        50L);

        assertEquals(1, results.size());
        assertEquals("Aarhus", results.get(0)[0]);
        assertEquals(50L, results.get(0)[1]);
        assertEquals(30.0, results.get(0)[2]);
    }

    @Test
    void selectLessThanReturnsMatchingRows(
            @TempDir Path tempDir) throws Exception {

        StorageEngine engine = new StorageEngine(tempDir);
        createTripsTable(engine);

        Path csv = tempDir.resolve("trips.csv");

        Files.writeString(
                csv,
                """
                Copenhagen,100,20.5
                Aarhus,50,30.0
                Odense,75,10.5
                """);

        engine.copyFile("trips", csv.toString());

        List<Object[]> results =
                engine.select(
                        "trips",
                        "distance",
                        Comparison.LESS_THAN,
                        80L);

        assertEquals(2, results.size());
        assertEquals("Aarhus", results.get(0)[0]);
        assertEquals("Odense", results.get(1)[0]);
    }

    @Test
    void selectGreaterThanReturnsMatchingRows(
            @TempDir Path tempDir) throws Exception {

        StorageEngine engine = new StorageEngine(tempDir);
        createTripsTable(engine);

        Path csv = tempDir.resolve("trips.csv");

        Files.writeString(
                csv,
                """
                Copenhagen,100,20.5
                Aarhus,50,30.0
                Odense,75,10.5
                """);

        engine.copyFile("trips", csv.toString());

        List<Object[]> results =
                engine.select(
                        "trips",
                        "distance",
                        Comparison.GREATER_THAN,
                        60L);

        assertEquals(2, results.size());
        assertEquals("Copenhagen", results.get(0)[0]);
        assertEquals("Odense", results.get(1)[0]);
    }

    @Test
    void selectCanReturnEmptyResult(
            @TempDir Path tempDir) throws Exception {

        StorageEngine engine = new StorageEngine(tempDir);
        createTripsTable(engine);

        Path csv = tempDir.resolve("trips.csv");

        Files.writeString(
                csv,
                """
                Copenhagen,100,20.5
                Aarhus,50,30.0
                Odense,75,10.5
                """);

        engine.copyFile("trips", csv.toString());

        List<Object[]> results =
                engine.select(
                        "trips",
                        "distance",
                        Comparison.EQUALS,
                        999L);

        assertTrue(results.isEmpty());
    }

    @Test
    void selectPrunesPartitions(
            @TempDir Path tempDir) throws Exception {

        StorageEngine engine = new StorageEngine(tempDir);
        createTripsTable(engine);

        Path csv = tempDir.resolve("trips.csv");

        StringBuilder content = new StringBuilder();

        for (int i = 0; i < 2000; i++) {
            content
                    .append("City")
                    .append(i)
                    .append(",")
                    .append(i)
                    .append(",")
                    .append(i)
                    .append("\n");
        }

        Files.writeString(csv, content);

        engine.copyFile("trips", csv.toString());

        List<Object[]> results =
                engine.select(
                        "trips",
                        "distance",
                        Comparison.EQUALS,
                        1500L);

        assertEquals(1, results.size());
        assertEquals(1500L, results.get(0)[1]);

        ScanStats stats = engine.getLastScanStats();

        assertEquals(1, stats.partitionsRead());
        assertEquals(1, stats.partitionsPruned());
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
}