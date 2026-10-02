package dk.itu.datasys;

/** An executable operator tree, plus the pruning statistics gathered while planning it. */
public record Plan(Operator root, ScanStats stats) { }
