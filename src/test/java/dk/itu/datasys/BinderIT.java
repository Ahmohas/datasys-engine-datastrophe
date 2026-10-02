package dk.itu.datasys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BinderIT {

    @Test
    void validCreateTableBinds(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        Binder binder = new Binder(engine);

        Statement statement = new CreateTableStatement(
                "trips",
                List.of(new ColumnSpec("city", ColumnType.STRING)));

        assertDoesNotThrow(() -> binder.bind(statement));
    }

    @Test
    void validCopyBinds(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        engine.createTable("trips", List.of(new ColumnSpec("city", ColumnType.STRING)));
        Binder binder = new Binder(engine);

        Statement statement = new CopyStatement("trips", "trips.csv");

        assertDoesNotThrow(() -> binder.bind(statement));
    }

    @Test
    void validSelectBinds(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        engine.createTable("trips", List.of(new ColumnSpec("distance", ColumnType.LONG)));
        Binder binder = new Binder(engine);

        Statement statement = new SelectStatement(
                "trips",
                Optional.of(new Predicate("distance", Comparison.GREATER_THAN, 100L)));

        assertDoesNotThrow(() -> binder.bind(statement));
    }

    @Test
    void selectOnUnknownTableThrows(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        Binder binder = new Binder(engine);

        Statement statement = new SelectStatement("missing", Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> binder.bind(statement));
    }

    @Test
    void selectOnUnknownColumnThrows(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        engine.createTable("trips", List.of(new ColumnSpec("city", ColumnType.STRING)));
        Binder binder = new Binder(engine);

        Statement statement = new SelectStatement(
                "trips",
                Optional.of(new Predicate("missing", Comparison.EQUALS, "x")));

        assertThrows(IllegalArgumentException.class, () -> binder.bind(statement));
    }

    @Test
    void selectWithTypeMismatchedConstantThrows(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        engine.createTable("trips", List.of(new ColumnSpec("distance", ColumnType.LONG)));
        Binder binder = new Binder(engine);

        Statement statement = new SelectStatement(
                "trips",
                Optional.of(new Predicate("distance", Comparison.EQUALS, "x")));

        assertThrows(IllegalArgumentException.class, () -> binder.bind(statement));
    }

    @Test
    void duplicateColumnsInCreateTableThrows(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        Binder binder = new Binder(engine);

        Statement statement = new CreateTableStatement(
                "trips",
                List.of(
                        new ColumnSpec("city", ColumnType.STRING),
                        new ColumnSpec("city", ColumnType.LONG)));

        assertThrows(IllegalArgumentException.class, () -> binder.bind(statement));
    }
}