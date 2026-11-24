package dk.itu.datasys;

import java.util.List;

public final class ScanStats {

    private final int partitionsTotal;
    private int partitionsPruned;
    private int partitionsRead;

    public ScanStats(int partitionsTotal) {
        this.partitionsTotal = partitionsTotal;
    }

    /** The statistics of several tables added up. */
    public static ScanStats sum(List<ScanStats> all) {
        ScanStats total = new ScanStats(all.stream().mapToInt(ScanStats::partitionsTotal).sum());
        total.partitionsPruned = all.stream().mapToInt(ScanStats::partitionsPruned).sum();
        total.partitionsRead = all.stream().mapToInt(ScanStats::partitionsRead).sum();
        return total;
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
