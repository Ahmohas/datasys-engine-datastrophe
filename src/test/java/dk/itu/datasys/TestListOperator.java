package dk.itu.datasys;

import java.util.List;

/** Test stub: serves a fixed list of rows, and records how it was opened and closed. */
final class TestListOperator implements Operator {

    private final List<ColumnSpec> schema;
    private final List<Object[]> rows;
    private int position;
    boolean opened;
    boolean closed;

    TestListOperator(List<ColumnSpec> schema, List<Object[]> rows) {
        this.schema = schema;
        this.rows = rows;
    }

    @Override
    public void open() {
        position = 0;
        opened = true;
    }

    @Override
    public Object[] next() {
        return position < rows.size() ? rows.get(position++) : null;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public List<ColumnSpec> schema() {
        return schema;
    }
}
