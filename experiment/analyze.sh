#!/usr/bin/env bash
# Extracts the measurements from the engine's own logs, using the engine itself:
# each run directory's log files are snapshotted into one file, COPYed into a
# `logs` table, and filtered with SELECT. Writes experiment/results/measurements.csv.
#
#   experiment/analyze.sh            # after run-experiment.sh
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
EXP="$ROOT/experiment"
OUT="$EXP/results/measurements.csv"
mkdir -p "$EXP/results"
unset ENGINE_JAVA_OPTS

echo "order,maxRowsPerPartition,run,cold,sessionId,partitionsTotal,partitionsRead,partitionsPruned,fractionRead,rowsOut,durationMs" > "$OUT"

for dir in "$EXP"/runs/*/s*/; do
    dir=${dir%/}
    size=${dir##*/s}
    order=$(basename "$(dirname "$dir")")
    echo "analyzing $order maxRowsPerPartition=$size" >&2

    # Rotated files have higher indexes when newer; the live file is the newest
    cd "$dir"
    : > snapshot.log
    for i in $(seq 1 1000); do
        [ -f "logs/engine-$i.log" ] && cat "logs/engine-$i.log" >> snapshot.log
    done
    cat logs/engine.log >> snapshot.log

    rm -rf analysis
    mkdir analysis
    cd analysis
    ../../../../../engine -c "CREATE TABLE logs (timestamp STRING, sessionId STRING, statementNumber LONG,
                                                 threadId LONG, logLevel STRING, className STRING, logMessage STRING);
                              COPY logs FROM '../snapshot.log';" 2>/dev/null
    ../../../../../engine -c "SELECT * FROM logs WHERE className = 'Engine';"   2>/dev/null > engine.csv
    ../../../../../engine -c "SELECT * FROM logs WHERE className = 'Planner';"  2>/dev/null > planner.csv
    ../../../../../engine -c "SELECT * FROM logs WHERE className = 'Executor';" 2>/dev/null > executor.csv
    ../../../../../engine -c "SELECT * FROM logs WHERE logLevel = 'ERROR';"     2>/dev/null > errors.csv
    if [ -s errors.csv ]; then
        echo "error lines in $dir:" >&2
        cat errors.csv >&2
        exit 1
    fi

    # Join the per-session lines; sessions are ordered by their first timestamp,
    # so the first SELECT session of a directory is its cold run (run 0).
    awk -F, -v order="$order" -v size="$size" '
        function field(msg, key,   m) { return match(msg, key "=[^ ]*") ? substr(msg, RSTART + length(key) + 1, RLENGTH - length(key) - 1) : "" }
        FILENAME ~ /engine.csv$/   && $7 ~ /^engine started/ { if (field($7, "maxRowsPerPartition") != size) bad[$2] = 1 }
        FILENAME ~ /planner.csv$/  && $7 ~ /decision=READ$/   { readLines[$2]++ }
        FILENAME ~ /planner.csv$/  && $7 ~ /decision=PRUNED$/ { prunedLines[$2]++ }
        FILENAME ~ /planner.csv$/  && $7 ~ /partitionsTotal=/ { total[$2] = field($7, "partitionsTotal"); read[$2] = field($7, "partitionsRead"); pruned[$2] = field($7, "partitionsPruned") }
        FILENAME ~ /executor.csv$/ && $7 ~ /^statement=SELECT/ { order_of[++n] = $2; ts[$2] = $1; rows[$2] = field($7, "rowsOut"); ms[$2] = field($7, "durationMs") }
        END {
            for (i = 1; i <= n; i++) {
                s = order_of[i]
                if (s in bad) { print "session " s " ran with the wrong maxRowsPerPartition" > "/dev/stderr"; exit 1 }
                # The metric is defined on the decision= lines; the summary line must agree with them
                if (readLines[s] + 0 != read[s] || prunedLines[s] + 0 != pruned[s]) {
                    print "session " s ": decision lines disagree with the planner summary" > "/dev/stderr"; exit 1
                }
                printf "%s,%s,%d,%s,%s,%s,%s,%s,%.6f,%s,%s\n", order, size, i - 1, (i == 1 ? "true" : "false"), s,
                       total[s], read[s], pruned[s], read[s] / total[s], rows[s], ms[s]
            }
        }' engine.csv planner.csv executor.csv >> "$OUT"
done

echo "wrote $OUT" >&2
