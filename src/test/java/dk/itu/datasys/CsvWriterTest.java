package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Expected strings are what `duckdb -csv -noheader` prints for the same values. */
class CsvWriterTest {

    @Test
    void formatsEachTypeLikeDuckDb() {
        assertEquals("Copenhagen,12,23.5\n", CsvWriter.format(new Object[] {"Copenhagen", 12L, 23.5}));
        assertEquals("Aarhus,-187,301.0\n", CsvWriter.format(new Object[] {"Aarhus", -187L, 301.0}));
        assertEquals("x,0.0001,12345678.5\n", CsvWriter.format(new Object[] {"x", 0.0001, 12345678.5}));
    }

    @Test
    void leavesApostrophesTabsAndNonAsciiUnquoted() {
        assertEquals("O'Brien\n", CsvWriter.format(new Object[] {"O'Brien"}));
        assertEquals("a\tb\n", CsvWriter.format(new Object[] {"a\tb"}));
        assertEquals("café,日本語\n", CsvWriter.format(new Object[] {"café", "日本語"}));
    }

    @Test
    void quotesEmptyStringsSeparatorsQuotesAndLineBreaks() {
        assertEquals("\"\"\n", CsvWriter.format(new Object[] {""}));
        assertEquals("\"a,b\"\n", CsvWriter.format(new Object[] {"a,b"}));
        assertEquals("\"say \"\"hi\"\"\"\n", CsvWriter.format(new Object[] {"say \"hi\""}));
        assertEquals("\"a\nb\"\n", CsvWriter.format(new Object[] {"a\nb"}));
        assertEquals("\"a\rb\"\n", CsvWriter.format(new Object[] {"a\rb"}));
    }
}
