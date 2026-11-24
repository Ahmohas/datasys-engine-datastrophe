package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JoinPlannerTest {

    private static final JoinPredicate ORDERS_CUSTOMERS = new JoinPredicate("o_custname", "c_name");
    private static final JoinPredicate ORDERS_PRODUCTS = new JoinPredicate("o_prodname", "p_name");
    private static final JoinPredicate CUSTOMERS_CITIES = new JoinPredicate("c_city", "ct_city");
    private static final Predicate IN_COPENHAGEN = new Predicate("c_city", Comparison.EQUALS, "Copenhagen");

    @Test
    void fourTablesBuildThreeHashJoinsLeftDeepInFromListOrder(@TempDir Path dir) {
        Plan plan = new Planner(testbed(dir, 1000)).plan(new SelectStatement(
                List.of("orders", "customers", "products", "cities"),
                List.of(),
                List.of(ORDERS_CUSTOMERS, ORDERS_PRODUCTS, CUSTOMERS_CITIES)));

        //                 join3
        //                /     \
        //            join2     cities
        //           /     \
        //       join1     products
        //      /     \
        //  orders   customers
        HashJoinOperator join3 = assertInstanceOf(HashJoinOperator.class, plan.root());
        HashJoinOperator join2 = assertInstanceOf(HashJoinOperator.class, join3.build());
        HashJoinOperator join1 = assertInstanceOf(HashJoinOperator.class, join2.build());
        assertEquals("orders", scanOf(join1.build()).tableName());
        assertEquals("customers", scanOf(join1.probe()).tableName());
        assertEquals("products", scanOf(join2.probe()).tableName());
        assertEquals("cities", scanOf(join3.probe()).tableName());

        // Keys: build side by position in the joined-so-far row, probe side in its own table
        assertKeys(join1, "o_custname", "c_name");
        assertKeys(join2, "o_prodname", "p_name");
        assertKeys(join3, "c_city", "ct_city");

        // SELECT * emits columns in FROM-list order
        assertEquals(
                List.of("o_orderkey", "o_custname", "o_prodname", "o_quantity", "c_name", "c_city",
                        "p_name", "p_price", "ct_city", "ct_country", "ct_population"),
                plan.root().schema().stream().map(ColumnSpec::name).toList());
        assertEquals(4, FilterOperatorTest.drain(plan.root()).size());
    }

    @Test
    void joinPredicateMayNameTheNewTableFirst(@TempDir Path dir) {
        // c_name = o_custname: customers is written on the left, but it is the probe side
        Plan plan = new Planner(testbed(dir, 1000)).plan(new SelectStatement(
                List.of("orders", "customers"),
                List.of(),
                List.of(new JoinPredicate("c_name", "o_custname"))));

        HashJoinOperator join = assertInstanceOf(HashJoinOperator.class, plan.root());
        assertKeys(join, "o_custname", "c_name");
        assertEquals(4, FilterOperatorTest.drain(join).size());
    }

    @Test
    void filtersSitBelowTheJoinsAndPruningIsPerTable(@TempDir Path dir) {
        // One row per partition: customers has 3 partitions; c_city = 'Copenhagen' prunes Bob's
        Plan plan = new Planner(testbed(dir, 1)).plan(new SelectStatement(
                List.of("orders", "customers", "products"),
                List.of(IN_COPENHAGEN),
                List.of(ORDERS_CUSTOMERS, ORDERS_PRODUCTS)));

        HashJoinOperator join2 = assertInstanceOf(HashJoinOperator.class, plan.root());
        HashJoinOperator join1 = assertInstanceOf(HashJoinOperator.class, join2.build());
        FilterOperator filter = assertInstanceOf(FilterOperator.class, join1.probe());
        assertEquals(IN_COPENHAGEN, filter.predicate());
        assertEquals("customers", scanOf(filter.child()).tableName());
        assertInstanceOf(ScanOperator.class, join1.build());    // orders: no filter
        assertInstanceOf(ScanOperator.class, join2.probe());    // products: no filter

        assertStats(plan.tables().get(0), "orders", 4, 4, 0);
        assertStats(plan.tables().get(1), "customers", 3, 2, 1);
        assertStats(plan.tables().get(2), "products", 2, 2, 0);
        assertEquals(1, plan.stats().partitionsPruned());
        assertEquals(3, FilterOperatorTest.drain(plan.root()).size());
    }

    @Test
    void severalFiltersOnOneTableAllPrune(@TempDir Path dir) {
        // orders, one row per partition: o_quantity > 1 keeps only order 101; o_orderkey < 101 none
        Plan plan = new Planner(testbed(dir, 1)).plan(new SelectStatement(
                List.of("orders", "customers"),
                List.of(
                        new Predicate("o_quantity", Comparison.GREATER_THAN, 1L),
                        new Predicate("o_orderkey", Comparison.LESS_THAN, 101L)),
                List.of(ORDERS_CUSTOMERS)));

        assertStats(plan.tables().get(0), "orders", 4, 0, 4);
        assertEquals(List.of(), FilterOperatorTest.drain(plan.root()));
    }

    @Test
    void unconnectedFromListIsRejected(@TempDir Path dir) {
        Planner planner = new Planner(testbed(dir, 1000));

        // products joins orders, but orders is listed after it: products has no partner before it
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> planner.plan(
                new SelectStatement(
                        List.of("customers", "products", "orders"),
                        List.of(),
                        List.of(ORDERS_CUSTOMERS, ORDERS_PRODUCTS))));
        assertTrue(e.getMessage().contains("No join predicate connects table products"), e.getMessage());
        assertTrue(e.getMessage().contains("cross product"), e.getMessage());
        assertTrue(e.getMessage().contains("Reorder the FROM list"), e.getMessage());

        // The same tables in a connected order plan fine
        Plan plan = planner.plan(new SelectStatement(
                List.of("customers", "orders", "products"),
                List.of(),
                List.of(ORDERS_CUSTOMERS, ORDERS_PRODUCTS)));
        assertEquals(4, FilterOperatorTest.drain(plan.root()).size());
    }

    @Test
    void tablesWithoutAnyJoinPredicateAreRejected(@TempDir Path dir) {
        Planner planner = new Planner(testbed(dir, 1000));

        assertThrows(IllegalArgumentException.class, () -> planner.plan(
                new SelectStatement(List.of("orders", "customers"), List.of(), List.of())));
    }

    @Test
    void secondJoinPredicateForOneTableIsRejected(@TempDir Path dir) {
        Planner planner = new Planner(testbed(dir, 1000));

        // cities joins customers twice over: c_city = ct_city and c_name = ct_country
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> planner.plan(
                new SelectStatement(
                        List.of("orders", "customers", "cities"),
                        List.of(),
                        List.of(ORDERS_CUSTOMERS, CUSTOMERS_CITIES, new JoinPredicate("c_name", "ct_country")))));
        assertTrue(e.getMessage().contains("only one join predicate per table"), e.getMessage());
    }

    private static ScanOperator scanOf(Operator operator) {
        return assertInstanceOf(ScanOperator.class, operator);
    }

    private static void assertKeys(HashJoinOperator join, String buildColumn, String probeColumn) {
        assertEquals(buildColumn, join.build().schema().get(join.buildKey()).name());
        assertEquals(probeColumn, join.probe().schema().get(join.probeKey()).name());
    }

    private static void assertStats(Plan.TableScan scan, String table, int total, int read, int pruned) {
        assertEquals(table, scan.tableName());
        assertEquals(total, scan.stats().partitionsTotal());
        assertEquals(read, scan.stats().partitionsRead());
        assertEquals(pruned, scan.stats().partitionsPruned());
    }

    /** The four testbed tables, loaded with the given number of rows per partition. */
    static StorageEngine testbed(Path dir, int maxRowsPerPartition) {
        StorageEngine engine = BinderIT.createTestbedTables(new StorageEngine(dir, maxRowsPerPartition));
        Path data = Path.of("src", "test", "resources", "joins");
        for (String table : List.of("orders", "products", "customers", "cities")) {
            engine.copyFile(table, data.resolve(table + ".csv").toString());
        }
        return engine;
    }
}
