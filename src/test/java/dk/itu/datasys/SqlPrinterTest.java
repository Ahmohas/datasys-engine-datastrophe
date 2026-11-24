package dk.itu.datasys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlPrinterTest {

    private final SqlPrinter printer = new SqlPrinter();
    private final SqlParser parser = new SqlParser();

    @Test
    void roundTripsCreateTable() {
        assertRoundTrips(new CreateTableStatement(
                "trips",
                List.of(
                        new ColumnSpec("city", ColumnType.STRING),
                        new ColumnSpec("distance", ColumnType.LONG),
                        new ColumnSpec("price", ColumnType.DOUBLE))));
    }

    @Test
    void roundTripsCopy() {
        assertRoundTrips(new CopyStatement("trips", "trips.csv"));
    }

    @Test
    void roundTripsSelectWithoutWhere() {
        assertRoundTrips(new SelectStatement("trips", Optional.empty()));
    }

    @Test
    void roundTripsSelectWithWhereOnEachComparison() {
        assertRoundTrips(new SelectStatement(
                "trips",
                Optional.of(new Predicate("distance", Comparison.EQUALS, 100L))));
        assertRoundTrips(new SelectStatement(
                "trips",
                Optional.of(new Predicate("distance", Comparison.LESS_THAN, 100L))));
        assertRoundTrips(new SelectStatement(
                "trips",
                Optional.of(new Predicate("distance", Comparison.GREATER_THAN, 100L))));
    }

    @Test
    void roundTripsAllLiteralTypes() {
        assertRoundTrips(new SelectStatement(
                "trips",
                Optional.of(new Predicate("city", Comparison.EQUALS, "Odense"))));
        assertRoundTrips(new SelectStatement(
                "trips",
                Optional.of(new Predicate("price", Comparison.EQUALS, 23.5))));
        assertRoundTrips(new SelectStatement(
                "trips",
                Optional.of(new Predicate("distance", Comparison.EQUALS, -1L))));
        assertRoundTrips(new SelectStatement(
                "trips",
                Optional.of(new Predicate("price", Comparison.EQUALS, 0.0001))));
        assertRoundTrips(new SelectStatement(
                "trips",
                Optional.of(new Predicate("price", Comparison.EQUALS, 12345678.5))));
    }

    private void assertRoundTrips(Statement original) {
        String printed = printer.print(original);
        Statement reparsed = parser.parse(printed).get(0);
        assertEquals(original, reparsed);
    }

    @Test
    void roundTripsNegativeZero() {
        assertRoundTrips(new SelectStatement(
                "trips",
                Optional.of(new Predicate("price", Comparison.EQUALS, -0.0))));
    }

    @Test
    void roundTripsDelete() {
        assertRoundTrips(new DeleteStatement("trips", Optional.empty()));
        assertRoundTrips(new DeleteStatement(
                "trips",
                Optional.of(new Predicate("distance", Comparison.LESS_THAN, 100L))));
        assertRoundTrips(new DeleteStatement(
                "trips",
                Optional.of(new Predicate("city", Comparison.EQUALS, "Odense"))));
    }

    // ---- Exercise 10: table lists, filters and joins

    @Test
    void roundTripsTableList() {
        assertRoundTrips(new SelectStatement(List.of("orders", "customers"), List.of(), List.of()));
    }

    @Test
    void roundTripsFiltersAndJoins() {
        assertRoundTrips(new SelectStatement(
                List.of("orders", "customers", "products", "cities"),
                List.of(
                        new Predicate("c_city", Comparison.EQUALS, "Copenhagen"),
                        new Predicate("o_quantity", Comparison.GREATER_THAN, 1L),
                        new Predicate("p_price", Comparison.LESS_THAN, 500.0)),
                List.of(
                        new JoinPredicate("o_custname", "c_name"),
                        new JoinPredicate("o_prodname", "p_name"),
                        new JoinPredicate("c_city", "ct_city"))));
    }

    @Test
    void printsFiltersBeforeJoins() {
        SelectStatement select = new SelectStatement(
                List.of("orders", "customers"),
                List.of(new Predicate("c_city", Comparison.EQUALS, "Aarhus")),
                List.of(new JoinPredicate("o_custname", "c_name")));

        assertEquals(
                "SELECT * FROM orders, customers WHERE c_city = 'Aarhus' AND o_custname = c_name;",
                printer.print(select));
    }
}
