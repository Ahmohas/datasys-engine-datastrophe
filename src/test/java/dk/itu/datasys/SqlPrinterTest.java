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
    }

    private void assertRoundTrips(Statement original) {
        String printed = printer.print(original);
        Statement reparsed = parser.parse(printed).get(0);
        assertEquals(original, reparsed);
    }
}