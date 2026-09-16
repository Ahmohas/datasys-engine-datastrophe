package dk.itu.datasys;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class BinaryFormat {

    private static final int MAGIC = 0x44535452; // "DSTR"
    private static final int VERSION = 1;

    private BinaryFormat() {
    }

    static void writeHeader(DataOutputStream out, int rowCount) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(rowCount);
    }

    static int readHeader(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException("Invalid partition magic");
        }

        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported partition version: " + version);
        }

        return in.readInt();
    }

    static void writeValue(
            DataOutputStream out,
            ColumnType type,
            Object value) throws IOException {

        switch (type) {
            case STRING -> {
                byte[] bytes = ((String) value).getBytes(StandardCharsets.US_ASCII);
                out.writeInt(bytes.length);
                out.write(bytes);
            }

            case LONG -> out.writeLong((Long) value);

            case DOUBLE -> out.writeDouble((Double) value);
        }
    }

    static Object readValue(
            DataInputStream in,
            ColumnType type) throws IOException {

        return switch (type) {
            case STRING -> {
                int length = in.readInt();

                if (length < 0) {
                    throw new IOException("Negative string length");
                }

                byte[] bytes = in.readNBytes(length);

                if (bytes.length != length) {
                    throw new IOException("Unexpected end of partition");
                }

                yield new String(bytes, StandardCharsets.US_ASCII);
            }

            case LONG -> in.readLong();

            case DOUBLE -> in.readDouble();
        };
    }
}