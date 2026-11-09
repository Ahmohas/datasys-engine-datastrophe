package dk.itu.datasys;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Tables with a versioned catalog. Partition files are immutable once written: COPY and
 * DELETE write new partition files and then commit a new partition list with one atomic
 * swap (see docs/transactions-design.md).
 */
public final class StorageEngine {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(StorageEngine.class);

    private final Path dataDirectory;
    private final Path catalogPath;
    private final ObjectMapper objectMapper;
    private final Catalog catalog;
    private final int maxRowsPerPartition;

    // Partition numbers handed out but not necessarily committed yet, per table
    private final Map<String, Integer> reservedPartitions = new TreeMap<>();

    public StorageEngine(Path dataDirectory) {
        this(dataDirectory, 1000);
    }

    StorageEngine(Path dataDirectory, int maxRowsPerPartition) {
        // Engine.main owns the session; only start one here when used standalone (e.g. tests)
        if (MDC.get("sessionId") == null) {
            MDC.put("sessionId", UUID.randomUUID().toString());
            MDC.put("statementNumber", "0");
        }

        if (maxRowsPerPartition <= 0) {
            throw new IllegalArgumentException(
                    "maxRowsPerPartition must be positive");
        }

        this.dataDirectory = dataDirectory;
        this.catalogPath = dataDirectory.resolve("catalog.json");
        this.maxRowsPerPartition = maxRowsPerPartition;

        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        try {
            Files.createDirectories(dataDirectory);

            if (Files.exists(catalogPath)) {
                this.catalog = objectMapper.readValue(
                        catalogPath.toFile(),
                        Catalog.class);
                normalizeCatalogValues();
            } else {
                this.catalog = new Catalog();
                writeCatalog(catalog.tables);
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to initialize storage engine", e);
        }
    }

    /** The table's current committed version, or null if there is no such table. */
    TableMetadata tableMetadata(String tableName) {
        return catalog.tables.get(tableName);
    }

    /** Pins the table's current committed version. Throws IllegalArgumentException if unknown. */
    public TableMetadata snapshot(String tableName) {
        TableMetadata table = catalog.tables.get(tableName);
        if (table == null) {
            throw new IllegalArgumentException(
                    "Table does not exist: " + tableName);
        }
        return table;
    }

    /** The table's schema, in column order. Throws IllegalArgumentException if unknown. */
    public List<ColumnSpec> schema(String tableName) {
        return snapshot(tableName).columns();
    }

    /** Creates the table at version 0, with no partitions. */
    public synchronized TableMetadata createTable(String tableName, List<ColumnSpec> columns) {
        LOGGER.debug(
                "operation=createTable table={} columnCount={}",
                tableName,
                columns == null ? 0 : columns.size());
        if (catalog.tables.containsKey(tableName)) {
            throw new IllegalArgumentException(
                    "Table already exists: " + tableName
            );
        }

        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Column list cannot be empty"
            );
        }

        for (int i = 0; i < columns.size(); i++) {
            for (int j = i + 1; j < columns.size(); j++) {
                if (columns.get(i).name().equals(columns.get(j).name())) {
                    throw new IllegalArgumentException(
                            "Duplicate column name: " + columns.get(i).name()
                    );
                }
            }
        }

        TableMetadata table = new TableMetadata(columns, 0, 0, List.of());
        publish(tableName, table);
        return table;
    }

    /** Appends the CSV file's rows as new partitions and commits the next version. */
    public CopyResult copyFile(String tableName, String csvFilePath) {
        LOGGER.debug(
                "operation=copyFile table={} file={}",
                tableName,
                LogText.safe(csvFilePath));
        TableMetadata base = snapshot(tableName);

        Path csvPath = Path.of(csvFilePath);

        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException(
                    "CSV file does not exist: " + csvFilePath);
        }

        List<PartitionMetadata> written = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        long rowCount = 0;

        try (var reader = Files.newBufferedReader(csvPath)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isEmpty()) {
                    continue;
                }
                rows.add(
                        CsvParser.parseLine(
                                line,
                                base.columns(),
                                csvFilePath,
                                lineNumber));
                rowCount++;

                if (rows.size() == maxRowsPerPartition) {
                    written.add(writeNewPartition(tableName, base.columns(), rows, "copyFile"));
                    rows = new ArrayList<>();
                }
            }

            if (!rows.isEmpty()) {
                written.add(writeNewPartition(tableName, base.columns(), rows, "copyFile"));
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to copy CSV file: " + csvFilePath,
                    e);
        }

        List<PartitionMetadata> partitions = new ArrayList<>(base.partitions());
        partitions.addAll(written);
        TableMetadata committed = commit(tableName, base, partitions);

        LOGGER.debug(
                "operation=copyFile table={} partitionsCreated={} version={}",
                tableName,
                written.size(),
                committed.version());

        return new CopyResult(rowCount, written.size(), committed.version());
    }

    /**
     * Deletes the matching rows by copy-on-write: partitions that may contain a match are
     * rewritten without those rows, the others are carried over by reference. No partition
     * file is ever modified. Without a WHERE, every row matches.
     */
    public DeleteResult delete(String tableName, Optional<Predicate> where) {
        LOGGER.debug(
                "operation=delete table={} where={}",
                tableName,
                where.map(p -> p.columnName() + " " + p.comparison() + " " + LogText.safe(p.constant()))
                        .orElse("none"));
        TableMetadata base = snapshot(tableName);

        List<PartitionMetadata> partitions = new ArrayList<>();
        long rowsDeleted = 0;
        int rewritten = 0;
        int carried = 0;

        if (where.isEmpty()) {
            for (PartitionMetadata partition : base.partitions()) {
                rowsDeleted += partition.rowCount;
                rewritten++;
                LOGGER.debug("operation=delete partition={} decision=DROP", partition.file);
            }
        } else {
            Predicate predicate = where.get();
            int columnIndex = Planner.columnIndex(base, predicate.columnName());
            ColumnType type = base.columns().get(columnIndex).type();

            for (PartitionMetadata partition : base.partitions()) {
                // The planner's pruning helper, in the opposite role: may match = rewrite
                if (!Pruning.mayContainMatch(type, partition, columnIndex, predicate)) {
                    partitions.add(partition);
                    carried++;
                    LOGGER.debug("operation=delete partition={} decision=CARRY", partition.file);
                    continue;
                }

                List<Object[]> rows = readPartition(base.columns(), partition);
                List<Object[]> kept = new ArrayList<>();
                for (Object[] row : rows) {
                    if (!Pruning.matches(type, row[columnIndex], predicate.comparison(), predicate.constant())) {
                        kept.add(row);
                    }
                }

                if (kept.size() == rows.size()) {
                    // A candidate without an actual match: keep the original, write no copy
                    partitions.add(partition);
                    carried++;
                    LOGGER.debug("operation=delete partition={} decision=CARRY matches=0", partition.file);
                    continue;
                }

                rowsDeleted += rows.size() - kept.size();
                rewritten++;
                if (kept.isEmpty()) {
                    LOGGER.debug("operation=delete partition={} decision=DROP", partition.file);
                } else {
                    PartitionMetadata replacement = writeNewPartition(tableName, base.columns(), kept, "delete");
                    partitions.add(replacement);
                    LOGGER.debug(
                            "operation=delete partition={} decision=REWRITE replacement={} rowsKept={}",
                            partition.file,
                            replacement.file,
                            kept.size());
                }
            }
        }

        TableMetadata committed = commit(tableName, base, partitions);
        return new DeleteResult(rowsDeleted, rewritten, carried, committed.version());
    }

    /**
     * Reads all rows of one partition, decoded with the columns of the caller's pinned
     * version; used by ScanOperator and DELETE. Does not consult the catalog.
     */
    List<Object[]> readPartition(List<ColumnSpec> columns, PartitionMetadata partition) {
        try {
            return PartitionReader.read(
                    dataDirectory.resolve(partition.file),
                    columns);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to read partition: " + partition.file,
                    e);
        }
    }

    /**
     * Makes a new version of the table visible: the next version number and the new partition
     * list, written to disk atomically and then swapped in memory with one reference
     * assignment. Fails if another commit got in since the caller pinned {@code base}.
     */
    private synchronized TableMetadata commit(
            String tableName,
            TableMetadata base,
            List<PartitionMetadata> partitions) {

        TableMetadata current = snapshot(tableName);
        if (current.version() != base.version()) {
            throw new IllegalStateException(
                    "Concurrent commit on table " + tableName + ": started from version "
                            + base.version() + " but the current version is " + current.version());
        }
        int nextPartition = Math.max(current.nextPartition(), reservedPartitions.getOrDefault(tableName, 0));
        TableMetadata next = current.next(nextPartition, partitions);
        publish(tableName, next);
        return next;
    }

    // Disk first, then memory: the in-memory catalog never runs ahead of what is durable
    private synchronized void publish(String tableName, TableMetadata table) {
        Map<String, TableMetadata> tables = new TreeMap<>(catalog.tables);
        tables.put(tableName, table);
        writeCatalog(tables);
        catalog.tables.put(tableName, table);
    }

    /**
     * Writes the whole catalog to a temporary file, forces it to disk, and renames it over
     * catalog.json. Partition files were already forced when they were written, so a commit
     * that survives a power loss never points at data that did not reach the disk.
     */
    private void writeCatalog(Map<String, TableMetadata> tables) {
        Catalog snapshot = new Catalog();
        snapshot.tables.putAll(tables);
        Path temporary = catalogPath.resolveSibling("catalog.json.tmp");
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(objectMapper.writeValueAsBytes(snapshot)));
                channel.force(true);
            }
            Files.move(
                    temporary,
                    catalogPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(dataDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save catalog", e);
        }
    }

    /**
     * Makes the rename itself durable on POSIX file systems by syncing the directory entry.
     * Windows cannot open a directory as a channel; there NTFS journals the rename, and
     * this is a no-op.
     */
    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            // Not supported on this platform (Windows); see above
        }
    }

    /** Writes rows into a new partition file whose name no version has ever used. */
    private PartitionMetadata writeNewPartition(
            String tableName,
            List<ColumnSpec> columns,
            List<Object[]> rows,
            String operation) {

        String fileName = tableName + "-partition-" + reservePartitionNumber(tableName) + ".bin";
        try {
            PartitionMetadata metadata = PartitionWriter.write(
                    dataDirectory.resolve(fileName),
                    columns,
                    rows);
            for (int column = 0; column < columns.size(); column++) {
                LOGGER.debug(
                        "operation={} table={} partition={} column={} min={} max={}",
                        operation,
                        tableName,
                        metadata.file,
                        columns.get(column).name(),
                        LogText.safe(metadata.minValues.get(column)),
                        LogText.safe(metadata.maxValues.get(column)));
            }
            return metadata;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write partition: " + fileName, e);
        }
    }

    private synchronized int reservePartitionNumber(String tableName) {
        int committed = snapshot(tableName).nextPartition();
        int number = Math.max(committed, reservedPartitions.getOrDefault(tableName, 0));
        reservedPartitions.put(tableName, number + 1);
        return number;
    }

    private void normalizeCatalogValues() {
        for (Map.Entry<String, TableMetadata> entry : catalog.tables.entrySet()) {
            TableMetadata table = entry.getValue();
            for (PartitionMetadata partition : table.partitions()) {
                for (int i = 0; i < table.columns().size(); i++) {
                    ColumnType type = table.columns().get(i).type();

                    partition.minValues.set(
                            i,
                            normalizeValue(partition.minValues.get(i), type)
                    );

                    partition.maxValues.set(
                            i,
                            normalizeValue(partition.maxValues.get(i), type)
                    );
                }
            }
            // Part 1 catalogs have no counter; their partitions are numbered 0..n-1
            if (table.nextPartition() < table.partitions().size()) {
                catalog.tables.put(entry.getKey(), new TableMetadata(
                        table.columns(), table.version(), table.partitions().size(), table.partitions()));
            }
        }
    }

    private Object normalizeValue(Object value, ColumnType type) {
        return switch (type) {
            case STRING -> (String) value;
            case LONG -> ((Number) value).longValue();
            case DOUBLE -> ((Number) value).doubleValue();
        };
    }
}
