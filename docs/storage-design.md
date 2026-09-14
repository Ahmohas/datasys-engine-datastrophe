# Storage Design

## 1. Catalog

We will store the catalog as a JSON file inside the data directory.

The catalog will contain each table's:
- Name and schema
- Data files/partitions
- Information needed to find those partitions

JSON was chosen because it is easy to read and inspect while developing.

## 2. Min/Max Metadata

Each partition will store min/max values for every column.

We will put this metadata in a **footer at the end of the partition file**. This allows us to calculate the values while writing the data and write the metadata afterwards.

The `select` operation can use these values to skip partitions that cannot contain a matching row.

## 3. Restart and Persistence

All data will be stored under the `dataDirectory` given to `StorageEngine`.

When a new `StorageEngine` is created using an existing directory, it will read the catalog and regain access to the existing tables and partitions.

## 4. Data Layout

We will use a **row-wise** binary format. Values belonging to the same row will be stored together in schema order.

We chose this because it keeps the implementation relatively simple and fits the `select` API, which returns complete rows.

## 5. Partitions

The maximum number of rows per partition will be configurable.

Our default will be **1,000 rows**, while tests can use smaller values such as 2 to make partitioning and pruning easier to test.

## 6. Binary Format

Each partition file will contain:

```text
Magic bytes + version
Row data
Min/max metadata footer
```

Values will be encoded as follows:

- `STRING`: length followed by ASCII bytes
- `LONG`: 8-byte integer
- `DOUBLE`: 8-byte IEEE 754 value

We will use **big-endian** byte order, matching Java's `ByteBuffer` default.