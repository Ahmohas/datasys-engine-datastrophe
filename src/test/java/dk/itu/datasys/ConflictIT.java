package dk.itu.datasys;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

/**
 * Conflict detection between concurrent writers. Every race is forced through the
 * {@code beforeCommit} hook with latches: no sleeps, and every wait has a timeout.
 */
class ConflictIT {

    private static final long TIMEOUT_SECONDS = 30;

    private static final String TRIPS_CSV = Path.of("src", "test", "resources", "trips.csv").toString();
    private static final String SORTED_CSV = Path.of("src", "test", "resources", "sorted-trips.csv").toString();

    private static final String TRIPS = """
            Copenhagen,12,23.5
            Aarhus,187,301.0
            Odense,95,120.75
            Copenhagen,140,210.0
            Aalborg,210,340.5
            Roskilde,31,45.0
            Copenhagen,88,99.99
            Esbjerg,299,450.25
            """;

    private static final String TRIPS_WITHOUT_COPENHAGEN = """
            Aarhus,187,301.0
            Odense,95,120.75
            Aalborg,210,340.5
            Roskilde,31,45.0
            Esbjerg,299,450.25
            """;

    private static final String SORTED = """
            Copenhagen,12,23.5
            Roskilde,31,45.0
            Copenhagen,88,99.99
            Odense,95,120.75
            Copenhagen,140,210.0
            Aarhus,187,301.0
            Aalborg,210,340.5
            Esbjerg,299,450.25
            """;

    private static final String SORTED_WITHOUT_COPENHAGEN = """
            Roskilde,31,45.0
            Odense,95,120.75
            Aarhus,187,301.0
            Aalborg,210,340.5
            Esbjerg,299,450.25
            """;

    private static final Optional<Predicate> COPENHAGEN =
            Optional.of(new Predicate("city", Comparison.EQUALS, "Copenhagen"));

    // Required test 1: one statement runs inside another, no threads needed
    @Test
    void scanReturnsItsPinnedVersionWhileADeleteCommits(@TempDir Path dir) {
        StorageEngine storage = new StorageEngine(dir, 2);   // 4 partitions of 2 rows
        storage.createTable("trips", columns());
        storage.copyFile("trips", TRIPS_CSV);

        // Open the SELECT's plan: it pins version 1 and its partition list
        Operator scan = new Planner(storage).plan(new SelectStatement("trips", Optional.empty())).root();
        scan.open();
        StringBuilder rows = new StringBuilder(CsvWriter.format(scan.next()));

        // A whole DELETE commits version 2 while the scan is half-way through
        DeleteResult deleted = storage.delete("trips", COPENHAGEN);
        assertEquals(2, deleted.version());
        assertEquals(3, deleted.rowsDeleted());

        // Draining the scan still returns version 1's rows, Copenhagen included
        Object[] row;
        while ((row = scan.next()) != null) {
            rows.append(CsvWriter.format(row));
        }
        scan.close();
        assertEquals(TRIPS, rows.toString());

        // A new SELECT sees the DELETE
        assertEquals(TRIPS_WITHOUT_COPENHAGEN, select(storage));
    }

    // Required test 2: DELETE parked before its commit, COPY commits, the DELETE aborts
    @Test
    void parkedDeleteAbortsWhenACopyCommitsFirst(@TempDir Path dir) throws Exception {
        StorageEngine storage = tripsAtVersionOne(dir);

        ConflictException conflict = raceAndLose(
                storage,
                () -> storage.delete("trips", COPENHAGEN),
                () -> storage.copyFile("trips", SORTED_CSV));

        assertEquals(1, conflict.baseVersion());
        assertEquals(2, conflict.currentVersion());
    }

    // Required test 3: the same race between two COPY statements
    @Test
    void parkedCopyAbortsWhenAnotherCopyCommitsFirst(@TempDir Path dir) throws Exception {
        StorageEngine storage = tripsAtVersionOne(dir);

        ConflictException conflict = raceAndLose(
                storage,
                () -> storage.copyFile("trips", TRIPS_CSV),
                () -> storage.copyFile("trips", SORTED_CSV));

        assertEquals(1, conflict.baseVersion());
        assertEquals(2, conflict.currentVersion());
    }

    // Required test 4: after the abort the table is exactly the winner's, and a rerun succeeds
    @Test
    void abortedDeleteLeavesTheWinnersTableAndSucceedsWhenRerun(@TempDir Path dir) throws Exception {
        StorageEngine storage = tripsAtVersionOne(dir);

        raceAndLose(
                storage,
                () -> storage.delete("trips", COPENHAGEN),
                () -> storage.copyFile("trips", SORTED_CSV));

        // Only the winner's COPY is visible; nothing of the aborted DELETE is
        assertEquals(2, storage.snapshot("trips").version());
        assertEquals(TRIPS + SORTED, select(storage));

        // A clean failure can simply be retried: on the new base it commits
        DeleteResult rerun = storage.delete("trips", COPENHAGEN);
        assertEquals(3, rerun.version());
        assertEquals(6, rerun.rowsDeleted());
        assertEquals(TRIPS_WITHOUT_COPENHAGEN + SORTED_WITHOUT_COPENHAGEN, select(storage));
    }

    @Test
    void abortedCopyLeavesTheWinnersTableAndSucceedsWhenRerun(@TempDir Path dir) throws Exception {
        StorageEngine storage = tripsAtVersionOne(dir);

        raceAndLose(
                storage,
                () -> storage.copyFile("trips", TRIPS_CSV),
                () -> storage.copyFile("trips", SORTED_CSV));

        assertEquals(2, storage.snapshot("trips").version());
        assertEquals(TRIPS + SORTED, select(storage));

        CopyResult rerun = storage.copyFile("trips", TRIPS_CSV);
        assertEquals(3, rerun.version());
        assertEquals(TRIPS + SORTED + TRIPS, select(storage));
    }

    // Both writers run as SQL through an Executor, so engine.log tells the story of the race:
    // two threads, one version committed by the winner, one ERROR line for the loser
    @Test
    void sqlRaceLeavesOneCommitAndOneErrorLineOnTwoThreads(@TempDir Path dir) throws Exception {
        StorageEngine storage = tripsAtVersionOne(dir);
        PrintStream discard = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        Executor loserSql = new Executor(storage, discard);
        Executor winnerSql = new Executor(storage, discard);
        int linesBefore = logLines().size();

        ConflictException conflict = raceAndLose(
                storage,
                () -> {
                    // The loser's pool thread has no session yet; give it one, as Engine.main would
                    MDC.put("sessionId", UUID.randomUUID().toString());
                    try {
                        loserSql.executeScript("DELETE FROM trips WHERE city = 'Copenhagen';");
                    } finally {
                        MDC.remove("sessionId");
                    }
                    return null;
                },
                () -> winnerSql.executeScript("COPY trips FROM '" + SORTED_CSV + "';"));

        assertEquals("conflict table=trips base=1 current=2", conflict.getMessage());
        assertEquals(TRIPS + SORTED, select(storage));

        // Fields: timestamp, sessionId, statementNumber, threadId, logLevel, className, logMessage
        List<String[]> lines = logLines().subList(linesBefore, logLines().size()).stream()
                .map(line -> line.split(",", 7))
                .filter(fields -> fields.length == 7)
                .toList();

        List<String[]> errors = lines.stream().filter(f -> f[4].equals("ERROR")).toList();
        assertEquals(1, errors.size(), "exactly one ERROR line for the loser");
        assertEquals("conflict table=trips base=1 current=2", errors.get(0)[6]);

        List<String[]> commits = lines.stream()
                .filter(f -> f[6].startsWith("statement=COPY table=trips ") && f[6].contains(" version=2 "))
                .toList();
        assertEquals(1, commits.size(), "the winner's COPY commits version 2");

        assertTrue(lines.stream().noneMatch(f -> f[6].startsWith("statement=DELETE ")),
                "the loser's DELETE never commits");
        assertNotEquals(errors.get(0)[3], commits.get(0)[3], "winner and loser run on two threads");
    }

    /**
     * Runs {@code loser} on a second thread and parks it right before its commit, lets
     * {@code winner} commit on this thread, then releases the loser into validation.
     * Returns the loser's ConflictException; fails the test if the loser does not abort.
     */
    private static ConflictException raceAndLose(
            StorageEngine storage,
            Callable<?> loser,
            Runnable winner) throws Exception {

        CountDownLatch parked = new CountDownLatch(1);
        CountDownLatch winnerDone = new CountDownLatch(1);
        storage.beforeCommit = () -> {
            parked.countDown();
            await(winnerDone);
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> loserResult = executor.submit(loser);
            assertTrue(parked.await(TIMEOUT_SECONDS, SECONDS), "the loser never reached its commit");

            // The parked loser already holds the old hook; the winner must not park too
            storage.beforeCommit = () -> { };
            try {
                winner.run();
            } finally {
                winnerDone.countDown();   // release the loser even if the winner failed
            }

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> loserResult.get(TIMEOUT_SECONDS, SECONDS));
            return assertInstanceOf(ConflictException.class, failure.getCause());
        } finally {
            storage.beforeCommit = () -> { };
            executor.shutdownNow();
        }
    }

    // CountDownLatch.await throws a checked exception, which a Runnable hook cannot
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, SECONDS)) {
                throw new AssertionError("timed out waiting in beforeCommit");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while parked in beforeCommit", e);
        }
    }

    // Every line engine.log holds so far; tests compare before and after a run
    private static List<String> logLines() throws java.io.IOException {
        Path log = Path.of("logs", "engine.log");
        return Files.exists(log) ? Files.readAllLines(log, StandardCharsets.UTF_8) : List.of();
    }

    private static StorageEngine tripsAtVersionOne(Path dir) {
        StorageEngine storage = new StorageEngine(dir);
        storage.createTable("trips", columns());
        storage.copyFile("trips", TRIPS_CSV);
        assertEquals(1, storage.snapshot("trips").version());
        return storage;
    }

    private static List<ColumnSpec> columns() {
        return List.of(
                new ColumnSpec("city", ColumnType.STRING),
                new ColumnSpec("distance", ColumnType.LONG),
                new ColumnSpec("price", ColumnType.DOUBLE));
    }

    private static String select(StorageEngine storage) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Executor(storage, new PrintStream(out, true, StandardCharsets.UTF_8))
                .executeScript("SELECT * FROM trips;");
        return out.toString(StandardCharsets.UTF_8);
    }
}
