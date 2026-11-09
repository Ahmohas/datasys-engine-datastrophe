package dk.itu.datasys;

/**
 * What a committed DELETE did. Rewritten partitions were replaced by a new partition (or
 * dropped, when every row matched); carried partitions were kept by reference.
 */
public record DeleteResult(long rowsDeleted, int partitionsRewritten, int partitionsCarried, long version) { }
