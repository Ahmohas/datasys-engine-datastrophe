package dk.itu.datasys;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class StorageEngine {

    private final Path dataDirectory;
    private final Path catalogPath;
    private final ObjectMapper objectMapper;
    private Catalog catalog;

    public StorageEngine(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.catalogPath = dataDirectory.resolve("catalog.json");

        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        try {
            Files.createDirectories(dataDirectory);

            if (Files.exists(catalogPath)) {
                this.catalog = objectMapper.readValue(
                        catalogPath.toFile(),
                        Catalog.class
                );
            } else {
                this.catalog = new Catalog();
                saveCatalog();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage engine", e);
        }
    }

    public void createTable(String tableName, List<ColumnSpec> columns) {
        if (catalog.tables.containsKey(tableName)) {
            throw new IllegalArgumentException(
                    "Table already exists: " + tableName
            );
        }

        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Column list cannot be empty"
            );
        }

        for (int i = 0; i < columns.size(); i++) {
            for (int j = i + 1; j < columns.size(); j++) {
                if (columns.get(i).name().equals(columns.get(j).name())) {
                    throw new IllegalArgumentException(
                            "Duplicate column name: " + columns.get(i).name()
                    );
                }
            }
        }

        TableMetadata table = new TableMetadata();
        table.columns.addAll(columns);

        catalog.tables.put(tableName, table);
        saveCatalog();
    }

    public void copyFile(String tableName, String csvFilePath) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<Object[]> select(
            String tableName,
            String columnName,
            Comparison comparison,
            Object constant) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void saveCatalog() {
        try {
            objectMapper.writeValue(catalogPath.toFile(), catalog);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save catalog", e);
        }
    }
}