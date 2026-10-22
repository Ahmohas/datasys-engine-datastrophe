# Week 6 experiment: partition size vs. pruning

Runs the experiment specified in [docs/experiment-design.md](../docs/experiment-design.md).

## Reproduce

```bash
mvn -q -DskipTests package                       # builds target/engine.jar
experiment/run-experiment.sh sorted shuffled     # data generation + sweep, about 15 min
experiment/analyze.sh                            # log -> results/measurements.csv, via the engine itself
java experiment/PlotResults.java                 # results/summary.csv and the two charts
```

- `GenerateTrips.java` writes 100,000 rows with seed 42; `distance` is uniform in [0, 100000).
  The sorted and shuffled files contain the same rows, only in a different order. The output is
  identical on every machine (`java.util.Random` is fully specified, lines end in `\n`).
- `run-experiment.sh` gives every `maxRowsPerPartition` value (2, 8, 32, 128, 512, 1024) its own
  working directory with its own `data/` and `logs/`. It loads the table once, then runs
  `SELECT * FROM trips WHERE distance > 89999;` 1 + 5 times, each time in a fresh JVM through
  `./engine`. The partition size is passed as `ENGINE_JAVA_OPTS=-Dengine.maxRowsPerPartition=N`.
- `analyze.sh` concatenates each directory's rotated log files into one snapshot, `COPY`s it into
  a `logs` table and filters it with the engine's own `SELECT ... WHERE className = ...` queries.
  It counts the planner's `decision=READ` and `decision=PRUNED` lines per session and checks them
  against the planner's summary line, checks that every session ran with the intended partition
  size, and fails if the log contains any `ERROR` line. Run 0 of every point is the cold run and is
  excluded from the summary.

## Conditions

| | |
|---|---|
| Commit measured | `70b9957` (engine code; the later commits only add analysis scripts and results) |
| Machine | Laptop, Intel Core i7-9750H @ 2.60 GHz (6 cores / 12 threads), 8 GB RAM |
| Disk | SanDisk SD8SNAT 256 GB SATA SSD (data and logs) |
| OS | Windows 11 Home, version 10.0.26200; engine started from Git Bash via `./engine` |
| JVM | Oracle JDK 26.0.2.1 (HotSpot 64-bit Server VM) |
| Heap | JVM default: max heap 2008 MB (`MaxHeapSize` = 1/4 of RAM), initial 8 MB |
| Logging | log4j2 at `debug`, CSV file appender plus console appender (console redirected to `/dev/null`) |

## Results

From [results/summary.csv](results/summary.csv); every point is the median of 5 warm runs. The query
returns 10,103 rows (10.1% of the table) in every run. The fraction of partitions read was identical
in all 6 runs of every point, so its spread is zero. Duration is the `durationMs` of the executor's
`statement=SELECT` line, and its min–max range is in the CSV and on the chart.

| maxRowsPerPartition | partitions | sorted: fraction read | sorted: median ms (min–max) | shuffled: fraction read | shuffled: median ms (min–max) |
|---:|---:|---:|---:|---:|---:|
| 2 | 50,000 | 0.10104 | 1,929 (1,859–2,118) | 0.19140 | 2,661 (2,434–2,977) |
| 8 | 12,500 | 0.10104 | 800 (782–865) | 0.57624 | 1,518 (1,472–2,017) |
| 32 | 3,125 | 0.10112 | 355 (340–411) | 0.96832 | 677 (656–722) |
| 128 | 782 | 0.10230 | 190 (180–196) | 1.00000 | 288 (281–315) |
| 512 | 196 | 0.10714 | 136 (128–142) | 1.00000 | 192 (172–360) |
| 1024 | 98 | 0.11225 | 108 (105–117) | 1.00000 | 149 (143–164) |

Charts: [results/fraction-read.png](results/fraction-read.png) (the designed chart) and
[results/duration.png](results/duration.png).

**Hypothesis check (designed experiment, sorted input):** the hypothesis predicted that going from
1024 to 2 rows per partition cuts the fraction of partitions read by at least half. Measured:
0.11225 → 0.10104, a reduction of 10.0%. The direction holds (the fraction never increases as
partitions shrink), but the predicted size of the effect does not, so the hypothesis as stated is
**refuted**.

Cold runs (run 0, excluded above) are far slower than warm runs at small partition sizes, for
example 32,033 ms vs. a 1,929 ms median for sorted input at size 2. Raw values are in
[results/measurements.csv](results/measurements.csv).

### Deviations from the design

- **JVM version:** the design says Java 25; the measurement machine has JDK 26. CI builds with 25.
- **Supplementary sweep:** the shuffled-input curve is **not** part of the designed experiment and
  was added after the design was written. It is reported separately and does not change the
  hypothesis or how it is judged.
