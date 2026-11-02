package dk.itu.datasys;

/** What a committed COPY did: rows loaded, partitions written, and the version it committed. */
public record CopyResult(long rows, int partitions, long version) { }
