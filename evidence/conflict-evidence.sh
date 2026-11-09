#!/usr/bin/env bash
# Exercise 9, Task 1 (and demo steps 1-2): produce one conflict and read it back through
# our own engine.
#
#   evidence/conflict-evidence.sh
#
# 1. Runs the two-writer test ConflictIT#sqlRaceLeavesOneCommitAndOneErrorLineOnTwoThreads:
#    a DELETE is parked before its commit, a COPY commits version 2, the DELETE aborts.
# 2. Copies the log lines that run appended to logs/engine.log into evidence/conflict-race.log.
# 3. Loads that snapshot into a `logs` table with ./engine and filters it with our own SELECTs:
#    the ERROR line, then everything each writer's thread logged.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
EVIDENCE="$ROOT/evidence"
cd "$ROOT"

[ -f target/engine.jar ] || mvn -q -DskipTests package

LOG=logs/engine.log
before=$( [ -f "$LOG" ] && wc -l < "$LOG" || echo 0 )

echo "== 1. The two-writer test" >&2
mvn -B test-compile failsafe:integration-test failsafe:verify \
    -Dit.test='ConflictIT#sqlRaceLeavesOneCommitAndOneErrorLineOnTwoThreads' \
    | grep -E "Tests run:|BUILD|ERROR" >&2

tail -n +"$((before + 1))" "$LOG" > "$EVIDENCE/conflict-race.log"
echo "== 2. $(wc -l < "$EVIDENCE/conflict-race.log") log lines captured in evidence/conflict-race.log" >&2

echo "== 3. The race, read back through our own engine" >&2
RUN="$EVIDENCE/run/logs-table"
rm -rf "$RUN"
mkdir -p "$RUN"
cd "$RUN"
if ! "$ROOT/engine" -c "CREATE TABLE logs (timestamp STRING, sessionId STRING, statementNumber LONG,
                                          threadId LONG, logLevel STRING, className STRING, logMessage STRING);
                       COPY logs FROM '../../conflict-race.log';" 2> ingest.err; then
    grep "^Error" ingest.err >&2
    exit 1
fi

query() {
    echo "-- $1"
    "$ROOT/engine" -c "$1" 2>/dev/null
    echo
}

errors=$("$ROOT/engine" -c "SELECT * FROM logs WHERE logLevel = 'ERROR';" 2>/dev/null)
loser_thread=$(echo "$errors" | cut -d, -f4)
winner_thread=$("$ROOT/engine" -c "SELECT * FROM logs WHERE className = 'Executor';" 2>/dev/null \
    | grep "statement=COPY table=trips .* version=2 " | cut -d, -f4)

{
    query "SELECT * FROM logs WHERE logLevel = 'ERROR';"
    echo "-- loser thread $loser_thread, winner thread $winner_thread"
    echo
    query "SELECT * FROM logs WHERE threadId = $loser_thread;"
    query "SELECT * FROM logs WHERE threadId = $winner_thread;"
} | tee "$EVIDENCE/conflict-queries.txt"
