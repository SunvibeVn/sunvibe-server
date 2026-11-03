package vn.sunvibe.survey.core.domain.model.media;

import java.time.Instant;

/**
 * The photos of one checklist (survey-media spec 4.1, 5.2): {@code setVersion} grows with every change a reader can
 * see, {@code lastMediaNo} numbers the photos (gaps allowed). Plain columns, read and changed under the set row's
 * {@code FOR UPDATE} lock, never a JPA {@code @Version}.
 */
public final class MediaSet {

    private final String checklistId;
    private long setVersion;
    private int lastMediaNo;
    private Instant updatedAt;

    private MediaSet(String checklistId, long setVersion, int lastMediaNo, Instant updatedAt) {
        if (checklistId == null || updatedAt == null) {
            throw new IllegalArgumentException("A media set has its checklist and time.");
        }
        if (setVersion < 0 || lastMediaNo < 0) {
            throw new IllegalArgumentException("Set version and last media number are not negative.");
        }
        this.checklistId = checklistId;
        this.setVersion = setVersion;
        this.lastMediaNo = lastMediaNo;
        this.updatedAt = updatedAt;
    }

    /** The empty set a checklist's first photo creates. */
    public static MediaSet start(String checklistId, Instant now) {
        return new MediaSet(checklistId, 0L, 0, now);
    }

    /** Load from database (reconstruct). */
    public static MediaSet load(String checklistId, long setVersion, int lastMediaNo, Instant updatedAt) {
        return new MediaSet(checklistId, setVersion, lastMediaNo, updatedAt);
    }

    /** The next photo number of this checklist. */
    public int nextMediaNo() {
        lastMediaNo++;
        return lastMediaNo;
    }

    /** spec 4.2: every change a reader can see bumps the set version. */
    public void bump(Instant now) {
        setVersion++;
        updatedAt = now;
    }

    public String getChecklistId() {
        return checklistId;
    }

    public long getSetVersion() {
        return setVersion;
    }

    public int getLastMediaNo() {
        return lastMediaNo;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "MediaSet[" + checklistId + " v" + setVersion + "]";
    }
}
