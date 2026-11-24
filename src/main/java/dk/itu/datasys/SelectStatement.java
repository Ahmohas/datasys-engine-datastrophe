package dk.itu.datasys;

import java.util.List;
import java.util.Optional;

/**
 * {@code SELECT * FROM t1, t2, ... WHERE p1 AND p2 ...}. The WHERE clause is split into
 * filters (a column compared with a constant) and joins (a column compared with a column).
 */
public record SelectStatement(List<String> tableNames, List<Predicate> filters, List<JoinPredicate> joins)
        implements Statement {

    public SelectStatement {
        tableNames = List.copyOf(tableNames);
        filters = List.copyOf(filters);
        joins = List.copyOf(joins);
    }

    /** The Part 1 shape: one table and at most one filter. */
    public SelectStatement(String tableName, Optional<Predicate> where) {
        this(List.of(tableName), where.stream().toList(), List.of());
    }
}
