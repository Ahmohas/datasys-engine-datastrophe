package dk.itu.datasys;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class StorageEngine {

    TableMetadata tableMetadata(String tableName) {
        return catalog.tables.get(tableName);
    }  

    private final Path dataDirectory;
    private final Path catalogPath;
    private final ObjectMapper objectMapper;
    private Catalog catalog;
    private ScanStats lastScanStats;
    private final int maxRowsPerPartition;
    private static final Logger LOGGER =
        LoggerFactory.getLogger(StorageEngine.class);

    public StorageEngine(Path dataDirectory) {
        this(dataDirectory, 1000);
}


    StorageEngine(Path dataDirectory, int maxRowsPerPartition) {
        MDC.put("sessionId", UUID.randomUUID().toString());
        MDC.put("statementNumber", "0");

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
                saveCatalog();
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to initialize storage engine", e);
        }
    }

    public void createTable(String tableName, List<ColumnSpec> columns) {
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

        TableMetadata table = new TableMetadata();
        table.columns.addAll(columns);

        catalog.tables.put(tableName, table);
        saveCatalog();
    }

    public List<ColumnSpec> schema(String tableName) {
        TableMetadata table = catalog.tables.get(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table does not exist: " + tableName);
        }
        return table.columns;
    }

    public void copyFile(String tableName, String csvFilePath) {
        LOGGER.debug(
                "operation=copyFile table={} file={}",
                tableName,
                csvFilePath);
        TableMetadata table = catalog.tables.get(tableName);

        if (table == null) {
            throw new IllegalArgumentException(
                    "Table does not exist: " + tableName);
        }
        if (!table.partitions.isEmpty()) {
            throw new UnsupportedOperationException(
                    "Appending to an existing table is not supported");
}
        Path csvPath = Path.of(csvFilePath);

        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException(
                    "CSV file does not exist: " + csvFilePath);
        }

        final int partitionSize = maxRowsPerPartition;
        List<Object[]> rows = new ArrayList<>();
        int partitionNumber = 0;
        int partitionsCreated = 0;

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
                                    table.columns,
                                    csvFilePath,
                                    lineNumber));

                if (rows.size() == partitionSize) {
                    PartitionMetadata metadata = writePartition(
                            tableName,
                            partitionNumber,
                            table.columns,
                            rows);
                    for (int column = 0; column < table.columns.size(); column++) {
                        LOGGER.debug(
                                "operation=copyFile table={} partition={} column={} min={} max={}",
                                tableName,
                                metadata.file,
                                table.columns.get(column).name(),
                                metadata.minValues.get(column),
                                metadata.maxValues.get(column));
                    }
                    table.partitions.add(metadata);
                    partitionsCreated++;

                    rows = new ArrayList<>();
                    partitionNumber++;
                }
            }

            if (!rows.isEmpty()) {
                PartitionMetadata metadata = writePartition(
                        tableName,
                        partitionNumber,
                        table.columns,
                        rows);
                for (int column = 0; column < table.columns.size(); column++) {
                    LOGGER.debug(
                            "operation=copyFile table={} partition={} column={} min={} max={}",
                            tableName,
                            metadata.file,
                            table.columns.get(column).name(),
                            metadata.minValues.get(column),
                            metadata.maxValues.get(column));
                }
                table.partitions.add(metadata);
                partitionsCreated++;
            }

            saveCatalog();
            
        LOGGER.debug(
                "operation=copyFile table={} partitionsCreated={}",
                tableName,
                partitionsCreated);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to copy CSV file: " + csvFilePath,
                    e);
        }
    }

    public List<Object[]> select(
            String tableName,
            String columnName,
            Comparison comparison,
            Object constant) {

        LOGGER.debug(
                "operation=select table={} column={} comparison={} constant={}",
                tableName,
                columnName,
                comparison,
                constant);

        TableMetadata table = catalog.tables.get(tableName);

        if (table == null) {
            throw new IllegalArgumentException(
                    "Table does not exist: " + tableName);
        }

        int columnIndex = findColumnIndex(table, columnName);
        ColumnSpec column = table.columns.get(columnIndex);

        validateConstant(column, constant);

        ScanStats stats = new ScanStats();
        lastScanStats = stats;

        LOGGER.debug(
                "operation=select stats=created table={} column={}",
                tableName,
                columnName);

        List<Object[]> results = new ArrayList<>();

        for (PartitionMetadata partition : table.partitions) {

            Object min = partition.minValues.get(columnIndex);
            Object max = partition.maxValues.get(columnIndex);

            LOGGER.debug(
                    "operation=select partition={} column={} min={} max={}",
                    partition.file,
                    columnName,
                    min,
                    max);

            if (Pruning.canPrune(
                    column.type(),
                    min,
                    max,
                    comparison,
                    constant)) {

                stats.recordPruned();

                LOGGER.debug(
                        "operation=select partition={} decision=prune",
                        partition.file);

                continue;
            }

            stats.recordRead();

            LOGGER.debug(
                    "operation=select partition={} decision=read",
                    partition.file);

            Path partitionPath =
                    dataDirectory.resolve(partition.file);

            try {
                List<Object[]> rows =
                        PartitionReader.read(
                                partitionPath,
                                table.columns);

                for (Object[] row : rows) {
                    if (matches(
                            row[columnIndex],
                            comparison,
                            constant,
                            column.type())) {

                        results.add(row);
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to read partition: "
                                + partition.file,
                        e);
            }
        }

        LOGGER.debug(
                "operation=select stats=used table={} partitionsRead={} partitionsPruned={}",
                tableName,
                stats.partitionsRead(),
                stats.partitionsPruned());

        return results;
    }

    private void saveCatalog() {
        try {
            objectMapper.writeValue(catalogPath.toFile(), catalog);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save catalog", e);
        }
    }

    private PartitionMetadata writePartition(
        String tableName,
        int partitionNumber,
        List<ColumnSpec> columns,
        List<Object[]> rows) throws IOException {

    String fileName =
            tableName + "-partition-" + partitionNumber + ".bin";

    Path partitionPath =
            dataDirectory.resolve(fileName);

    return PartitionWriter.write(
            partitionPath,
            columns,
            rows);
    }

    ScanStats getLastScanStats() {
        return lastScanStats;
    }

    private int findColumnIndex(
            TableMetadata table,
            String columnName) {

        for (int i = 0; i < table.columns.size(); i++) {
            if (table.columns.get(i).name().equals(columnName)) {
                return i;
            }
    }

    throw new IllegalArgumentException(
            "Unknown column: " + columnName);
    }

    private void validateConstant(
            ColumnSpec column,
            Object constant) {

        if (constant == null) {
            throw new IllegalArgumentException(
                    "Comparison constant cannot be null");
        }

        boolean valid = switch (column.type()) {
            case STRING -> constant instanceof String;
            case LONG -> constant instanceof Long;
            case DOUBLE -> constant instanceof Double;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Constant type does not match column type: "
                            + column.name());
        }
    }

    private void normalizeCatalogValues() {
        for (TableMetadata table : catalog.tables.values()) {
            for (PartitionMetadata partition : table.partitions) {
                for (int i = 0; i < table.columns.size(); i++) {
                    ColumnType type = table.columns.get(i).type();

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
        }
    }

    private Object normalizeValue(Object value, ColumnType type) {
        return switch (type) {
            case STRING -> (String) value;
            case LONG -> ((Number) value).longValue();
            case DOUBLE -> ((Number) value).doubleValue();
        };
    }

    private boolean matches(
            Object value,
            Comparison comparison,
            Object constant,
            ColumnType type) {

        int cmp = switch (type) {
            case STRING ->
                    ((String) value).compareTo((String) constant);

            case LONG ->
                    Long.compare((Long) value, (Long) constant);

            case DOUBLE ->
                    Double.compare((Double) value, (Double) constant);
        };

        return switch (comparison) {
            case EQUALS -> cmp == 0;
            case LESS_THAN -> cmp < 0;
            case GREATER_THAN -> cmp > 0;
        };
    }
}