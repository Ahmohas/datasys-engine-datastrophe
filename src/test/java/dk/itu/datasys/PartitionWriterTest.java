package dk.itu.datasys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PartitionWriterTest {

    @Test
    void calculatesMinAndMax(@TempDir Path tempDir) throws Exception {
        Path partition = tempDir.resolve("partition-0.bin");

        List<ColumnSpec> columns = List.of(
                new ColumnSpec("city", ColumnType.STRING),
                new ColumnSpec("distance", ColumnType.LONG),
                new ColumnSpec("price", ColumnType.DOUBLE)
        );

        List<Object[]> rows = List.of(
                new Object[]{"Copenhagen", 100L, 20.5},
                new Object[]{"Aarhus", 50L, 30.0},
                new Object[]{"Odense", 75L, 10.5}
        );

        PartitionMetadata metadata =
                PartitionWriter.write(partition, columns, rows);

        assertTrue(Files.exists(partition));
        assertEquals(3, metadata.rowCount);

        assertEquals("Aarhus", metadata.minValues.get(0));
        assertEquals("Odense", metadata.maxValues.get(0));

        assertEquals(50L, metadata.minValues.get(1));
        assertEquals(100L, metadata.maxValues.get(1));

        assertEquals(10.5, metadata.minValues.get(2));
        assertEquals(30.0, metadata.maxValues.get(2));
    }
}