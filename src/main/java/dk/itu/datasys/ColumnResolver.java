package dk.itu.datasys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers, for the binder and the planner alike, "which FROM-list table owns this column,
 * and at which position does it sit in the joined row?". Column names are unqualified, so a
 * name must occur in exactly one listed table. The joined row is the table schemas
 * concatenated in FROM-list order.
 */
public final class ColumnResolver {

    /**
     * One resolved column.
     *
     * @param table      position of its table in the FROM list
     * @param index      position in the joined row (all schemas concatenated)
     * @param localIndex position in its own table's schema
     */
    public record Column(String name, int table, String tableName, int index, int localIndex, ColumnType type) { }

    private final Map<String, List<Column>> byName = new LinkedHashMap<>();
    private final List<Integer> offsets = new ArrayList<>();
    private final int width;

    public ColumnResolver(List<String> tableNames, List<List<ColumnSpec>> schemas) {
        if (tableNames.size() != schemas.size()) {
            throw new IllegalArgumentException("one schema per table expected");
        }
        int offset = 0;
        for (int table = 0; table < tableNames.size(); table++) {
            offsets.add(offset);
            List<ColumnSpec> schema = schemas.get(table);
            for (int local = 0; local < schema.size(); local++) {
                ColumnSpec spec = schema.get(local);
                byName.computeIfAbsent(spec.name(), name -> new ArrayList<>())
                        .add(new Column(spec.name(), table, tableNames.get(table), offset + local, local, spec.type()));
            }
            offset += schema.size();
        }
        this.width = offset;
    }

    /** Resolves a column name. Throws IllegalArgumentException if it is unknown or ambiguous. */
    public Column resolve(String columnName) {
        List<Column> candidates = byName.get(columnName);
        if (candidates == null) {
            throw new IllegalArgumentException("Unknown column: " + columnName);
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("Ambiguous column: " + columnName + " is in tables "
                    + String.join(" and ", candidates.stream().map(Column::tableName).toList()));
        }
        return candidates.get(0);
    }

    /** Position of the given table's first column in the joined row. */
    public int offset(int table) {
        return offsets.get(table);
    }

    /** Number of columns in the joined row. */
    public int width() {
        return width;
    }
}
