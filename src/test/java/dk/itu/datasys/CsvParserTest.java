package dk.itu.datasys;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("city", ColumnType.STRING),
            new ColumnSpec("distance", ColumnType.LONG),
            new ColumnSpec("price", ColumnType.DOUBLE)
    );

    @Test
    void parsesTypedValues() {
        Object[] row =
                CsvParser.parseLine("Copenhagen,100,20.5", COLUMNS);

        assertEquals("Copenhagen", row[0]);
        assertEquals(100L, row[1]);
        assertEquals(20.5, row[2]);
    }

    @Test
    void rejectsWrongFieldCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CsvParser.parseLine("Copenhagen,100", COLUMNS)
        );
    }

    @Test
    void rejectsMalformedLong() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CsvParser.parseLine("Copenhagen,nope,20.5", COLUMNS)
        );
    }

    @Test
    void rejectsMalformedDouble() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CsvParser.parseLine("Copenhagen,100,nope", COLUMNS)
        );
    }

    @Test
    void rejectsNonAsciiString() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CsvParser.parseLine("København,100,20.5", COLUMNS)
        );
    }
}