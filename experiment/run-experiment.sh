#!/usr/bin/env bash
# Runs the week 6 sweep as specified in docs/experiment-design.md.
#
#   experiment/run-experiment.sh            # the designed experiment: sorted input
#   experiment/run-experiment.sh shuffled   # supplementary sweep: same rows, random order
#
# For every maxRowsPerPartition value, the table is loaded once into its own
# working directory (own data/ and logs/), then the query runs 1 + REPS times,
# each in a fresh JVM. Run 0 is the cold run; analyze.sh excludes it.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
EXP="$ROOT/experiment"

ROWS=100000
SEED=42
SIZES="2 8 32 128 512 1024"
REPS=5
THRESHOLD=89999   # distance is uniform in [0, 100000), so about 10% of rows qualify

[ -f "$ROOT/target/engine.jar" ] || (cd "$ROOT" && mvn -q -DskipTests package)

for order in "${@:-sorted}"; do
    mkdir -p "$EXP/runs/data"
    csv="$EXP/runs/data/trips-$order.csv"
    [ -f "$csv" ] || java "$EXP/GenerateTrips.java" "$ROWS" "$SEED" "$order" "$csv"

    for size in $SIZES; do
        dir="$EXP/runs/$order/s$size"
        rm -rf "$dir"
        mkdir -p "$dir"
        cd "$dir"
        export ENGINE_JAVA_OPTS="-Dengine.maxRowsPerPartition=$size"

        echo "$order maxRowsPerPartition=$size: load" >&2
        # Relative path so the JVM understands it on every OS, Git Bash included
        "$ROOT/engine" -c "CREATE TABLE trips (city STRING, distance LONG, price DOUBLE);
                           COPY trips FROM '../../data/trips-$order.csv';" 2>/dev/null

        for run in $(seq 0 "$REPS"); do
            echo "$order maxRowsPerPartition=$size: run $run" >&2
            "$ROOT/engine" -c "SELECT * FROM trips WHERE distance > $THRESHOLD;" >/dev/null 2>&1
        done
    done
done
