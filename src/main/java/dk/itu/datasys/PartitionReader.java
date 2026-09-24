package dk.itu.datasys;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PartitionReader {

    private PartitionReader() {
    }

    static List<Object[]> read(
            Path path,
            List<ColumnSpec> columns) throws IOException {

        List<Object[]> rows = new ArrayList<>();

        try (DataInputStream in =
                     new DataInputStream(
                             new BufferedInputStream(
                                     Files.newInputStream(path)))) {

            int rowCount = BinaryFormat.readHeader(in);

            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                Object[] values = new Object[columns.size()];

                for (int columnIndex = 0;
                     columnIndex < columns.size();
                     columnIndex++) {

                    values[columnIndex] =
                            BinaryFormat.readValue(
                                    in,
                                    columns.get(columnIndex).type());
                }

                rows.add(values);
            }
        }

        return rows;
    }
}