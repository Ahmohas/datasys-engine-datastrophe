package dk.itu.datasys;

final class Pruning {

    private Pruning() {}

    static boolean canPrune(
            ColumnType type,
            Object min,
            Object max,
            Comparison comparison,
            Object constant) {

        return switch (comparison) {
            case EQUALS ->
                    compare(type, constant, min) < 0
                    || compare(type, constant, max) > 0;

            case LESS_THAN ->
                    compare(type, min, constant) >= 0;

            case GREATER_THAN ->
                    compare(type, max, constant) <= 0;
        };
    }

    @SuppressWarnings("unchecked")
    static int compare(ColumnType type, Object left, Object right) {
        return switch (type) {
            case STRING ->
                    ((String) left).compareTo((String) right);

            case LONG ->
                    Long.compare((Long) left, (Long) right);

            case DOUBLE ->
                    Double.compare((Double) left, (Double) right);
        };
    }
}