package dk.itu.datasys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inner equi-join: builds a hash table from the build child, streams the probe child.
 * Each output row is the build row's columns followed by the probe row's columns. Rows
 * without a partner on the other side are dropped.
 */
public final class HashJoinOperator implements Operator {

    private static final Logger LOGGER = LoggerFactory.getLogger(HashJoinOperator.class);

    private final Operator build;
    private final Operator probe;
    private final int buildKey;
    private final int probeKey;
    private final List<ColumnSpec> schema;

    private Map<Object, List<Object[]>> table;
    private Object[] probeRow;            // the probe row being matched
    private List<Object[]> matches;       // its build partners
    private int nextMatch;                // cursor into matches, kept between calls

    private long rowsBuild;
    private long rowsProbe;
    private long rowsOut;

    public HashJoinOperator(Operator build, Operator probe, int buildKey, int probeKey) {
        this.build = build;
        this.probe = probe;
        this.buildKey = buildKey;
        this.probeKey = probeKey;
        List<ColumnSpec> columns = new ArrayList<>(build.schema());
        columns.addAll(probe.schema());
        this.schema = List.copyOf(columns);
    }

    @Override
    public void open() {
        rowsBuild = 0;
        rowsProbe = 0;
        rowsOut = 0;
        // Keys compare with equals(): the binder has made sure both sides have the same type
        table = new HashMap<>();
        build.open();
        Object[] row;
        while ((row = build.next()) != null) {
            rowsBuild++;
            table.computeIfAbsent(row[buildKey], key -> new ArrayList<>()).add(row);
        }
        probe.open();
        probeRow = null;
        matches = List.of();
        nextMatch = 0;
    }

    @Override
    public Object[] next() {
        while (nextMatch >= matches.size()) {
            probeRow = probe.next();
            if (probeRow == null) {
                return null;
            }
            rowsProbe++;
            matches = table.getOrDefault(probeRow[probeKey], List.of());
            nextMatch = 0;
        }
        Object[] buildRow = matches.get(nextMatch++);
        Object[] out = new Object[buildRow.length + probeRow.length];
        System.arraycopy(buildRow, 0, out, 0, buildRow.length);
        System.arraycopy(probeRow, 0, out, buildRow.length, probeRow.length);
        rowsOut++;
        return out;
    }

    @Override
    public void close() {
        probe.close();
        build.close();
        table = null;
        matches = List.of();
        LOGGER.debug(
                "operator=HashJoin buildKey={} probeKey={} rowsBuild={} rowsProbe={} rowsOut={}",
                schema.get(buildKey).name(),
                schema.get(build.schema().size() + probeKey).name(),
                rowsBuild,
                rowsProbe,
                rowsOut);
    }

    @Override
    public List<ColumnSpec> schema() {
        return schema;
    }

    public Operator build() {
        return build;
    }

    public Operator probe() {
        return probe;
    }

    public int buildKey() {
        return buildKey;
    }

    public int probeKey() {
        return probeKey;
    }
}
