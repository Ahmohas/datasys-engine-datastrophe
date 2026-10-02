package dk.itu.datasys;

import java.io.PrintStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Runs SQL scripts: parse the whole script, then bind, plan and execute
 * statement by statement, stopping at the first error. SELECT rows are
 * written to the output stream as headerless CSV.
 */
public final class Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(Executor.class);

    private final StorageEngine storage;
    private final SqlParser parser = new SqlParser();
    private final Binder binder;
    private final Planner planner;
    private final PrintStream out;

    private int statementNumber;

    public Executor(StorageEngine storage, PrintStream out) {
        this.storage = storage;
        this.binder = new Binder(storage);
        this.planner = new Planner(storage);
        this.out = out;
    }

    public void executeScript(String sqlText) {
        // Script-level parsing is outside any statement, so it logs under statementNumber 0
        List<Statement> statements = parser.parse(sqlText);
        try {
            for (Statement statement : statements) {
                statementNumber++;
                MDC.put("statementNumber", String.valueOf(statementNumber));
                execute(statement);
            }
        } finally {
            MDC.put("statementNumber", "0");
            out.flush();
        }
    }

    private void execute(Statement statement) {
        long start = System.nanoTime();
        try {
            binder.bind(statement);
            switch (statement) {
                case CreateTableStatement s -> {
                    storage.createTable(s.tableName(), s.columns());
                    LOGGER.debug("statement=CREATE_TABLE table={} durationMs={}",
                            s.tableName(), elapsedMs(start));
                }
                case CopyStatement s -> {
                    storage.copyFile(s.tableName(), s.csvFilePath());
                    LOGGER.debug("statement=COPY table={} durationMs={}",
                            s.tableName(), elapsedMs(start));
                }
                case SelectStatement s -> {
                    long rowsOut = executeSelect(s);
                    LOGGER.debug("statement=SELECT table={} rowsOut={} durationMs={}",
                            s.tableName(), rowsOut, elapsedMs(start));
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("statement failed error={} durationMs={}", e.getMessage(), elapsedMs(start));
            throw e;
        }
    }

    private long executeSelect(SelectStatement select) {
        Operator root = planner.plan(select).root();
        long rows = 0;
        root.open();
        try {
            Object[] row;
            while ((row = root.next()) != null) {
                out.print(CsvWriter.format(row));
                rows++;
            }
        } finally {
            root.close();
        }
        return rows;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
