package dk.itu.datasys;

import java.util.ArrayList;
import java.util.List;

public class PartitionMetadata {

    public String file;
    public int rowCount;
    public List<Object> minValues = new ArrayList<>();
    public List<Object> maxValues = new ArrayList<>();
}