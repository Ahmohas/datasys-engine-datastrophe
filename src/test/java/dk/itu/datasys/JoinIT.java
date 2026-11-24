package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Exercise 10 testbed through the front door. Expected rows are DuckDB's output for the
 * same script, sorted: a hash join emits rows in probe order and DuckDB promises no order
 * either. DuckDB must load the CSVs with {@code (HEADER false)}: customers.csv has only STRING
 * columns, so DuckDB's sniffer would otherwise take its first line for a header and drop Anna.
 */
class JoinIT {

    private static final Path SCRIPT = Path.of("src", "test", "resources", "joins", "joins.sql");

    private static final String ORDERS_CUSTOMERS = """
            SELECT * FROM orders, customers WHERE o_custname = c_name;""";
    private static final String ORDERS_CUSTOMERS_PRODUCTS = """
            SELECT * FROM orders, customers, products
            WHERE o_custname = c_name AND o_prodname = p_name;""";
    private static final String ALL_FOUR = """
            SELECT * FROM orders, customers, products, cities
            WHERE o_custname = c_name AND o_prodname = p_name AND c_city = ct_city;""";
    private static final String COPENHAGEN_ONLY = """
            SELECT * FROM orders, customers, products
            WHERE o_custname = c_name AND o_prodname = p_name AND c_city = 'Copenhagen';""";

    // DuckDB 2.0.1, sorted
    private static final List<String> DUCKDB_ORDERS_CUSTOMERS = List.of(
            "100,Anna,Bike,1,Anna,Copenhagen",
            "101,Bob,Helmet,2,Bob,Aarhus",
            "102,Carol,Bike,1,Carol,Copenhagen",
            "103,Anna,Helmet,1,Anna,Copenhagen");
    private static final List<String> DUCKDB_ORDERS_CUSTOMERS_PRODUCTS = List.of(
            "100,Anna,Bike,1,Anna,Copenhagen,Bike,2999.0",
            "101,Bob,Helmet,2,Bob,Aarhus,Helmet,299.0",
            "102,Carol,Bike,1,Carol,Copenhagen,Bike,2999.0",
            "103,Anna,Helmet,1,Anna,Copenhagen,Helmet,299.0");
    private static final List<String> DUCKDB_ALL_FOUR = List.of(
            "100,Anna,Bike,1,Anna,Copenhagen,Bike,2999.0,Copenhagen,Denmark,1400000",
            "101,Bob,Helmet,2,Bob,Aarhus,Helmet,299.0,Aarhus,Denmark,290000",
            "102,Carol,Bike,1,Carol,Copenhagen,Bike,2999.0,Copenhagen,Denmark,1400000",
            "103,Anna,Helmet,1,Anna,Copenhagen,Helmet,299.0,Copenhagen,Denmark,1400000");
    private static final List<String> DUCKDB_COPENHAGEN_ONLY = List.of(
            "100,Anna,Bike,1,Anna,Copenhagen,Bike,2999.0",
            "102,Carol,Bike,1,Carol,Copenhagen,Bike,2999.0",
            "103,Anna,Helmet,1,Anna,Copenhagen,Helmet,299.0");

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @Test
    void testbedScriptMatchesDuckDbSorted(@TempDir Path tempDir) {
        assertEquals(0, run(tempDir, "-f", SCRIPT.toString()), stderr());

        List<String> expected = Stream.of(
                        DUCKDB_ORDERS_CUSTOMERS, DUCKDB_ORDERS_CUSTOMERS_PRODUCTS,
                        DUCKDB_ALL_FOUR, DUCKDB_COPENHAGEN_ONLY)
                .flatMap(List::stream)
                .sorted()
                .toList();
        assertEquals(expected, sortedLines());
    }

    @Test
    void eachQueryMatchesDuckDbSorted(@TempDir Path tempDir) {
        loadTestbed(tempDir);

        assertQuery(tempDir, ORDERS_CUSTOMERS, DUCKDB_ORDERS_CUSTOMERS);
        assertQuery(tempDir, ORDERS_CUSTOMERS_PRODUCTS, DUCKDB_ORDERS_CUSTOMERS_PRODUCTS);
        assertQuery(tempDir, ALL_FOUR, DUCKDB_ALL_FOUR);
        assertQuery(tempDir, COPENHAGEN_ONLY, DUCKDB_COPENHAGEN_ONLY);
    }

    @Test
    void everyJoinLogsItsSizesAndTheSelectLogsEveryTable(@TempDir Path tempDir) throws Exception {
        loadTestbed(tempDir);
        Path log = Path.of("logs", "engine.log");
        int before = Files.exists(log) ? Files.readAllLines(log).size() : 0;

        assertQuery(tempDir, ALL_FOUR, DUCKDB_ALL_FOUR);

        List<String> lines = Files.readAllLines(log);
        List<String> messages = new ArrayList<>();
        for (String line : lines.subList(before, lines.size())) {
            String[] fields = line.split(",", 7);
            if (fields.length == 7) {
                messages.add(fields[6]);
            }
        }
        // Closed bottom-up: orders x customers, then x products, then x cities
        assertEquals(
                List.of(
                        "operator=HashJoin buildKey=o_custname probeKey=c_name rowsBuild=4 rowsProbe=3 rowsOut=4",
                        "operator=HashJoin buildKey=o_prodname probeKey=p_name rowsBuild=4 rowsProbe=2 rowsOut=4",
                        "operator=HashJoin buildKey=c_city probeKey=ct_city rowsBuild=4 rowsProbe=2 rowsOut=4"),
                messages.stream().filter(m -> m.startsWith("operator=HashJoin")).toList());
        assertTrue(messages.stream().anyMatch(m -> m.startsWith(
                        "statement=SELECT table=orders+customers+products+cities rowsOut=4 ")
                        && m.contains(" version=1+1+1+1 ")),
                String.join("\n", messages));
    }

    @Test
    void unconnectedFromListFailsWithAClearError(@TempDir Path tempDir) {
        loadTestbed(tempDir);

        int exitCode = run(tempDir, "-c",
                "SELECT * FROM customers, products, orders WHERE o_custname = c_name AND o_prodname = p_name;");

        assertEquals(1, exitCode);
        assertEquals("", stdout.toString(StandardCharsets.UTF_8));
        assertTrue(stderr().contains("No join predicate connects table products"), stderr());
    }

    @Test
    void ambiguousColumnFailsWithAClearError(@TempDir Path tempDir) {
        loadTestbed(tempDir);
        assertEquals(0, run(tempDir, "-c", "CREATE TABLE shops (c_city STRING);"), stderr());

        int exitCode = run(tempDir, "-c",
                "SELECT * FROM customers, shops WHERE c_city = 'Aarhus';");

        assertEquals(1, exitCode);
        assertTrue(stderr().contains("Ambiguous column: c_city"), stderr());
    }

    private void loadTestbed(Path tempDir) {
        StringBuilder setup = new StringBuilder();
        try {
            for (String line : Files.readAllLines(SCRIPT)) {
                if (line.startsWith("CREATE") || line.startsWith("COPY")) {
                    setup.append(line).append('\n');
                }
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        assertEquals(0, run(tempDir, "-c", setup.toString()), stderr());
    }

    private void assertQuery(Path tempDir, String sql, List<String> expectedSorted) {
        stdout.reset();
        stderr.reset();
        assertEquals(0, run(tempDir, "-c", sql), stderr());
        assertEquals(expectedSorted, sortedLines(), sql);
    }

    private List<String> sortedLines() {
        return stdout.toString(StandardCharsets.UTF_8).lines().sorted().toList();
    }

    private int run(Path tempDir, String... args) {
        return Engine.run(
                args,
                tempDir.resolve("data"),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private String stderr() {
        return stderr.toString(StandardCharsets.UTF_8);
    }
}
