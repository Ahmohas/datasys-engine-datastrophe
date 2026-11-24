package dk.itu.datasys;

import java.util.List;

/** An executable operator tree, plus the pruning statistics gathered for each table while planning it. */
public record Plan(Operator root, List<Plan.TableScan> tables) {

    /** What planning decided for one FROM-list table: its pinned version and its pruning statistics. */
    public record TableScan(String tableName, long version, ScanStats stats) { }

    public Plan {
        tables = List.copyOf(tables);
    }

    /** Pruning statistics summed over all tables. */
    public ScanStats stats() {
        return ScanStats.sum(tables.stream().map(TableScan::stats).toList());
    }
}
