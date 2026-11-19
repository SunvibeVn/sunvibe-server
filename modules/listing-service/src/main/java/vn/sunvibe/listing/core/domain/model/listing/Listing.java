package vn.sunvibe.listing.core.domain.model.listing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregate - a listing: a stable shell (code, unit, transaction type, agreement, state) whose revisions are its content
 * (listing BR-2). One unit, one transaction type and one agreement for life, and a code that never changes (listing
 * BR-1, listing-detail BR-15): there is no setter. Its invariants (listing sections 3.2, 3.3) hold on create and load:
 * at least one revision, numbered 1..n without gaps, at most one {@code DRAFT} or {@code PENDING_REVIEW}, at most one
 * {@code APPROVED}, at most one {@code LIVE}.
 */
public final class Listing {

    private final String id;
    private final ListingCode code;
    private final String unitId;
    private final TransactionType transactionType;
    private final String agreementId;
    private final ListingState state;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long rowVersion;
    private final List<ListingRevision> revisions;
    private final List<ListingHistoryEntry> pendingHistory;

    private Listing(String id, ListingCode code, String unitId, TransactionType transactionType, String agreementId,
                    ListingState state, String createdBy, Instant createdAt, Instant updatedAt, long rowVersion,
                    List<ListingRevision> revisions, List<ListingHistoryEntry> pendingHistory) {
        if (id == null || code == null || unitId == null || transactionType == null || agreementId == null
                || state == null || createdBy == null || createdAt == null || updatedAt == null) {
            throw new IllegalStateException("a listing needs its id, code, unit, type, agreement, state, creator and times");
        }
        if (revisions == null || revisions.isEmpty()) {
            throw new IllegalStateException("a listing has at least one revision");
        }
        List<ListingRevision> byNumber = revisions.stream()
                .sorted(Comparator.comparingInt(ListingRevision::revisionNo))
                .toList();
        for (int index = 0; index < byNumber.size(); index++) {
            if (byNumber.get(index).revisionNo() != index + 1) {
                throw new IllegalStateException("the revisions of a listing are numbered 1..n without gaps");
            }
        }
        // listing section 3.3 and listing-publish BR-6: at most one open revision, one approved and one live
        requireAtMostOne(byNumber, RevisionStatus.DRAFT, RevisionStatus.PENDING_REVIEW);
        requireAtMostOne(byNumber, RevisionStatus.APPROVED);
        requireAtMostOne(byNumber, RevisionStatus.LIVE);
        this.id = id;
        this.code = code;
        this.unitId = unitId;
        this.transactionType = transactionType;
        this.agreementId = agreementId;
        this.state = state;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.rowVersion = rowVersion;
        this.revisions = byNumber;
        this.pendingHistory = List.copyOf(pendingHistory);
    }

    /**
     * A new listing (listing-publish US-1 AC-4, E06-S1 spec E06S1-D16): {@code UNPUBLISHED}, long-term rental, revision 1
     * {@code DRAFT} by the creator, and one pending history entry {@code CREATED} with revision 1, the creator and
     * {@code now} (listing BR-3: history from the first command).
     */
    public static Listing create(String id, ListingCode code, String unitId, String agreementId, String creatorId,
                                 Instant now, String revisionId, String historyId) {
        if (now == null || creatorId == null) {
            throw new IllegalStateException("a new listing needs its creator and time");
        }
        List<ListingHistoryEntry> history = new ArrayList<>();
        history.add(new ListingHistoryEntry(historyId, ListingHistoryAction.CREATED, 1, creatorId, now));
        return new Listing(id, code, unitId, TransactionType.LONG_TERM_RENT, agreementId, ListingState.UNPUBLISHED,
                creatorId, now, now, 0L, List.of(ListingRevision.create(revisionId, 1, creatorId, now)), history);
    }

    /** A stored listing with all its revisions; no pending history. */
    public static Listing load(String id, ListingCode code, String unitId, TransactionType transactionType,
                               String agreementId, ListingState state, String createdBy, Instant createdAt,
                               Instant updatedAt, long rowVersion, List<ListingRevision> revisions) {
        return new Listing(id, code, unitId, transactionType, agreementId, state, createdBy, createdAt, updatedAt,
                rowVersion, revisions, List.of());
    }

    private static void requireAtMostOne(List<ListingRevision> revisions, RevisionStatus... statuses) {
        List<RevisionStatus> counted = List.of(statuses);
        if (revisions.stream().filter(revision -> counted.contains(revision.status())).count() > 1) {
            throw new IllegalStateException("a listing has at most one revision in each of " + counted);
        }
    }

    public String id() {
        return id;
    }

    public ListingCode code() {
        return code;
    }

    public String unitId() {
        return unitId;
    }

    public TransactionType transactionType() {
        return transactionType;
    }

    public String agreementId() {
        return agreementId;
    }

    public ListingState state() {
        return state;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long rowVersion() {
        return rowVersion;
    }

    /** Every revision, by number; unmodifiable. */
    public List<ListingRevision> revisions() {
        return revisions;
    }

    /** The history rows the last command recorded and the repository has not stored yet. */
    public List<ListingHistoryEntry> pendingHistory() {
        return pendingHistory;
    }
}
