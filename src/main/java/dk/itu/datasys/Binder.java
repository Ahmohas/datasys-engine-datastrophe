package dk.itu.datasys;

import java.util.List;

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

    private void bindSelect(SelectStatement s) {
        List<ColumnSpec> columns = engine.schema(s.tableName());

        if (s.where().isPresent()) {
            Predicate predicate = s.where().get();
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