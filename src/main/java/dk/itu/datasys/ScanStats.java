package dk.itu.datasys;

public final class ScanStats {

    private int partitionsPruned;
    private int partitionsRead;

    public void recordPruned() {
        partitionsPruned++;
    }

    public void recordRead() {
        partitionsRead++;
    }

    public int partitionsPruned() {
        return partitionsPruned;
    }

    public int partitionsRead() {
        return partitionsRead;
    }
}