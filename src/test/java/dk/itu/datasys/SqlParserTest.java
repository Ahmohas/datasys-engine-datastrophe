package dk.itu.datasys;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlParserTest {

    private final SqlParser parser = new SqlParser();

    @Test
    void parsesCreateTable() {
        List<Statement> statements = parser.parse(
                "CREATE TABLE trips (city STRING, distance LONG, price DOUBLE);");

        assertEquals(1, statements.size());
        assertEquals(
                new CreateTableStatement(
                        "trips",
                        List.of(
                                new ColumnSpec("city", ColumnType.STRING),
                                new ColumnSpec("distance", ColumnType.LONG),
                                new ColumnSpec("price", ColumnType.DOUBLE))),
                statements.get(0));
    }

    @Test
    void parsesCopy() {
        List<Statement> statements = parser.parse(
                "COPY trips FROM 'trips.csv';");

        assertEquals(1, statements.size());
        assertEquals(
                new CopyStatement("trips", "trips.csv"),
                statements.get(0));
    }

    @Test
    void parsesSelectWithoutWhere() {
        List<Statement> statements = parser.parse("SELECT * FROM trips;");

        assertEquals(1, statements.size());
        assertEquals(
                new SelectStatement("trips", Optional.empty()),
                statements.get(0));
    }

    @Test
    void parsesSelectWithWhere() {
        List<Statement> statements = parser.parse(
                "SELECT * FROM trips WHERE distance > 100;");

        assertEquals(1, statements.size());
        assertEquals(
                new SelectStatement(
                        "trips",
                        Optional.of(new Predicate(
                                "distance", Comparison.GREATER_THAN, 100L))),
                statements.get(0));
    }

    @Test
    void literalTypesAreCorrect() {
        Statement stmt = parser.parse(
                "SELECT * FROM trips WHERE distance = 12;").get(0);
        Predicate p = ((SelectStatement) stmt).where().get();
        assertEquals(12L, p.constant());
        assertTrue(p.constant() instanceof Long);

        stmt = parser.parse(
                "SELECT * FROM trips WHERE price = 12.0;").get(0);
        p = ((SelectStatement) stmt).where().get();
        assertEquals(12.0, p.constant());
        assertTrue(p.constant() instanceof Double);

        stmt = parser.parse(
                "SELECT * FROM trips WHERE city = '12';").get(0);
        p = ((SelectStatement) stmt).where().get();
        assertEquals("12", p.constant());
        assertTrue(p.constant() instanceof String);
    }

    @Test
    void negativeLiteralsAreParsed() {
        Statement stmt = parser.parse(
                "SELECT * FROM trips WHERE distance = -1;").get(0);
        Predicate p = ((SelectStatement) stmt).where().get();
        assertEquals(-1L, p.constant());

        stmt = parser.parse(
                "SELECT * FROM trips WHERE price = -1.5;").get(0);
        p = ((SelectStatement) stmt).where().get();
        assertEquals(-1.5, p.constant());
    }

    @Test
    void keywordsAreCaseInsensitiveAndIdentifierCasingIsPreserved() {
        List<Statement> statements = parser.parse("select * from Trips;");

        assertEquals(1, statements.size());
        assertEquals(
                new SelectStatement("Trips", Optional.empty()),
                statements.get(0));
    }

    @Test
    void commentsAndWhitespaceAreSkipped() {
        List<Statement> statements = parser.parse(
                """
                -- this is a comment
                SELECT * FROM trips;
                """);

        assertEquals(1, statements.size());
    }

    @Test
    void missingSemicolonReportsPosition() {
        SqlParseException e = assertThrows(
                SqlParseException.class,
                () -> parser.parse("SELECT * FROM trips"));
        assertEquals(1, e.line());
    }

    @Test
    void unbalancedParensReportsPosition() {
        assertThrows(
                SqlParseException.class,
                () -> parser.parse(
                        "CREATE TABLE trips (city STRING;"));
    }

    @Test
    void unknownTypeNameReportsPosition() {
        assertThrows(
                SqlParseException.class,
                () -> parser.parse(
                        "CREATE TABLE trips (city TEXT);"));
    }

    @Test
    void unterminatedStringLiteralReportsPosition() {
        assertThrows(
                SqlParseException.class,
                () -> parser.parse(
                        "COPY trips FROM 'trips.csv;"));
    }

    @Test
    void missingFromReportsPosition() {
        assertThrows(
                SqlParseException.class,
                () -> parser.parse("SELECT * trips;"));
    }
}