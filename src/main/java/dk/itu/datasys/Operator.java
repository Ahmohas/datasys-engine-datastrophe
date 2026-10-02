package dk.itu.datasys;

import java.util.List;

/** A Volcano-style iterator: open, pull rows with next() until null, close. */
public interface Operator {

    void open();

    /** One row in schema column order, or null when exhausted. */
    Object[] next();

    void close();

    /** The columns of the rows this operator produces, in order. */
    List<ColumnSpec> schema();
}
