package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VersioningIT {

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

    private static final String CSV = Path.of("src", "test", "resources", "trips.csv").toString();

    @Test
    void createCopyCopyDeleteProduceVersionsZeroToThree(@TempDir Path dir) {
        StorageEngine storage = new StorageEngine(dir);

        run(storage, "CREATE TABLE trips (city STRING, distance LONG, price DOUBLE);");
        assertEquals(0, storage.snapshot("trips").version());
        assertEquals("", run(storage, "SELECT * FROM trips;"));

        run(storage, "COPY trips FROM '" + CSV + "';");
        assertEquals(1, storage.snapshot("trips").version());
        assertEquals(TRIPS, run(storage, "SELECT * FROM trips;"));

        run(storage, "COPY trips FROM '" + CSV + "';");
        assertEquals(2, storage.snapshot("trips").version());
        assertEquals(TRIPS + TRIPS, run(storage, "SELECT * FROM trips;"));

        run(storage, "DELETE FROM trips WHERE city = 'Copenhagen';");
        assertEquals(3, storage.snapshot("trips").version());
        assertEquals(TRIPS_WITHOUT_COPENHAGEN + TRIPS_WITHOUT_COPENHAGEN, run(storage, "SELECT * FROM trips;"));
    }

    @Test
    void freshEngineSeesThePostDeleteVersion(@TempDir Path dir) throws Exception {
        StorageEngine before = new StorageEngine(dir);
        run(before, """
                CREATE TABLE trips (city STRING, distance LONG, price DOUBLE);
                COPY trips FROM '%s';
                COPY trips FROM '%s';
                DELETE FROM trips WHERE city = 'Copenhagen';
                """.formatted(CSV, CSV));

        StorageEngine after = new StorageEngine(dir);

        assertEquals(3, after.snapshot("trips").version());
        assertEquals(TRIPS_WITHOUT_COPENHAGEN + TRIPS_WITHOUT_COPENHAGEN, run(after, "SELECT * FROM trips;"));
        assertTrue(Files.readString(dir.resolve("catalog.json")).contains("\"version\" : 3"));
        assertTrue(Files.notExists(dir.resolve("catalog.json.tmp")));
    }

    @Test
    void pinnedVersionStillReadsItsRowsAfterADelete(@TempDir Path dir) {
        StorageEngine storage = new StorageEngine(dir);
        run(storage, """
                CREATE TABLE trips (city STRING, distance LONG, price DOUBLE);
                COPY trips FROM '%s';
                """.formatted(CSV));
        TableMetadata pinned = storage.snapshot("trips");

        storage.delete("trips", Optional.empty());

        assertEquals(2, storage.snapshot("trips").version());
        assertEquals(0, storage.snapshot("trips").partitions().size());
        // The old version is untouched: its partition files were never modified or removed
        Plan plan = new Planner(storage).plan(new SelectStatement("trips", Optional.empty()), pinned);
        assertEquals(8, FilterOperatorTest.drain(plan.root()).size());
    }

    @Test
    void deleteThatMatchesNothingStillCommitsAVersion(@TempDir Path dir) {
        StorageEngine storage = new StorageEngine(dir);
        run(storage, """
                CREATE TABLE trips (city STRING, distance LONG, price DOUBLE);
                COPY trips FROM '%s';
                DELETE FROM trips WHERE city = 'Vejle';
                """.formatted(CSV));

        assertEquals(2, storage.snapshot("trips").version());
        assertEquals(TRIPS, run(storage, "SELECT * FROM trips;"));
    }

    private static String run(StorageEngine storage, String sql) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Executor(storage, new PrintStream(out, true, StandardCharsets.UTF_8)).executeScript(sql);
        return out.toString(StandardCharsets.UTF_8);
    }
}
