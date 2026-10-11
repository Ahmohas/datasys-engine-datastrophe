package dk.itu.datasys;

/** Makes user-supplied text safe to put inside one field of the CSV log. */
final class LogText {
    private LogText() {
    }

    static String safe(Object value) {
        return String.valueOf(value)
                .replace(',', '_')
                .replace('"', '_')
                .replace('\r', '_')
                .replace('\n', '_');
    }
}
