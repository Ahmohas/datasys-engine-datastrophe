import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import javax.imageio.ImageIO;

/**
 * Summarizes experiment/results/measurements.csv (warm runs only) into summary.csv
 * and draws the two charts as PNG.
 *
 * Usage: java experiment/PlotResults.java
 */
public class PlotResults {

    record Run(String order, int size, int run, boolean cold, long total, long read, double fraction, long durationMs) { }

    record Point(int size, int n, double median, double min, double max) { }

    static final Path RESULTS = Path.of("experiment", "results");
    static final Color SORTED = new Color(0x1f5fbf);
    static final Color SHUFFLED = new Color(0xd9661f);

    public static void main(String[] args) throws IOException {
        List<String> lines = Files.readAllLines(RESULTS.resolve("measurements.csv"));
        List<Run> runs = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split(",");
            runs.add(new Run(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Boolean.parseBoolean(f[3]),
                    Long.parseLong(f[5]), Long.parseLong(f[6]), Double.parseDouble(f[8]), Long.parseLong(f[10])));
        }

        Map<String, List<Point>> fraction = summarize(runs, Run::fraction);
        Map<String, List<Point>> duration = summarize(runs, r -> r.durationMs());

        StringBuilder csv = new StringBuilder(
                "order,maxRowsPerPartition,warmRuns,partitionsTotal,fractionReadMedian,fractionReadMin,fractionReadMax,"
                        + "durationMsMedian,durationMsMin,durationMsMax\n");
        for (String order : fraction.keySet()) {
            for (int i = 0; i < fraction.get(order).size(); i++) {
                Point f = fraction.get(order).get(i);
                Point d = duration.get(order).get(i);
                long total = runs.stream().filter(r -> r.order().equals(order) && r.size() == f.size())
                        .findFirst().orElseThrow().total();
                csv.append(String.format(Locale.ROOT, "%s,%d,%d,%d,%.5f,%.5f,%.5f,%.0f,%.0f,%.0f\n",
                        order, f.size(), f.n(), total, f.median(), f.min(), f.max(), d.median(), d.min(), d.max()));
            }
        }
        Files.writeString(RESULTS.resolve("summary.csv"), csv.toString());

        new Chart("Fraction of partitions read vs. partition size",
                "maxRowsPerPartition (rows, log scale)",
                "fraction of partitions read (partitionsRead / partitionsTotal)",
                false, 0, 1)
                .draw(fraction, RESULTS.resolve("fraction-read.png"));

        double lo = duration.values().stream().flatMap(List::stream).mapToDouble(Point::min).min().orElse(1);
        double hi = duration.values().stream().flatMap(List::stream).mapToDouble(Point::max).max().orElse(10);
        new Chart("SELECT duration vs. partition size",
                "maxRowsPerPartition (rows, log scale)",
                "statement durationMs (ms, log scale)",
                true, Math.pow(10, Math.floor(Math.log10(lo))), Math.pow(10, Math.ceil(Math.log10(hi))))
                .draw(duration, RESULTS.resolve("duration.png"));
    }

    /** Median, min and max over the warm runs, per input order and partition size. */
    static Map<String, List<Point>> summarize(List<Run> runs, ToDoubleFunction<Run> metric) {
        Map<String, TreeMap<Integer, List<Double>>> grouped = new TreeMap<>();
        for (Run r : runs) {
            if (!r.cold()) {
                grouped.computeIfAbsent(r.order(), k -> new TreeMap<>())
                        .computeIfAbsent(r.size(), k -> new ArrayList<>())
                        .add(metric.applyAsDouble(r));
            }
        }
        Map<String, List<Point>> result = new TreeMap<>();
        grouped.forEach((order, bySize) -> {
            List<Point> points = new ArrayList<>();
            bySize.forEach((size, values) -> {
                List<Double> v = values.stream().sorted().toList();
                int n = v.size();
                double median = n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2;
                points.add(new Point(size, n, median, v.get(0), v.get(n - 1)));
            });
            result.put(order, points);
        });
        return result;
    }

    /** A line chart with a log2 x axis, min-max whiskers and one curve per input order. */
    record Chart(String title, String xLabel, String yLabel, boolean logY, double yMin, double yMax) {

        static final int W = 960, H = 600, LEFT = 110, RIGHT = 40, TOP = 60, BOTTOM = 90;

        void draw(Map<String, List<Point>> series, Path out) throws IOException {
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);

            List<Integer> sizes = series.values().stream().flatMap(List::stream).map(Point::size).distinct().sorted().toList();
            double xMin = log2(sizes.get(0)), xMax = log2(sizes.get(sizes.size() - 1));
            int plotW = W - LEFT - RIGHT, plotH = H - TOP - BOTTOM;
            java.util.function.DoubleUnaryOperator px = s -> LEFT + (log2(s) - xMin) / (xMax - xMin) * plotW;
            java.util.function.DoubleUnaryOperator py = v -> {
                double t = logY ? (Math.log10(v) - Math.log10(yMin)) / (Math.log10(yMax) - Math.log10(yMin))
                                : (v - yMin) / (yMax - yMin);
                return TOP + plotH - t * plotH;
            };

            Font tick = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
            Font label = new Font(Font.SANS_SERIF, Font.PLAIN, 15);
            g.setFont(tick);
            FontMetrics fm = g.getFontMetrics();

            // Grid and y ticks
            List<Double> yTicks = new ArrayList<>();
            if (logY) {
                for (double v = yMin; v <= yMax * 1.0001; v *= 10) yTicks.add(v);
            } else {
                for (int i = 0; i <= 5; i++) yTicks.add(yMin + i * (yMax - yMin) / 5);
            }
            for (double v : yTicks) {
                int y = (int) py.applyAsDouble(v);
                g.setColor(new Color(0xe6e6e6));
                g.drawLine(LEFT, y, LEFT + plotW, y);
                g.setColor(new Color(0x444444));
                String s = logY ? String.format(Locale.ROOT, "%,.0f", v) : String.format(Locale.ROOT, "%.1f", v);
                g.drawString(s, LEFT - 10 - fm.stringWidth(s), y + 5);
            }
            // x ticks at the measured sizes
            for (int size : sizes) {
                int x = (int) px.applyAsDouble(size);
                g.setColor(new Color(0xe6e6e6));
                g.drawLine(x, TOP, x, TOP + plotH);
                g.setColor(new Color(0x444444));
                String s = String.valueOf(size);
                g.drawString(s, x - fm.stringWidth(s) / 2, TOP + plotH + 22);
            }
            g.setColor(new Color(0x444444));
            g.drawLine(LEFT, TOP + plotH, LEFT + plotW, TOP + plotH);
            g.drawLine(LEFT, TOP, LEFT, TOP + plotH);

            // Axis labels and title
            g.setFont(label);
            fm = g.getFontMetrics();
            g.drawString(xLabel, LEFT + plotW / 2 - fm.stringWidth(xLabel) / 2, H - 40);
            AffineTransform saved = g.getTransform();
            g.rotate(-Math.PI / 2);
            g.drawString(yLabel, -(TOP + plotH / 2) - fm.stringWidth(yLabel) / 2, 30);
            g.setTransform(saved);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.drawString(title, LEFT, 32);

            // Series: median line with markers, min-max whiskers
            int legendY = TOP + (int) (plotH * 0.38); // middle right: clear of both charts' curves
            for (Map.Entry<String, List<Point>> e : series.entrySet()) {
                boolean sorted = e.getKey().equals("sorted");
                Color color = sorted ? SORTED : SHUFFLED;
                Stroke line = sorted ? new BasicStroke(2.5f)
                        : new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {9, 6}, 0);
                g.setColor(color);
                List<Point> pts = e.getValue();
                for (int i = 0; i + 1 < pts.size(); i++) {
                    g.setStroke(line);
                    g.drawLine((int) px.applyAsDouble(pts.get(i).size()), (int) py.applyAsDouble(pts.get(i).median()),
                            (int) px.applyAsDouble(pts.get(i + 1).size()), (int) py.applyAsDouble(pts.get(i + 1).median()));
                }
                g.setStroke(new BasicStroke(1.5f));
                for (Point p : pts) {
                    int x = (int) px.applyAsDouble(p.size());
                    int y1 = (int) py.applyAsDouble(p.min()), y2 = (int) py.applyAsDouble(p.max());
                    g.drawLine(x, y1, x, y2);
                    g.drawLine(x - 5, y1, x + 5, y1);
                    g.drawLine(x - 5, y2, x + 5, y2);
                    int y = (int) py.applyAsDouble(p.median());
                    if (sorted) g.fillOval(x - 5, y - 5, 10, 10); else g.fillRect(x - 5, y - 5, 10, 10);
                }
                String name = sorted ? "sorted input (designed experiment)" : "shuffled input (supplementary)";
                int lx = LEFT + plotW - 330;
                g.setStroke(line);
                g.drawLine(lx, legendY - 5, lx + 36, legendY - 5);
                g.setColor(new Color(0x222222));
                g.setFont(tick);
                g.drawString(name, lx + 46, legendY);
                legendY += 24;
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 13));
            g.setColor(new Color(0x555555));
            g.drawString("Each curve: median of 5 warm runs per point (cold run excluded); whiskers span min to max.",
                    LEFT, H - 12);
            g.dispose();
            ImageIO.write(image, "png", out.toFile());
        }

        static double log2(double v) {
            return Math.log(v) / Math.log(2);
        }
    }
}
