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

    /** Plans against the table's current version. */
    public Plan plan(SelectStatement select) {
        return plan(select, storage.snapshot(select.tableName()));
    }

    /** Plans against a pinned version; the table is not looked up again. */
    public Plan plan(SelectStatement select, TableMetadata table) {
        String tableName = select.tableName();
        ScanStats stats = new ScanStats(table.partitions().size());

        if (select.where().isEmpty()) {
            for (PartitionMetadata partition : table.partitions()) {
                stats.recordRead();
                LOGGER.debug(
                        "operation=plan partition={} decision=READ",
                        partition.file);
            }
            logStats(tableName, table, stats);
            return new Plan(new ScanOperator(storage, tableName, table.columns(), table.partitions()), stats);
        }

        Predicate predicate = select.where().get();
        int columnIndex = columnIndex(table, predicate.columnName());
        ColumnType type = table.columns().get(columnIndex).type();

        List<PartitionMetadata> surviving = new ArrayList<>();
        for (PartitionMetadata partition : table.partitions()) {
            LOGGER.debug(
                    "operation=plan partition={} column={} min={} max={}",
                    partition.file,
                    predicate.columnName(),
                    LogText.safe(partition.minValues.get(columnIndex)),
                    LogText.safe(partition.maxValues.get(columnIndex)));

            if (Pruning.mayContainMatch(type, partition, columnIndex, predicate)) {
                stats.recordRead();
                surviving.add(partition);
                LOGGER.debug(
                        "operation=plan partition={} decision=READ",
                        partition.file);
            } else {
                stats.recordPruned();
                LOGGER.debug(
                        "operation=plan partition={} decision=PRUNED",
                        partition.file);
            }
        }

        logStats(tableName, table, stats);
        Operator scan = new ScanOperator(storage, tableName, table.columns(), surviving);
        return new Plan(new FilterOperator(scan, predicate), stats);
    }

    static int columnIndex(TableMetadata table, String columnName) {
        for (int i = 0; i < table.columns().size(); i++) {
            if (table.columns().get(i).name().equals(columnName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown column: " + columnName);
    }

    private static void logStats(String tableName, TableMetadata table, ScanStats stats) {
        LOGGER.debug(
                "operation=plan table={} version={} partitionsTotal={} partitionsRead={} partitionsPruned={}",
                tableName,
                table.version(),
                stats.partitionsTotal(),
                stats.partitionsRead(),
                stats.partitionsPruned());
    }
}
