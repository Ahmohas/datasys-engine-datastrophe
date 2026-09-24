package dk.itu.datasys;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class PartitionWriter {

    private PartitionWriter() {
    }

    static PartitionMetadata write(
            Path partitionPath,
            List<ColumnSpec> columns,
            List<Object[]> rows) throws IOException {

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Cannot write empty partition");
        }

        int columnCount = columns.size();

        PartitionMetadata metadata = new PartitionMetadata();
        metadata.file = partitionPath.getFileName().toString();
        metadata.rowCount = rows.size();

        for (int column = 0; column < columnCount; column++) {
            Object firstValue = rows.get(0)[column];

            metadata.minValues.add(firstValue);
            metadata.maxValues.add(firstValue);
        }

        for (Object[] row : rows) {
            for (int column = 0; column < columnCount; column++) {
                Object value = row[column];

                if (compare(value, metadata.minValues.get(column), columns.get(column).type()) < 0) {
                    metadata.minValues.set(column, value);
                }

                if (compare(value, metadata.maxValues.get(column), columns.get(column).type()) > 0) {
                    metadata.maxValues.set(column, value);
                }
            }
        }

        try (DataOutputStream out =
                     new DataOutputStream(Files.newOutputStream(partitionPath))) {

            BinaryFormat.writeHeader(out, rows.size());

            for (Object[] row : rows) {
                for (int column = 0; column < columnCount; column++) {
                    BinaryFormat.writeValue(
                            out,
                            columns.get(column).type(),
                            row[column]
                    );
                }
            }

            writeFooter(out, columns, metadata);
        }

        return metadata;
    }

    private static void writeFooter(
            DataOutputStream out,
            List<ColumnSpec> columns,
            PartitionMetadata metadata) throws IOException {

        for (int column = 0; column < columns.size(); column++) {
            ColumnType type = columns.get(column).type();

            BinaryFormat.writeValue(
                    out,
                    type,
                    metadata.minValues.get(column)
            );

            BinaryFormat.writeValue(
                    out,
                    type,
                    metadata.maxValues.get(column)
            );
        }
    }

    private static int compare(
            Object left,
            Object right,
            ColumnType type) {

        return switch (type) {
            case STRING ->
                    ((String) left).compareTo((String) right);

            case LONG ->
                    Long.compare((Long) left, (Long) right);

            case DOUBLE ->
                    Double.compare((Double) left, (Double) right);
        };
    }
}