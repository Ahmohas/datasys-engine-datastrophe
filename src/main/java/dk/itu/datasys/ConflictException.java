package dk.itu.datasys;

/**
 * A DML statement lost a commit race: another statement committed a new version of the table
 * after this one pinned its base version. The statement aborted and left no visible change;
 * rerunning it starts from the new version.
 */
public final class ConflictException extends RuntimeException {

    private final String tableName;
    private final long baseVersion;
    private final long currentVersion;

    public ConflictException(String tableName, long baseVersion, long currentVersion) {
        super("conflict table=" + tableName + " base=" + baseVersion + " current=" + currentVersion);
        this.tableName = tableName;
        this.baseVersion = baseVersion;
        this.currentVersion = currentVersion;
    }

    public String tableName() {
        return tableName;
    }

    public long baseVersion() {
        return baseVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
