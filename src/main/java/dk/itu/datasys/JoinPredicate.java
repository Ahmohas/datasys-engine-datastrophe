package dk.itu.datasys;

/** A column compared with a column, {@code left = right}: an equi-join between two tables. */
public record JoinPredicate(String leftColumn, String rightColumn) { }
