package dk.itu.datasys;

import java.util.ArrayList;
import java.util.List;

public class TableMetadata {

    public List<ColumnSpec> columns = new ArrayList<>();

    public List<PartitionMetadata> partitions = new ArrayList<>();
}