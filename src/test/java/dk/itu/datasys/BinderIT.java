package dk.itu.datasys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void validDeleteBinds(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        engine.createTable("trips", List.of(new ColumnSpec("distance", ColumnType.LONG)));
        Binder binder = new Binder(engine);

        assertDoesNotThrow(() -> binder.bind(new DeleteStatement("trips", Optional.empty())));
        assertDoesNotThrow(() -> binder.bind(new DeleteStatement(
                "trips",
                Optional.of(new Predicate("distance", Comparison.LESS_THAN, 10L)))));
    }

    @Test
    void deleteOnUnknownTableThrows(@TempDir Path directory) {
        Binder binder = new Binder(new StorageEngine(directory));

        Statement statement = new DeleteStatement("missing", Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> binder.bind(statement));
    }

    @Test
    void deleteOnUnknownColumnThrows(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        engine.createTable("trips", List.of(new ColumnSpec("city", ColumnType.STRING)));
        Binder binder = new Binder(engine);

        Statement statement = new DeleteStatement(
                "trips",
                Optional.of(new Predicate("missing", Comparison.EQUALS, "x")));

        assertThrows(IllegalArgumentException.class, () -> binder.bind(statement));
    }

    @Test
    void deleteWithTypeMismatchedConstantThrows(@TempDir Path directory) {
        StorageEngine engine = new StorageEngine(directory);
        engine.createTable("trips", List.of(new ColumnSpec("distance", ColumnType.LONG)));
        Binder binder = new Binder(engine);

        Statement statement = new DeleteStatement(
                "trips",
                Optional.of(new Predicate("distance", Comparison.EQUALS, "x")));

        assertThrows(IllegalArgumentException.class, () -> binder.bind(statement));
    }

    // ---- Exercise 10: binding without qualified names

    @Test
    void validJoinQueryBinds(@TempDir Path directory) {
        Binder binder = new Binder(testbed(directory));

        assertDoesNotThrow(() -> binder.bind(new SelectStatement(
                List.of("orders", "customers", "products", "cities"),
                List.of(new Predicate("c_city", Comparison.EQUALS, "Copenhagen")),
                List.of(
                        new JoinPredicate("o_custname", "c_name"),
                        new JoinPredicate("p_name", "o_prodname"),
                        new JoinPredicate("c_city", "ct_city")))));
    }

    @Test
    void unknownColumnAcrossTablesThrows(@TempDir Path directory) {
        Binder binder = new Binder(testbed(directory));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> binder.bind(
                new SelectStatement(
                        List.of("orders", "customers"),
                        List.of(),
                        List.of(new JoinPredicate("o_custname", "c_nickname")))));
        assertTrue(e.getMessage().contains("Unknown column: c_nickname"), e.getMessage());
    }

    @Test
    void columnInTwoTablesIsAmbiguous(@TempDir Path directory) {
        StorageEngine engine = testbed(directory);
        engine.createTable("shops", List.of(new ColumnSpec("c_city", ColumnType.STRING)));
        Binder binder = new Binder(engine);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> binder.bind(
                new SelectStatement(
                        List.of("customers", "shops"),
                        List.of(new Predicate("c_city", Comparison.EQUALS, "Aarhus")),
                        List.of())));
        assertTrue(e.getMessage().contains("Ambiguous column: c_city"), e.getMessage());
    }

    @Test
    void joinAcrossMismatchedTypesThrows(@TempDir Path directory) {
        Binder binder = new Binder(testbed(directory));

        // o_quantity is LONG, p_price is DOUBLE: a hash join would never find 3 equal to 3.0
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> binder.bind(
                new SelectStatement(
                        List.of("orders", "products"),
                        List.of(),
                        List.of(new JoinPredicate("o_quantity", "p_price")))));
        assertTrue(e.getMessage().contains("LONG with DOUBLE"), e.getMessage());
    }

    @Test
    void joinWhoseSidesLandInOneTableThrows(@TempDir Path directory) {
        Binder binder = new Binder(testbed(directory));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> binder.bind(
                new SelectStatement(
                        List.of("orders", "customers"),
                        List.of(),
                        List.of(new JoinPredicate("o_custname", "o_prodname")))));
        assertTrue(e.getMessage().contains("two different tables"), e.getMessage());
    }

    @Test
    void filterOnAnyListedTableIsTypeChecked(@TempDir Path directory) {
        Binder binder = new Binder(testbed(directory));

        assertThrows(IllegalArgumentException.class, () -> binder.bind(new SelectStatement(
                List.of("orders", "customers"),
                List.of(new Predicate("c_city", Comparison.EQUALS, 42L)),
                List.of(new JoinPredicate("o_custname", "c_name")))));
    }

    @Test
    void unknownTableInListThrows(@TempDir Path directory) {
        Binder binder = new Binder(testbed(directory));

        assertThrows(IllegalArgumentException.class, () -> binder.bind(new SelectStatement(
                List.of("orders", "suppliers"), List.of(), List.of())));
    }

    @Test
    void tableListedTwiceThrows(@TempDir Path directory) {
        Binder binder = new Binder(testbed(directory));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> binder.bind(
                new SelectStatement(List.of("orders", "orders"), List.of(), List.of())));
        assertTrue(e.getMessage().contains("listed twice"), e.getMessage());
    }

    /** The four empty testbed tables; binding only needs their schemas. */
    static StorageEngine testbed(Path directory) {
        return createTestbedTables(new StorageEngine(directory));
    }

    /** Creates the Exercise 10 testbed's four tables (orders, products, customers, cities). */
    static StorageEngine createTestbedTables(StorageEngine engine) {
        engine.createTable("orders", List.of(
                new ColumnSpec("o_orderkey", ColumnType.LONG),
                new ColumnSpec("o_custname", ColumnType.STRING),
                new ColumnSpec("o_prodname", ColumnType.STRING),
                new ColumnSpec("o_quantity", ColumnType.LONG)));
        engine.createTable("products", List.of(
                new ColumnSpec("p_name", ColumnType.STRING),
                new ColumnSpec("p_price", ColumnType.DOUBLE)));
        engine.createTable("customers", List.of(
                new ColumnSpec("c_name", ColumnType.STRING),
                new ColumnSpec("c_city", ColumnType.STRING)));
        engine.createTable("cities", List.of(
                new ColumnSpec("ct_city", ColumnType.STRING),
                new ColumnSpec("ct_country", ColumnType.STRING),
                new ColumnSpec("ct_population", ColumnType.LONG)));
        return engine;
    }
}
