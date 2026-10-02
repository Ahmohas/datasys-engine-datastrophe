package dk.itu.datasys;

import java.math.BigDecimal;

/**
 * Formats rows as headerless CSV, matching `duckdb -csv -noheader` so that
 * DuckDB can serve as a differential oracle.
 */
final class CsvWriter {

    private CsvWriter() {
    }

    /** One row as a CSV line, terminated by '\n' on every platform. */
    static String format(Object[] row) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(formatValue(row[i]));
        }
        return line.append('\n').toString();
    }

    private static String formatValue(Object value) {
        return switch (value) {
            case String s -> needsQuotes(s) ? '"' + s.replace("\"", "\"\"") + '"' : s;
            case Double d -> formatDouble(d);
            default -> String.valueOf(value);
        };
    }

    // Plain notation, always with a decimal point: 301.0, not 301 or 3.01E2.
    // Known gap: DuckDB switches to scientific notation for very large or
    // small values (e.g. 1e20); we print those as plain decimals.
    private static String formatDouble(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return Double.toString(d);
        }
        String s = BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        return s.contains(".") ? s : s + ".0";
    }

    // DuckDB's CLI quotes empty strings and strings containing a comma, a double
    // quote or a line break; apostrophes, tabs and non-ASCII text stay unquoted
    private static boolean needsQuotes(String s) {
        if (s.isEmpty()) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') {
                return true;
            }
        }
        return false;
    }
}
