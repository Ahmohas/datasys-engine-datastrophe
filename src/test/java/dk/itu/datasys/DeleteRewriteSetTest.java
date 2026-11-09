package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeleteRewriteSetTest {

    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("city", ColumnType.STRING),
            new ColumnSpec("distance", ColumnType.LONG),
            new ColumnSpec("price", ColumnType.DOUBLE));

    // sorted-trips.csv with 2 rows per partition gives distance ranges
    // p0 [12, 31], p1 [88, 95], p2 [140, 187], p3 [210, 299]

    @Test
    void selectiveDeleteRewritesExactlyThePartitionsThatCanMatch(@TempDir Path dir) throws Exception {
        StorageEngine storage = sortedEngine(dir);
        TableMetadata before = storage.snapshot("trips");
        byte[] originalP1 = Files.readAllBytes(dir.resolve("trips-partition-1.bin"));

        DeleteResult result = storage.delete("trips", where("distance", Comparison.EQUALS, 88L));

        assertEquals(1, result.rowsDeleted());
        assertEquals(1, result.partitionsRewritten());
        assertEquals(3, result.partitionsCarried());
        assertEquals(2, result.version());

        TableMetadata after = storage.snapshot("trips");
        assertEquals(
                List.of("trips-partition-0.bin", "trips-partition-4.bin",
                        "trips-partition-2.bin", "trips-partition-3.bin"),
                files(after));
        // Carried partitions are the very same catalog entries: same file, row count and min/max
        assertSame(before.partitions().get(0), after.partitions().get(0));
        assertSame(before.partitions().get(2), after.partitions().get(2));
        assertSame(before.partitions().get(3), after.partitions().get(3));
        // The replacement holds the surviving row and is summarized like a COPY
        PartitionMetadata replacement = after.partitions().get(1);
        assertEquals(1, replacement.rowCount);
        assertEquals(95L, replacement.minValues.get(1));
        assertEquals(95L, replacement.maxValues.get(1));
        // The original is never modified
        assertArrayEquals(originalP1, Files.readAllBytes(dir.resolve("trips-partition-1.bin")));
    }

    @Test
    void partitionWhoseRowsAllMatchIsDropped(@TempDir Path dir) {
        StorageEngine storage = sortedEngine(dir);

        DeleteResult result = storage.delete("trips", where("distance", Comparison.LESS_THAN, 90L));

        // p0 [12, 31] is deleted entirely, p1 [88, 95] keeps Odense,95
        assertEquals(3, result.rowsDeleted());
        assertEquals(2, result.partitionsRewritten());
        assertEquals(2, result.partitionsCarried());
        assertEquals(
                List.of("trips-partition-4.bin", "trips-partition-2.bin", "trips-partition-3.bin"),
                files(storage.snapshot("trips")));
    }

    @Test
    void candidateWithoutAnActualMatchIsCarriedNotCopied(@TempDir Path dir) {
        StorageEngine storage = sortedEngine(dir);
        TableMetadata before = storage.snapshot("trips");

        // 90 lies inside p1's range [88, 95], but no row has distance 90
        DeleteResult result = storage.delete("trips", where("distance", Comparison.EQUALS, 90L));

        assertEquals(0, result.rowsDeleted());
        assertEquals(0, result.partitionsRewritten());
        assertEquals(4, result.partitionsCarried());
        assertEquals(2, result.version());
        assertEquals(before.partitions(), storage.snapshot("trips").partitions());
        assertTrue(Files.notExists(dir.resolve("trips-partition-4.bin")));
    }

    @Test
    void carriedPartitionsKeepTheirCatalogEntriesAcrossRestart(@TempDir Path dir) {
        StorageEngine storage = sortedEngine(dir);
        storage.delete("trips", where("distance", Comparison.EQUALS, 88L));
        TableMetadata committed = storage.snapshot("trips");

        TableMetadata reloaded = new StorageEngine(dir, 2).snapshot("trips");

        assertEquals(committed.version(), reloaded.version());
        assertEquals(files(committed), files(reloaded));
        for (int i = 0; i < committed.partitions().size(); i++) {
            PartitionMetadata expected = committed.partitions().get(i);
            PartitionMetadata actual = reloaded.partitions().get(i);
            assertEquals(expected.rowCount, actual.rowCount);
            assertEquals(expected.minValues, actual.minValues);
            assertEquals(expected.maxValues, actual.maxValues);
        }
    }

    @Test
    void deleteWithoutWhereEmptiesTheTableAndFileNamesAreNeverReused(@TempDir Path dir) {
        StorageEngine storage = sortedEngine(dir);

        DeleteResult result = storage.delete("trips", Optional.empty());

        assertEquals(8, result.rowsDeleted());
        assertEquals(4, result.partitionsRewritten());
        assertEquals(List.of(), storage.snapshot("trips").partitions());

        // A later COPY continues the numbering; old versions may still reference 0..3
        storage.copyFile("trips", Path.of("src", "test", "resources", "sorted-trips.csv").toString());
        assertEquals(
                List.of("trips-partition-4.bin", "trips-partition-5.bin",
                        "trips-partition-6.bin", "trips-partition-7.bin"),
                files(storage.snapshot("trips")));
    }

    private static Optional<Predicate> where(String column, Comparison comparison, Object constant) {
        return Optional.of(new Predicate(column, comparison, constant));
    }

    private static List<String> files(TableMetadata table) {
        return table.partitions().stream().map(p -> p.file).toList();
    }

    private static StorageEngine sortedEngine(Path dir) {
        StorageEngine engine = new StorageEngine(dir, 2);
        engine.createTable("trips", COLUMNS);
        engine.copyFile("trips", Path.of("src", "test", "resources", "sorted-trips.csv").toString());
        return engine;
    }
}
