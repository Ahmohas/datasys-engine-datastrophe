package dk.itu.datasys;

import java.util.List;

final class CsvParser {

    private CsvParser() {
    }

    static Object[] parseLine(
            String line,
            List<ColumnSpec> columns,
            String fileName,
            int lineNumber) {

        String[] fields = line.split(",", -1);

        if (fields.length != columns.size()) {
            throw new IllegalArgumentException(
                    fileName + ":" + lineNumber
                            + ": expected " + columns.size()
                            + " fields but found " + fields.length);
        }

        Object[] values = new Object[columns.size()];

        for (int i = 0; i < columns.size(); i++) {
            values[i] = parseValue(
                    fields[i],
                    columns.get(i),
                    fileName,
                    lineNumber);
        }

        return values;
    }

    private static Object parseValue(
            String value,
            ColumnSpec column,
            String fileName,
            int lineNumber) {

        return switch (column.type()) {

            case STRING -> {
                if (!value.chars().allMatch(c -> c < 128)) {
                    throw new IllegalArgumentException(
                            fileName + ":" + lineNumber
                                    + ": non-ASCII value for column "
                                    + column.name());
                }

                yield value;
            }

            case LONG -> {
                try {
                    yield Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            fileName + ":" + lineNumber
                                    + ": invalid LONG value for column "
                                    + column.name() + ": " + value,
                            e);
                }
            }

            case DOUBLE -> {
                try {
                    yield Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            fileName + ":" + lineNumber
                                    + ": invalid DOUBLE value for column "
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