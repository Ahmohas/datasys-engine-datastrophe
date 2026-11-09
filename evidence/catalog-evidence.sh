#!/usr/bin/env bash
# Exercise 9, report section "Versioning": the catalog of one table before and after a DELETE.
#
#   evidence/catalog-evidence.sh
#
# Loads sorted-trips.csv with 2 rows per partition (version 1), saves catalog.json, runs
# DELETE FROM trips WHERE distance = 88 (version 2), and saves catalog.json again. Only the
# partition holding distance 88 is rewritten; the other three are carried over unchanged.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
EVIDENCE="$ROOT/evidence"
[ -f "$ROOT/target/engine.jar" ] || (cd "$ROOT" && mvn -q -DskipTests package)

RUN="$EVIDENCE/run/catalog"
rm -rf "$RUN"
mkdir -p "$RUN"
cd "$RUN"
export ENGINE_JAVA_OPTS="-Dengine.maxRowsPerPartition=2"

"$ROOT/engine" -c "CREATE TABLE trips (city STRING, distance LONG, price DOUBLE);
                   COPY trips FROM '../../../src/test/resources/sorted-trips.csv';" 2>/dev/null
cp data/catalog.json "$EVIDENCE/catalog-v1.json"

"$ROOT/engine" -c "DELETE FROM trips WHERE distance = 88;" 2>/dev/null
cp data/catalog.json "$EVIDENCE/catalog-v2.json"

{
    echo "-- files in data/ after the DELETE"
    ls data
    echo
    echo "-- summary and decision lines"
    grep -E "statement=|decision=" logs/engine.log | cut -d, -f3,7
} | tee "$EVIDENCE/catalog-delete.txt"
echo >&2
diff -u --label catalog-v1.json --label catalog-v2.json "$EVIDENCE/catalog-v1.json" "$EVIDENCE/catalog-v2.json" | tee "$EVIDENCE/catalog-v1-v2.diff" || true
