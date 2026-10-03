package dk.itu.datasys;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * The SQL front door. SELECT rows go to stdout as headerless CSV; logs and
 * errors go to stderr, so stdout can be diffed against DuckDB's output.
 */
public final class Engine {
    private static final Logger LOGGER = LoggerFactory.getLogger(Engine.class);

    static final Path DEFAULT_DATA_DIRECTORY = Path.of("data");

    private static final String USAGE = """
            Usage:
              ./engine                      prints team name and usage
              ./engine -c "<SQL statement>"  runs one statement
              ./engine -f <script.sql>       runs a script
            Data directory: data/ under the working directory.
            """;

    public static void main(String[] args) {
        MDC.put("sessionId", UUID.randomUUID().toString());
        MDC.put("statementNumber", "0");
        LOGGER.debug("engine started");

        int exitCode = run(args, DEFAULT_DATA_DIRECTORY, System.out, System.err);

        LOGGER.debug("engine stopped exitCode={}", exitCode);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /** Runs the front door against the given streams and returns the process exit code. */
    static int run(String[] args, Path dataDirectory, PrintStream out, PrintStream err) {
        String sqlText;
        if (args.length == 0) {
            out.println("Team: " + new Engine().teamName());
            out.print(USAGE);
            return 0;
        } else if (args.length == 2 && args[0].equals("-c")) {
            sqlText = args[1];
        } else if (args.length == 2 && args[0].equals("-f")) {
            try {
                sqlText = Files.readString(Path.of(args[1]));
            } catch (IOException e) {
                err.println("Error: cannot read script " + args[1] + ": " + e.getMessage());
                return 1;
            }
        } else {
            err.print(USAGE);
            return 2;
        }

        try {
            new Executor(new StorageEngine(dataDirectory), out).executeScript(sqlText);
            return 0;
        } catch (SqlParseException e) {
            err.println("Error: syntax error at line " + e.line() + ", column " + e.column()
                    + ": " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        } finally {
            out.flush();
        }
    }

    String teamName() {
        return "datastrophe";
    }
}
