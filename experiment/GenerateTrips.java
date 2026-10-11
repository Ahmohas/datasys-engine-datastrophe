import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generates the synthetic trips CSV for the week 6 experiment.
 *
 * Usage: java experiment/GenerateTrips.java <rows> <seed> <sorted|shuffled> <out.csv>
 *
 * distance is uniform in [0, 100000), so "distance > 89999" selects about 10% of rows.
 * The same seed gives the same rows in both orders; only the row order differs.
 * java.util.Random is specified bit for bit, so the files are identical on every machine.
 */
public class GenerateTrips {

    private static final String[] CITIES = {
        "Copenhagen", "Aarhus", "Odense", "Aalborg", "Esbjerg", "Randers", "Kolding", "Roskilde"
    };

    record Trip(String city, long distance, double price) { }

    public static void main(String[] args) throws IOException {
        if (args.length != 4 || !(args[2].equals("sorted") || args[2].equals("shuffled"))) {
            System.err.println("usage: java GenerateTrips.java <rows> <seed> <sorted|shuffled> <out.csv>");
            System.exit(2);
        }
        int rows = Integer.parseInt(args[0]);
        long seed = Long.parseLong(args[1]);
        boolean sorted = args[2].equals("sorted");
        Path out = Path.of(args[3]);

        Random random = new Random(seed);
        List<Trip> trips = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            String city = CITIES[random.nextInt(CITIES.length)];
            long distance = random.nextInt(100_000);
            double price = Math.round((distance * 0.75 + random.nextInt(5_000)) * 100) / 100.0;
            trips.add(new Trip(city, distance, price));
        }
        if (sorted) {
            trips.sort(Comparator.comparingLong(Trip::distance)); // stable: ties keep generation order
        }

        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(out))) {
            for (Trip t : trips) {
                writer.printf(Locale.ROOT, "%s,%d,%.2f\n", t.city(), t.distance(), t.price());
            }
        }
    }
}
