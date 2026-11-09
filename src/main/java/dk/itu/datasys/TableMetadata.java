package dk.itu.datasys;

import java.util.List;

/**
 * One committed version of a table. Immutable: a commit creates a new instance, so a
 * statement that pinned a version keeps seeing exactly that version.
 *
 * @param nextPartition the next partition file number; only grows, so file names are never reused
 */
public record TableMetadata(
        List<ColumnSpec> columns,
        long version,
        int nextPartition,
        List<PartitionMetadata> partitions) {

    public TableMetadata {
        columns = columns == null ? List.of() : List.copyOf(columns);
        partitions = partitions == null ? List.of() : List.copyOf(partitions);
    }

    /** The successor version with a new partition list. */
    TableMetadata next(int nextPartition, List<PartitionMetadata> partitions) {
        return new TableMetadata(columns, version + 1, nextPartition, partitions);
    }
}
