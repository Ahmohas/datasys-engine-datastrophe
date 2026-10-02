package dk.itu.datasys;

public final class ScanStats {

    private final int partitionsTotal;
    private int partitionsPruned;
    private int partitionsRead;

    public ScanStats(int partitionsTotal) {
        this.partitionsTotal = partitionsTotal;
    }

    public void recordPruned() {
        partitionsPruned++;
    }

    public void recordRead() {
        partitionsRead++;
    }

    public int partitionsTotal() {
        return partitionsTotal;
    }

    public int partitionsPruned() {
        return partitionsPruned;
    }

    public int partitionsRead() {
        return partitionsRead;
    }
}
