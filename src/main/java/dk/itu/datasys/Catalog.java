package dk.itu.datasys;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class Catalog {

    // Concurrent so a commit's put is safely visible to statements on other threads;
    // sorted so catalog.json lists tables in a stable order
    @JsonDeserialize(as = ConcurrentSkipListMap.class)
    public Map<String, TableMetadata> tables = new ConcurrentSkipListMap<>();
}
