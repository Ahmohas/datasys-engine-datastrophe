package dk.itu.datasys;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a bound SELECT into an operator tree.
 *
 * <p>First one pipeline per FROM-list table, as in Part 1: a scan over the partitions that
 * survive pruning, wrapped in a filter for each of that table's filters. Pruning happens
 * here, from the catalog's min/max summaries alone, before any data file is opened.
 *
 * <p>Then the pipelines are joined left to right in FROM-list order (left-deep): each new
 * table is the probe side of a hash join whose build side is everything joined so far. That
 * order is deliberately naive; week 11 turns it into an optimization problem.
 */
public final class Planner {

    private static final Logger LOGGER = LoggerFactory.getLogger(Planner.class);

    private final StorageEngine storage;

    public Planner(StorageEngine storage) {
        this.storage = storage;
    }

    /** Plans against each table's current version. */
    public Plan plan(SelectStatement select) {
        return plan(select, select.tableNames().stream().map(storage::snapshot).toList());
    }

    /** Plans a single-table SELECT against a pinned version. */
    public Plan plan(SelectStatement select, TableMetadata table) {
        return plan(select, List.of(table));
    }

    /**
     * Plans against pinned versions, one per FROM-list table and in FROM-list order; no
     * table is looked up again.
     */
    public Plan plan(SelectStatement select, List<TableMetadata> tables) {
        List<String> tableNames = select.tableNames();
        if (tables.size() != tableNames.size()) {
            throw new IllegalArgumentException("one pinned version per FROM-list table expected");
        }
        ColumnResolver resolver = new ColumnResolver(
                tableNames,
                tables.stream().map(TableMetadata::columns).toList());

        // One pipeline per table: scan, pruned by that table's filters, wrapped in its filters
        List<Operator> pipelines = new ArrayList<>();
        List<Plan.TableScan> scans = new ArrayList<>();
        for (int i = 0; i < tableNames.size(); i++) {
            final int table = i;
            List<Predicate> filters = select.filters().stream()
                    .filter(filter -> resolver.resolve(filter.columnName()).table() == table)
                    .toList();
            ScanStats stats = new ScanStats(tables.get(i).partitions().size());
            pipelines.add(scanTable(tableNames.get(i), tables.get(i), filters, stats));
            scans.add(new Plan.TableScan(tableNames.get(i), tables.get(i).version(), stats));
        }

        // Fold the FROM list left to right: joined-so-far builds, the next table probes
        Operator joined = pipelines.get(0);
        for (int i = 1; i < tableNames.size(); i++) {
            JoinPredicate join = connectingJoin(select, resolver, tableNames, i);
            ColumnResolver.Column left = resolver.resolve(join.leftColumn());
            ColumnResolver.Column right = resolver.resolve(join.rightColumn());
            ColumnResolver.Column built = left.table() < i ? left : right;
            ColumnResolver.Column probed = left.table() < i ? right : left;

            // The joined-so-far row is tables 0..i-1 concatenated, so the build side's key sits
            // at its position in the full joined row; the probe side's key at its own position
            int buildKey = built.index();
            int probeKey = probed.localIndex();
            LOGGER.debug(
                    "operation=plan join={}={} build={} probe={} buildKey={} probeKey={}",
                    built.name(),
                    probed.name(),
                    String.join("+", tableNames.subList(0, i)),
                    tableNames.get(i),
                    buildKey,
                    probeKey);
            joined = new HashJoinOperator(joined, pipelines.get(i), buildKey, probeKey);
        }
        return new Plan(joined, scans);
    }

    /**
     * The one join predicate that connects table {@code i} to the tables before it. No such
     * predicate would make a cross product; more than one is not supported (we reject it
     * rather than silently apply only one).
     */
    private static JoinPredicate connectingJoin(
            SelectStatement select,
            ColumnResolver resolver,
            List<String> tableNames,
            int i) {

        List<JoinPredicate> connecting = select.joins().stream()
                .filter(join -> {
                    int left = resolver.resolve(join.leftColumn()).table();
                    int right = resolver.resolve(join.rightColumn()).table();
                    return (left == i && right < i) || (right == i && left < i);
                })
                .toList();
        String earlier = String.join(", ", tableNames.subList(0, i));
        if (connecting.isEmpty()) {
            throw new IllegalArgumentException("No join predicate connects table " + tableNames.get(i)
                    + " to the tables before it (" + earlier + "); that would be a cross product."
                    + " Reorder the FROM list so that every table joins a table listed before it.");
        }
        if (connecting.size() > 1) {
            throw new IllegalArgumentException("Table " + tableNames.get(i) + " has "
                    + connecting.size() + " join predicates to the tables before it (" + earlier
                    + "); only one join predicate per table is supported.");
        }
        return connecting.get(0);
    }

    /** One table's pipeline: a scan over the surviving partitions, then one filter per predicate. */
    private Operator scanTable(String tableName, TableMetadata table, List<Predicate> filters, ScanStats stats) {
        List<PartitionMetadata> surviving = new ArrayList<>();
        for (PartitionMetadata partition : table.partitions()) {
            boolean mayMatch = true;
            for (Predicate filter : filters) {
                int columnIndex = columnIndex(table, filter.columnName());
                LOGGER.debug(
                        "operation=plan partition={} column={} min={} max={}",
                        partition.file,
                        filter.columnName(),
                        LogText.safe(partition.minValues.get(columnIndex)),
                        LogText.safe(partition.maxValues.get(columnIndex)));
                ColumnType type = table.columns().get(columnIndex).type();
                mayMatch = mayMatch && Pruning.mayContainMatch(type, partition, columnIndex, filter);
            }
            if (mayMatch) {
                stats.recordRead();
                surviving.add(partition);
                LOGGER.debug("operation=plan partition={} decision=READ", partition.file);
            } else {
                stats.recordPruned();
                LOGGER.debug("operation=plan partition={} decision=PRUNED", partition.file);
            }
        }
        logStats(tableName, table, stats);

        Operator pipeline = new ScanOperator(storage, tableName, table.columns(), surviving);
        for (Predicate filter : filters) {
            pipeline = new FilterOperator(pipeline, filter);
        }
        return pipeline;
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
