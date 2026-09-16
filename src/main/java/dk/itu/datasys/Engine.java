package dk.itu.datasys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class Engine {
    private static final Logger LOGGER = LoggerFactory.getLogger(Engine.class);

    public static void main(String[] args) throws Exception {
        MDC.put("sessionId", UUID.randomUUID().toString());
        MDC.put("statementNumber", "0");

        LOGGER.debug("engine started");

        Path dataDirectory = Files.createTempDirectory("datasys-engine-demo");
        Path csvFile = dataDirectory.resolve("trips.csv");

        Files.writeString(csvFile, """
                Copenhagen,12,23.5
                Aarhus,187,301.0
                Odense,95,120.75
                Copenhagen,140,210.0
                Aalborg,210,340.5
                Roskilde,31,45.0
                Copenhagen,88,99.99
                Esbjerg,299,450.25
                """);

        StorageEngine engine = new StorageEngine(dataDirectory);

        engine.createTable(
                "trips",
                List.of(
                        new ColumnSpec("city", ColumnType.STRING),
                        new ColumnSpec("distance", ColumnType.LONG),
                        new ColumnSpec("price", ColumnType.DOUBLE)
                )
        );

        engine.copyFile("trips", csvFile.toString());

        System.out.println("distance > 100:");
        printRows(engine.select(
                "trips",
                "distance",
                Comparison.GREATER_THAN,
                100L
        ));

        System.out.println("city = Copenhagen:");
        printRows(engine.select(
                "trips",
                "city",
                Comparison.EQUALS,
                "Copenhagen"
        ));

        System.out.println("price < 50.0:");
        printRows(engine.select(
                "trips",
                "price",
                Comparison.LESS_THAN,
                50.0
        ));

        LOGGER.debug("engine stopped");
    }

    private static void printRows(List<Object[]> rows) {
        for (Object[] row : rows) {
            System.out.println(String.join(
                    ",",
                    java.util.Arrays.stream(row)
                            .map(String::valueOf)
                            .toArray(String[]::new)
            ));
        }
    }

    String teamName() {
        return "datastrophe";
    }
}