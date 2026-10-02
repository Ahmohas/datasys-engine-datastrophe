package dk.itu.datasys;

import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads every row of the partitions it is handed, in order. It never sees
 * the predicate: which partitions to read is decided by the planner.
 */
public final class ScanOperator implements Operator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanOperator.class);

    private final StorageEngine storage;
    private final String tableName;
    private final List<PartitionMetadata> partitions;

    private int nextPartition;
    private Iterator<Object[]> currentRows;
    private long rowsOut;

    public ScanOperator(StorageEngine storage, String tableName, List<PartitionMetadata> partitions) {
        this.storage = storage;
        this.tableName = tableName;
        this.partitions = List.copyOf(partitions);
    }

    @Override
    public void open() {
        nextPartition = 0;
        currentRows = null;
        rowsOut = 0;
    }

    @Override
    public Object[] next() {
        // Partitions are loaded one at a time, only when the previous one is used up
        while (currentRows == null || !currentRows.hasNext()) {
            if (nextPartition >= partitions.size()) {
                return null;
            }
            currentRows = storage.readPartition(tableName, partitions.get(nextPartition++)).iterator();
        }
        rowsOut++;
        return currentRows.next();
    }

    @Override
    public void close() {
        currentRows = null;
        LOGGER.debug(
                "operator=Scan table={} partitions={} rowsOut={}",
                tableName,
                partitions.size(),
                rowsOut);
    }

    @Override
    public List<ColumnSpec> schema() {
        return storage.schema(tableName);
    }

    public String tableName() {
        return tableName;
    }

    public List<PartitionMetadata> partitions() {
        return partitions;
    }
}
