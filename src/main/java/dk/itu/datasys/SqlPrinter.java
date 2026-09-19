package dk.itu.datasys;

public final class SqlPrinter {

    public String print(Statement statement) {
        return switch (statement) {
            case CreateTableStatement s -> printCreateTable(s);
            case CopyStatement s -> printCopy(s);
            case SelectStatement s -> printSelect(s);
        };
    }

    private String printCreateTable(CreateTableStatement s) {
        StringBuilder columns = new StringBuilder();
        for (int i = 0; i < s.columns().size(); i++) {
            if (i > 0) {
                columns.append(", ");
            }
            ColumnSpec column = s.columns().get(i);
            columns.append(column.name())
                    .append(' ')
                    .append(column.type().name());
        }
        return "CREATE TABLE " + s.tableName() + " (" + columns + ");";
    }

    private String printCopy(CopyStatement s) {
        return "COPY " + s.tableName() + " FROM '" + s.csvFilePath() + "';";
    }

    private String printSelect(SelectStatement s) {
        String base = "SELECT * FROM " + s.tableName();
        if (s.where().isEmpty()) {
            return base + ";";
        }
        Predicate p = s.where().get();
        String operator = switch (p.comparison()) {
            case EQUALS -> "=";
            case LESS_THAN -> "<";
            case GREATER_THAN -> ">";
        };
        String constant = printLiteral(p.constant());
        return base + " WHERE " + p.columnName() + " " + operator + " " + constant + ";";
    }

    private String printLiteral(Object value) {
        if (value instanceof String s) {
            return "'" + s + "'";
        }
        return value.toString();
    }
}