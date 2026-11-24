package dk.itu.datasys;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public final class SqlPrinter {

    public String print(Statement statement) {
        return switch (statement) {
            case CreateTableStatement s -> printCreateTable(s);
            case CopyStatement s -> printCopy(s);
            case SelectStatement s -> printSelect(s);
            case DeleteStatement s -> printWhere("DELETE FROM " + s.tableName(), s.where());
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

    // Filters first, then joins: a fixed order, and reparsing sorts them back into the same lists
    private String printSelect(SelectStatement s) {
        List<String> conditions = new ArrayList<>();
        s.filters().forEach(filter -> conditions.add(printFilter(filter)));
        s.joins().forEach(join -> conditions.add(join.leftColumn() + " = " + join.rightColumn()));
        String base = "SELECT * FROM " + String.join(", ", s.tableNames());
        return conditions.isEmpty() ? base + ";" : base + " WHERE " + String.join(" AND ", conditions) + ";";
    }

    private String printWhere(String base, Optional<Predicate> where) {
        return where.isEmpty() ? base + ";" : base + " WHERE " + printFilter(where.get()) + ";";
    }

    private String printFilter(Predicate p) {
        String operator = switch (p.comparison()) {
            case EQUALS -> "=";
            case LESS_THAN -> "<";
            case GREATER_THAN -> ">";
        };
        return p.columnName() + " " + operator + " " + printLiteral(p.constant());
    }

        private String printLiteral(Object value) {
        if (value instanceof Double d) {
            if (Double.compare(d, -0.0) == 0) {
                return "-0.0";
            }
            String s = BigDecimal.valueOf(d).toPlainString();
            if (!s.contains(".")) {
                s = s + ".0";
            }
            return s;
        }
        if (value instanceof String s) {
            return "'" + s + "'";
        }
        return value.toString();
    }
}