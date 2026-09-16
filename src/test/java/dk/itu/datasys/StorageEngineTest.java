package dk.itu.datasys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}