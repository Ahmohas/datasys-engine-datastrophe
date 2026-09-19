package dk.itu.datasys;

import dk.itu.datasys.sql.SqlBaseVisitor;
import dk.itu.datasys.sql.SqlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqlAstBuilder extends SqlBaseVisitor<Object> {

    // Called once per CREATE TABLE statement in the parse tree
    @Override
    public Object visitCreateTable(SqlParser.CreateTableContext ctx) {
        String tableName = ctx.IDENTIFIER().getText();
        List<ColumnSpec> columns = buildColumnList(ctx.columnDef());
        return new CreateTableStatement(tableName, columns);
    }

    // Called once per COPY statement
    @Override
    public Object visitCopy(SqlParser.CopyContext ctx) {
        String tableName = ctx.IDENTIFIER().getText();
        String csvFilePath = unquote(ctx.STRING_LITERAL().getText());
        return new CopyStatement(tableName, csvFilePath);
    }

    // Called once per SELECT statement
    @Override
    public Object visitSelect(SqlParser.SelectContext ctx) {
        String tableName = ctx.IDENTIFIER().getText();
        Optional<Predicate> where = buildWhereClause(ctx);
        return new SelectStatement(tableName, where);
    }

    // Called for the WHERE column = / < / > value part, if present
    @Override
    public Object visitPredicate(SqlParser.PredicateContext ctx) {
        String columnName = ctx.IDENTIFIER().getText();
        Comparison comparison = parseComparison(ctx.comparison.getText());
        Object constant = visit(ctx.literal());
        return new Predicate(columnName, comparison, constant);
    }

    // Converts a literal token into its real Java type (String/Long/Double)
    @Override
    public Object visitLiteral(SqlParser.LiteralContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            return unquote(ctx.STRING_LITERAL().getText());
        }
        if (ctx.DOUBLE_LITERAL() != null) {
            return Double.parseDouble(ctx.DOUBLE_LITERAL().getText());
        }
        return Long.parseLong(ctx.LONG_LITERAL().getText());
    }

    // Turns each columnDef (e.g. "city STRING") into a ColumnSpec
    private List<ColumnSpec> buildColumnList(
            List<SqlParser.ColumnDefContext> columnDefs) {

        List<ColumnSpec> columns = new ArrayList<>();
        for (SqlParser.ColumnDefContext columnDef : columnDefs) {
            String columnName = columnDef.IDENTIFIER().getText();
            ColumnType type = parseColumnType(columnDef.columnType());
            columns.add(new ColumnSpec(columnName, type));
        }
        return columns;
    }

    // WHERE is optional in our grammar, so this can return empty
    private Optional<Predicate> buildWhereClause(SqlParser.SelectContext ctx) {
        if (ctx.predicate() == null) {
            return Optional.empty();
        }
        return Optional.of((Predicate) visit(ctx.predicate()));
    }

    // Maps the STRING/LONG/DOUBLE keyword token to our ColumnType enum
    private ColumnType parseColumnType(SqlParser.ColumnTypeContext ctx) {
        if (ctx.STRING() != null) {
            return ColumnType.STRING;
        }
        if (ctx.LONG() != null) {
            return ColumnType.LONG;
        }
        return ColumnType.DOUBLE;
    }

    // Maps the =, <, > token text to our Comparison enum
    private Comparison parseComparison(String text) {
        return switch (text) {
            case "=" -> Comparison.EQUALS;
            case "<" -> Comparison.LESS_THAN;
            case ">" -> Comparison.GREATER_THAN;
            default -> throw new IllegalStateException(
                    "Unknown comparison: " + text);
        };
    }

    // Strips the surrounding single quotes, e.g. 'trips.csv' -> trips.csv
    private String unquote(String quoted) {
        return quoted.substring(1, quoted.length() - 1);
    }
}