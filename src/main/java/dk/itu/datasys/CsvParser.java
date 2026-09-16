package dk.itu.datasys;

import java.util.List;

final class CsvParser {

    private CsvParser() {
    }

    static Object[] parseLine(
            String line,
            List<ColumnSpec> columns) {

        String[] fields = line.split(",", -1);

        if (fields.length != columns.size()) {
            throw new IllegalArgumentException(
                    "Expected " + columns.size()
                            + " fields but found " + fields.length);
        }

        Object[] values = new Object[columns.size()];

        for (int i = 0; i < columns.size(); i++) {
            values[i] = parseValue(fields[i], columns.get(i));
        }

        return values;
    }

    private static Object parseValue(
            String value,
            ColumnSpec column) {

        return switch (column.type()) {
            case STRING -> parseString(value, column);

            case LONG -> {
                try {
                    yield Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid LONG value for column "
                                    + column.name() + ": " + value,
                            e);
                }
            }

            case DOUBLE -> {
                try {
                    yield Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid DOUBLE value for column "
                                    + column.name() + ": " + value,
                            e);
                }
            }
        };
    }

    private static String parseString(
            String value,
            ColumnSpec column) {

        if (!value.chars().allMatch(c -> c < 128)) {
            throw new IllegalArgumentException(
                    "Non-ASCII value for column "
                            + column.name());
        }

        return value;
    }
}