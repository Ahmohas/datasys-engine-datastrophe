package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

class EngineIT {

    private static final String SETUP = """
            CREATE TABLE trips (city STRING, distance LONG, price DOUBLE);
            COPY trips FROM 'src/test/resources/trips.csv';
            """;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @Test
    void scriptPrintsSelectRowsAsCsvByteForByte(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("q.sql");
        Files.writeString(script, SETUP + """
                -- rows with a long trip
                SELECT * FROM trips WHERE distance > 100;
                select * from trips where city = 'Copenhagen';
                SELECT * FROM trips WHERE price < 50.0;
                """);

        int exitCode = run(tempDir, "-f", script.toString());

        assertEquals(0, exitCode, stderr());
        assertEquals("""
                Aarhus,187,301.0
                Copenhagen,140,210.0
                Aalborg,210,340.5
                Esbjerg,299,450.25
                Copenhagen,12,23.5
                Copenhagen,140,210.0
                Copenhagen,88,99.99
                Copenhagen,12,23.5
                Roskilde,31,45.0
                """, stdout());
        assertEquals("0", MDC.get("statementNumber"));
    }

    @Test
    void singleStatementArgumentRunsAgainstExistingData(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("setup.sql");
        Files.writeString(script, SETUP);
        assertEquals(0, run(tempDir, "-f", script.toString()), stderr());
        assertEquals("", stdout());

        int exitCode = run(tempDir, "-c", "SELECT * FROM trips WHERE distance = 95");

        assertEquals(1, exitCode, "missing ';' must be a syntax error");
        stdout.reset();
        stderr.reset();

        exitCode = run(tempDir, "-c", "SELECT * FROM trips WHERE distance = 95;");

        assertEquals(0, exitCode, stderr());
        assertEquals("Odense,95,120.75\n", stdout());
    }

    @Test
    void failingScriptReportsOnStderrAndLeavesStdoutClean(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("bad.sql");
        Files.writeString(script, "SELECT * FROM missing;\n");

        int exitCode = run(tempDir, "-f", script.toString());

        assertEquals(1, exitCode);
        assertEquals("", stdout());
        assertTrue(stderr().contains("missing"), stderr());
        assertEquals("0", MDC.get("statementNumber"));
    }

    @Test
    void syntaxErrorReportsPositionAndExecutesNothing(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("bad.sql");
        Files.writeString(script, SETUP + "SELECT * FROM trips;\nSELECT * trips;\n");

        int exitCode = run(tempDir, "-f", script.toString());

        assertEquals(1, exitCode);
        assertEquals("", stdout());
        assertTrue(stderr().contains("line 4, column 9"), stderr());
    }

    @Test
    void noArgumentsPrintsTeamNameAndUsage(@TempDir Path tempDir) {
        assertEquals(0, run(tempDir));
        assertTrue(stdout().contains("datastrophe"));
        assertTrue(stdout().contains("Usage"));
    }

    private int run(Path tempDir, String... args) {
        return Engine.run(
                args,
                tempDir.resolve("data"),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private String stdout() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return stderr.toString(StandardCharsets.UTF_8);
    }
}
