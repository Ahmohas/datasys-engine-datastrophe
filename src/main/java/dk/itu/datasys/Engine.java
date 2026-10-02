package dk.itu.datasys;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class Engine {
    private static final Logger LOGGER = LoggerFactory.getLogger(Engine.class);

    public static void main(String[] args) {
        MDC.put("statementNumber", "0");
        MDC.put("sessionId", UUID.randomUUID().toString());
        LOGGER.debug("engine started");

        String script = """
                CREATE TABLE trips (city STRING, distance LONG, price DOUBLE);
                COPY trips FROM 'trips.csv';
                SELECT * FROM trips WHERE distance > 100;
                SELECT * FROM trips;
                """;

        SqlParser parser = new SqlParser();
        SqlPrinter printer = new SqlPrinter();

        List<Statement> statements = parser.parse(script);
        for (Statement statement : statements) {
            System.out.println(printer.print(statement));
        }

        LOGGER.debug("engine stopped");
    }

    String teamName() {
        return "datastrophe";
    }
}