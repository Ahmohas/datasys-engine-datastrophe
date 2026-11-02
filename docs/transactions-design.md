# Transactions design: DELETE, copy-on-write and a versioned catalog

Exercise 7 adds `DELETE` and repeated `COPY` without ever modifying a partition in place.
This document answers the design questions before the implementation; the week 9 report
distills it.

Ground rule: **a partition file is immutable once written.** Every change to a table writes new
partition files and then commits a new partition list.

## Versioning

- **Where the counter lives.** In the table's catalog entry. `TableMetadata` becomes an immutable
  record: `columns`, `version`, `nextPartition`, `partitions`. `CREATE TABLE` creates version 0
  with no partitions. Every committed `COPY` or `DELETE` creates a new `TableMetadata` with
  `version + 1`.
- **What a version is.** One immutable `TableMetadata` object in memory, and its entry in the
  rewritten `data/catalog.json` on disk. The file holds only the latest committed version of each
  table; there is no file per version. We chose this because it keeps one source of truth on disk
  and builds directly on Part 1's catalog. The cost: older versions exist only in memory, while
  some statement still references them.
- **Not the in-memory-only variant.** Every commit is written to disk before it becomes visible, so a
  crash loses at most the statement that was running.

## Commit

A commit replaces a table's partition list and increments its version in one step:

1. All new partition files are written and closed first.
2. Under one lock (`StorageEngine.commit`, `synchronized`), the engine checks that the table's
   current version is still the version the statement started from. Week 8's write-write conflict
   detection builds on this check; in week 7 it can only fail through a programming error.
3. **On disk:** the whole catalog is written to `catalog.json.tmp` and then moved over
   `catalog.json` with `ATOMIC_MOVE`. A reader of the file sees either the old or the new catalog,
   never a mix. A crash before the move leaves the old catalog in place.
4. **In memory:** one reference assignment, `tables.put(name, next)` on a concurrent map, after the
   file is in place. A statement that looks the table up afterwards sees the new version; one that
   already pinned the old version keeps it.

Disk first, memory second: if writing the catalog fails, the in-memory state does not move ahead
of what is durable.

## Snapshot

- **What is pinned.** A reference to the immutable `TableMetadata` of the version. It already *is* a
  private copy: its partition list is unmodifiable, and the `PartitionMetadata` entries it points to
  are never changed after their partition is written. Nothing has to be copied.
- **When.** At statement start, before binding: the executor pins the version for a `SELECT`, and
  `StorageEngine.delete` and `copyFile` pin the version they start from as their first step. The
  planner and the scan receive the pinned version and never look the table up again.

## Retention

- **On disk:** only the latest version per table is in `catalog.json`. Partition files that only old
  versions reference stay on disk; Exercise 7 deletes no partition files (see *Orphans* below).
- **In memory:** an old version lives exactly as long as a running statement holds a reference to
  it. The JVM's garbage collector does the reference counting for us, so no version can disappear
  under a reader.
- **Asking for a version that is gone:** not possible. There is no API to ask for a version by
  number; a statement can only pin the current version and then keep its own reference.

## The rewrite set

- **Shared helper.** `Pruning.mayContainMatch(type, partition, columnIndex, predicate)` decides from the
  partition's min/max. The planner prunes exactly the partitions for which it returns false;
  `DELETE` rewrites exactly the partitions for which it returns true. Same code, opposite roles.
- **Candidate that turns out to have no match:** carried over by reference, not copied. Writing an
  identical copy would cost I/O and a new file for nothing. Such a partition is counted as carried.
- **Partition whose rows all match:** dropped from the new list; no empty partition is written.
- **`DELETE` without `WHERE`:** every partition is in the rewrite set and every row matches, so
  the new list is empty. The deleted row count comes from the catalog's row counts; no file is read.

## New version for a DELETE that matches nothing

Yes. Every committed `COPY` or `DELETE` increments the version, even when the partition list does
not change. It keeps one rule, version = number of committed changes, and the log line of every
statement shows a version it committed.

## Partition placement

New partitions go into **files of their own**, as in Part 1: `data/<table>-partition-<n>.bin`.
`n` comes from the table's `nextPartition` counter, which only grows, so a file name is never
reused while an old version could still reference it. Numbers are reserved in memory when a file is
written and persisted at commit. After a crash, numbers reserved by the lost statement can be
handed out again; that overwrites only orphaned files, which no version references.

Why own files: appending to one data file per table would need offsets in the catalog and a careful
rule for torn appends. One file per partition makes "never modify a partition" a property of the
file system, and readers never see a half-written partition through a committed catalog.

## Orphans

A crash after writing partition files but before the catalog move leaves files that no catalog
entry references. They are invisible and harmless but waste disk space. Superseded partitions,
those only older versions referenced, are the same kind of garbage once no statement holds those
versions. A cleanup would list `data/*-partition-*.bin`, subtract every file named in
`catalog.json`, and delete the rest. It is only safe while no engine process is running, so it
belongs in a separate maintenance command, not in the statement path.

## Logging

Every statement's summary line records the version it committed or read:

```
statement=CREATE_TABLE table=trips version=0 durationMs=3
statement=COPY table=trips rows=8 partitions=2 version=1 durationMs=12
statement=DELETE table=trips rowsDeleted=4 partitionsRewritten=1 partitionsCarried=3 version=2 durationMs=9
statement=SELECT table=trips rowsOut=4 partitionsRead=2 partitionsPruned=2 version=2 durationMs=3
```
