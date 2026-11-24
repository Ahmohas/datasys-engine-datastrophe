package dk.itu.datasys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ColumnResolverTest {

    private static final List<ColumnSpec> ORDERS = List.of(
            new ColumnSpec("o_orderkey", ColumnType.LONG),
            new ColumnSpec("o_custname", ColumnType.STRING),
            new ColumnSpec("o_prodname", ColumnType.STRING),
            new ColumnSpec("o_quantity", ColumnType.LONG));
    private static final List<ColumnSpec> CUSTOMERS = List.of(
            new ColumnSpec("c_name", ColumnType.STRING),
            new ColumnSpec("c_city", ColumnType.STRING));
    private static final List<ColumnSpec> PRODUCTS = List.of(
            new ColumnSpec("p_name", ColumnType.STRING),
            new ColumnSpec("p_price", ColumnType.DOUBLE));

    // Joined row: o_orderkey o_custname o_prodname o_quantity | c_name c_city | p_name p_price
    //             0          1          2          3          | 4      5      | 6      7
    private final ColumnResolver resolver = new ColumnResolver(
            List.of("orders", "customers", "products"),
            List.of(ORDERS, CUSTOMERS, PRODUCTS));

    @Test
    void columnOfTheFirstTableKeepsItsPosition() {
        ColumnResolver.Column column = resolver.resolve("o_prodname");

        assertEquals(0, column.table());
        assertEquals("orders", column.tableName());
        assertEquals(2, column.index());
        assertEquals(2, column.localIndex());
        assertEquals(ColumnType.STRING, column.type());
    }

    @Test
    void columnOfALaterTableIsOffsetByTheSchemasBeforeIt() {
        ColumnResolver.Column city = resolver.resolve("c_city");
        assertEquals(1, city.table());
        assertEquals(5, city.index());
        assertEquals(1, city.localIndex());

        ColumnResolver.Column price = resolver.resolve("p_price");
        assertEquals(2, price.table());
        assertEquals("products", price.tableName());
        assertEquals(7, price.index());
        assertEquals(1, price.localIndex());
        assertEquals(ColumnType.DOUBLE, price.type());
    }

    @Test
    void offsetsAndWidthDescribeTheConcatenatedRow() {
        assertEquals(0, resolver.offset(0));
        assertEquals(4, resolver.offset(1));
        assertEquals(6, resolver.offset(2));
        assertEquals(8, resolver.width());
    }

    @Test
    void unknownColumnThrows() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> resolver.resolve("ct_city"));
        assertEquals("Unknown column: ct_city", e.getMessage());
    }

    @Test
    void columnInTwoTablesIsAmbiguous() {
        ColumnResolver twoWithId = new ColumnResolver(
                List.of("a", "b"),
                List.of(
                        List.of(new ColumnSpec("id", ColumnType.LONG)),
                        List.of(new ColumnSpec("name", ColumnType.STRING), new ColumnSpec("id", ColumnType.LONG))));

        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> twoWithId.resolve("id"));
        assertTrue(e.getMessage().startsWith("Ambiguous column: id"), e.getMessage());
        assertTrue(e.getMessage().contains("a and b"), e.getMessage());
        // A name in one table only still resolves
        assertEquals(1, twoWithId.resolve("name").index());
    }

    @Test
    void namesAreCaseSensitiveLikeTheCatalog() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("C_CITY"));
    }
}
