package dk.itu.datasys;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class Binder {

    private final StorageEngine engine;

    public Binder(StorageEngine engine) {
        this.engine = engine;
    }

    public void bind(Statement statement) {
        switch (statement) {
            case CreateTableStatement s -> bindCreateTable(s);
            case CopyStatement s -> bindCopy(s);
            case SelectStatement s -> bindSelect(s);
            case DeleteStatement s -> bindWhere(s.tableName(), s.where());
        }
    }

    private void bindCreateTable(CreateTableStatement s) {
        if (s.columns() == null || s.columns().isEmpty()) {
            throw new IllegalArgumentException("Column list cannot be empty");
        }
        for (int i = 0; i < s.columns().size(); i++) {
            for (int j = i + 1; j < s.columns().size(); j++) {
                if (s.columns().get(i).name().equals(s.columns().get(j).name())) {
                    throw new IllegalArgumentException(
                            "Duplicate column name: " + s.columns().get(i).name());
                }
            }
        }
    }

    private void bindCopy(CopyStatement s) {
        engine.schema(s.tableName());
    }

    // SELECT: every table exists once; every column name resolves to exactly one listed table;
    // filters compare with a constant of the column's type; joins connect two different tables
    // through columns of the same type (the hash join compares keys with equals())
    private void bindSelect(SelectStatement s) {
        if (s.tableNames().isEmpty()) {
            throw new IllegalArgumentException("FROM list cannot be empty");
        }
        Set<String> seen = new HashSet<>();
        List<List<ColumnSpec>> schemas = new ArrayList<>();
        for (String table : s.tableNames()) {
            if (!seen.add(table)) {
                throw new IllegalArgumentException("Table listed twice in FROM: " + table);
            }
            schemas.add(engine.schema(table));
        }
        ColumnResolver resolver = new ColumnResolver(s.tableNames(), schemas);

        for (Predicate filter : s.filters()) {
            ColumnResolver.Column column = resolver.resolve(filter.columnName());
            validateConstantType(new ColumnSpec(column.name(), column.type()), filter.constant());
        }
        for (JoinPredicate join : s.joins()) {
            ColumnResolver.Column left = resolver.resolve(join.leftColumn());
            ColumnResolver.Column right = resolver.resolve(join.rightColumn());
            if (left.table() == right.table()) {
                throw new IllegalArgumentException("Join " + left.name() + " = " + right.name()
                        + " compares two columns of table " + left.tableName()
                        + "; a join must connect two different tables");
            }
            if (left.type() != right.type()) {
                throw new IllegalArgumentException("Join " + left.name() + " = " + right.name()
                        + " compares " + left.type() + " with " + right.type()
                        + "; both sides must have the same type");
            }
        }
    }

    // DELETE: the table exists; a WHERE names an existing column with a constant of its type
    private void bindWhere(String tableName, Optional<Predicate> where) {
        List<ColumnSpec> columns = engine.schema(tableName);

        if (where.isPresent()) {
            Predicate predicate = where.get();
            ColumnSpec column = findColumn(columns, predicate.columnName());
            validateConstantType(column, predicate.constant());
        }
    }

    private ColumnSpec findColumn(List<ColumnSpec> columns, String columnName) {
        for (ColumnSpec column : columns) {
            if (column.name().equals(columnName)) {
                return column;
            }
        }
        throw new IllegalArgumentException("Unknown column: " + columnName);
    }

    private void validateConstantType(ColumnSpec column, Object constant) {
        boolean valid = switch (column.type()) {
            case STRING -> constant instanceof String;
            case LONG -> constant instanceof Long;
            case DOUBLE -> constant instanceof Double;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Constant type does not match column type: " + column.name());
        }
    }
}