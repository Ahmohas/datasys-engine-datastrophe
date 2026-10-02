package dk.itu.datasys;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a bound SELECT into an operator tree. Partition pruning happens here,
 * from the catalog's min/max summaries alone, before any data file is opened.
 */
public final class Planner {

    private static final Logger LOGGER = LoggerFactory.getLogger(Planner.class);

    private final StorageEngine storage;

    public Planner(StorageEngine storage) {
        this.storage = storage;
    }

    public Plan plan(SelectStatement select) {
        String tableName = select.tableName();
        TableMetadata table = storage.tableMetadata(tableName);
        if (table == null) {
            throw new IllegalArgumentException("Table does not exist: " + tableName);
        }

        ScanStats stats = new ScanStats(table.partitions.size());

        if (select.where().isEmpty()) {
            for (PartitionMetadata partition : table.partitions) {
                stats.recordRead();
                LOGGER.debug(
                        "operation=plan partition={} decision=READ",
                        partition.file);
            }
            logStats(tableName, stats);
            return new Plan(new ScanOperator(storage, tableName, table.partitions), stats);
        }

        Predicate predicate = select.where().get();
        int columnIndex = columnIndex(table, predicate.columnName());
        ColumnType type = table.columns.get(columnIndex).type();

        List<PartitionMetadata> surviving = new ArrayList<>();
        for (PartitionMetadata partition : table.partitions) {
            Object min = partition.minValues.get(columnIndex);
            Object max = partition.maxValues.get(columnIndex);

            LOGGER.debug(
                    "operation=plan partition={} column={} min={} max={}",
                    partition.file,
                    predicate.columnName(),
                    min,
                    max);

            if (Pruning.canPrune(type, min, max, predicate.comparison(), predicate.constant())) {
                stats.recordPruned();
                LOGGER.debug(
                        "operation=plan partition={} decision=PRUNED",
                        partition.file);
            } else {
                stats.recordRead();
                surviving.add(partition);
                LOGGER.debug(
                        "operation=plan partition={} decision=READ",
                        partition.file);
            }
        }

        logStats(tableName, stats);
        Operator scan = new ScanOperator(storage, tableName, surviving);
        return new Plan(new FilterOperator(scan, predicate), stats);
    }

    private static int columnIndex(TableMetadata table, String columnName) {
        for (int i = 0; i < table.columns.size(); i++) {
            if (table.columns.get(i).name().equals(columnName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown column: " + columnName);
    }

    private static void logStats(String tableName, ScanStats stats) {
        LOGGER.debug(
                "operation=plan table={} partitionsTotal={} partitionsRead={} partitionsPruned={}",
                tableName,
                stats.partitionsTotal(),
                stats.partitionsRead(),
                stats.partitionsPruned());
    }
}
