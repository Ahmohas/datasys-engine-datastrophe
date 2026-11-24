# Joins design (Exercise 10)

## What a query may say

`SELECT * FROM t1, t2, ... WHERE p1 AND p2 ...`. Each predicate is either a **filter**
(a column compared with a constant: `=`, `<`, `>`) or a **join** (a column `=` a column).
No `OR`, no parentheses. `DELETE` keeps one table and at most one filter.

Column names are unqualified, so each name must occur in exactly one listed table. The binder
rejects, with an `IllegalArgumentException`:

- an unknown column, or a column found in two listed tables (ambiguous, as in DuckDB);
- a join whose two sides belong to the same table;
- a join between columns of different types. The hash join compares keys with `equals`, so a
  `LONG` 3 never equals a `DOUBLE` 3.0. DuckDB would coerce; we reject instead, which keeps our
  promise that every statement we accept runs in DuckDB;
- a table listed twice in `FROM` (that needs aliases, an optional extension we did not build).

`ColumnResolver` answers "which table owns this column, and where does it sit in the joined
row?" once, for the binder and the planner alike. The joined row is the table schemas
concatenated in `FROM`-list order.

## How a query is planned

1. **One pipeline per table**, as in Part 1: a `ScanOperator` over the partitions that survive
   pruning, under one `FilterOperator` per filter on that table. A partition is read only if
   every filter on its table may match it. Pruning statistics are kept per table
   (`Plan.tables()`).
2. **Left-deep fold in `FROM`-list order.** For each next table, find the join predicate that
   connects it to a table listed before it, and build
   `HashJoinOperator(joinedSoFar, nextTable, buildKey, probeKey)`: everything joined so far is
   the build side, the new table probes. `SELECT *` therefore emits columns in `FROM`-list order.
3. Each statement pins one catalog version per table at its start.

Two cases are rejected with a clear error:

- **No connecting predicate** for a table: that would be a cross product. The message asks to
  reorder the `FROM` list so that every table joins one listed before it.
- **More than one connecting predicate** for a table, for example `c_city = ct_city AND
  c_name = ct_country`. We support one join predicate per table and reject the second rather
  than silently use only one; applying it as a filter on the join output would be the next step.

## What the log shows

`HashJoinOperator.close()` logs `operator=HashJoin buildKey=… probeKey=… rowsBuild=… rowsProbe=…
rowsOut=…`. `rowsOut` is the size of each intermediate result, the number week 11 optimizes.
The `SELECT` summary line names every table and its pinned version:
`statement=SELECT table=orders+customers version=1+1 …`; a single-table line is unchanged.

## Testing against DuckDB

`src/test/resources/joins/joins.sql` is the testbed; run it from the repository root. Compare
sorted output, since neither engine promises a row order. Load the CSVs into DuckDB with
`(HEADER false)`: `customers.csv` has only `STRING` columns, so DuckDB's sniffer otherwise
takes its first line for a header and drops Anna, and the first three queries return 2 rows
instead of 4.
