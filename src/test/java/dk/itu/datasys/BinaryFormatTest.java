package dk.itu.datasys;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class BinaryFormatTest {

    @Test
    void stringRoundTrip() throws Exception {
        Object value = "Copenhagen";

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            BinaryFormat.writeValue(out, ColumnType.STRING, value);
        }

        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {

            assertEquals(
                    value,
                    BinaryFormat.readValue(in, ColumnType.STRING)
            );
        }
    }

    @Test
    void longRoundTrip() throws Exception {
        Object value = 123456789L;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            BinaryFormat.writeValue(out, ColumnType.LONG, value);
        }

        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {

            assertEquals(
                    value,
                    BinaryFormat.readValue(in, ColumnType.LONG)
            );
        }
    }

    @Test
    void doubleRoundTrip() throws Exception {
        Object value = 123.456;

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            BinaryFormat.writeValue(out, ColumnType.DOUBLE, value);
        }

        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {

            assertEquals(
                    value,
                    BinaryFormat.readValue(in, ColumnType.DOUBLE)
            );
        }
    }
}