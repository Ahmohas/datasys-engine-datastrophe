package dk.itu.datasys;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pulls rows from its child and emits those that satisfy the predicate. */
public final class FilterOperator implements Operator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterOperator.class);

    private final Operator child;
    private final Predicate predicate;
    private final int columnIndex;
    private final ColumnType columnType;

    private long rowsIn;
    private long rowsOut;

    public FilterOperator(Operator child, Predicate predicate) {
        this.child = child;
        this.predicate = predicate;

        List<ColumnSpec> columns = child.schema();
        int index = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equals(predicate.columnName())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Unknown column: " + predicate.columnName());
        }
        this.columnIndex = index;
        this.columnType = columns.get(index).type();
    }

    @Override
    public void open() {
        rowsIn = 0;
        rowsOut = 0;
        child.open();
    }

    @Override
    public Object[] next() {
        Object[] row;
        while ((row = child.next()) != null) {
            rowsIn++;
            if (Pruning.matches(
                    columnType,
                    row[columnIndex],
                    predicate.comparison(),
                    predicate.constant())) {
                rowsOut++;
                return row;
            }
        }
        return null;
    }

    @Override
    public void close() {
        child.close();
        LOGGER.debug(
                "operator=Filter column={} comparison={} constant={} rowsIn={} rowsOut={}",
                predicate.columnName(),
                predicate.comparison(),
                predicate.constant(),
                rowsIn,
                rowsOut);
    }

    @Override
    public List<ColumnSpec> schema() {
        return child.schema();
    }

    public Operator child() {
        return child;
    }

    public Predicate predicate() {
        return predicate;
    }

    long rowsIn() {
        return rowsIn;
    }

    long rowsOut() {
        return rowsOut;
    }
}
