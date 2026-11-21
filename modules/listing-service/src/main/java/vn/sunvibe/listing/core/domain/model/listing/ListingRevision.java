package vn.sunvibe.listing.core.domain.model.listing;

import java.time.Instant;

/**
 * A revision of a listing (listing BR-2: the listing is a stable shell and its revisions are its content; section 3.3
 * for the statuses). Two forms (listing-publish E06-S2 spec E06S2-D19): the full form carries the content, the terms
 * snapshot, the unit snapshot and the submission; the header form of the newest-revisions read carries none of them,
 * and its content accessors throw, so no code can take a header for an empty revision. A Pending review revision holds
 * a title, a description, terms, a unit snapshot and its submission (listing-publish US-2 AC-4, AC-5). Other statuses
 * carry no content rule here: later slices make them. A revision copied from another names that revision's number,
 * always below its own (listing-publish E06-S4 spec E06S4-D14, D18), so a chain of copies ends.
 */
public final class ListingRevision {

    private final String id;
    private final int revisionNo;
    private final RevisionStatus status;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final boolean fullForm;
    private final RevisionContent content;
    private final RevisionTerms termsOrNull;
    private final UnitSnapshot unitSnapshotOrNull;
    private final Instant submittedAtOrNull;
    private final String submittedByOrNull;
    private final Integer copiedFromRevisionNoOrNull;

    private ListingRevision(String id, int revisionNo, RevisionStatus status, String createdBy, Instant createdAt,
                            Instant updatedAt, boolean fullForm, RevisionContent content, RevisionTerms termsOrNull,
                            UnitSnapshot unitSnapshotOrNull, Instant submittedAtOrNull, String submittedByOrNull,
                            Integer copiedFromRevisionNoOrNull) {
        if (id == null || status == null || createdBy == null || createdAt == null || updatedAt == null) {
            throw new IllegalStateException("a listing revision needs its id, status, creator and times");
        }
        if (revisionNo < 1) {
            throw new IllegalStateException("a listing revision is numbered from 1");
        }
        // listing-publish E06-S4 spec E06S4-D14: a copy names an earlier revision of the same listing
        if (copiedFromRevisionNoOrNull != null
                && (copiedFromRevisionNoOrNull < 1 || copiedFromRevisionNoOrNull >= revisionNo)) {
            throw new IllegalStateException("a revision is copied from an earlier revision");
        }
        if (fullForm) {
            if (content == null) {
                throw new IllegalStateException("a revision in its full form carries its content");
            }
            if ((submittedAtOrNull == null) != (submittedByOrNull == null)) {
                throw new IllegalStateException("a submission has both its time and its submitter");
            }
            // listing-publish US-2 AC-4, AC-5: a Pending review revision is complete and was submitted
            if (status == RevisionStatus.PENDING_REVIEW && (content.titleOrNull() == null
                    || content.descriptionOrNull() == null || termsOrNull == null || unitSnapshotOrNull == null
                    || submittedAtOrNull == null)) {
                throw new IllegalStateException("a revision pending review holds its content, snapshots and submission");
            }
        }
        this.id = id;
        this.revisionNo = revisionNo;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.fullForm = fullForm;
        this.content = content;
        this.termsOrNull = termsOrNull;
        this.unitSnapshotOrNull = unitSnapshotOrNull;
        this.submittedAtOrNull = submittedAtOrNull;
        this.submittedByOrNull = submittedByOrNull;
        this.copiedFromRevisionNoOrNull = copiedFromRevisionNoOrNull;
    }

    /** A new revision, in the status {@code DRAFT}, empty, created and last changed at {@code now}. */
    public static ListingRevision create(String id, int revisionNo, String createdBy, Instant now) {
        return new ListingRevision(id, revisionNo, RevisionStatus.DRAFT, createdBy, now, now, true,
                RevisionContent.EMPTY, null, null, null, null, null);
    }

    /**
     * A new Draft copied from a revision (listing-publish US-2 AC-6; E06-S4 spec E06S4-D14): created by the copier at
     * {@code now}, with the source's title, description, features and terms snapshot with its fees, source, pending
     * start date and capture time, naming the source's number; no unit snapshot and no submission.
     */
    public static ListingRevision copyOf(String id, int revisionNo, ListingRevision source, String createdBy,
                                         Instant now) {
        source.requireFullForm();
        return new ListingRevision(id, revisionNo, RevisionStatus.DRAFT, createdBy, now, now, true, source.content,
                source.termsOrNull, null, null, null, source.revisionNo);
    }

    /** A stored revision in its header form: number, status, creator and times; no content loaded. */
    public static ListingRevision load(String id, int revisionNo, RevisionStatus status, String createdBy,
                                       Instant createdAt, Instant updatedAt) {
        return new ListingRevision(id, revisionNo, status, createdBy, createdAt, updatedAt, false, null, null, null,
                null, null, null);
    }

    /** A stored revision in its full form, not copied from another. */
    public static ListingRevision load(String id, int revisionNo, RevisionStatus status, String createdBy,
                                       Instant createdAt, Instant updatedAt, RevisionContent content,
                                       RevisionTerms termsOrNull, UnitSnapshot unitSnapshotOrNull,
                                       Instant submittedAtOrNull, String submittedByOrNull) {
        return load(id, revisionNo, status, createdBy, createdAt, updatedAt, content, termsOrNull, unitSnapshotOrNull,
                submittedAtOrNull, submittedByOrNull, null);
    }

    /** A stored revision in its full form, with the number of the revision it was copied from, if any. */
    public static ListingRevision load(String id, int revisionNo, RevisionStatus status, String createdBy,
                                       Instant createdAt, Instant updatedAt, RevisionContent content,
                                       RevisionTerms termsOrNull, UnitSnapshot unitSnapshotOrNull,
                                       Instant submittedAtOrNull, String submittedByOrNull,
                                       Integer copiedFromRevisionNoOrNull) {
        return new ListingRevision(id, revisionNo, status, createdBy, createdAt, updatedAt, true, content, termsOrNull,
                unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull);
    }

    /** The same revision with a new title, description and features. */
    ListingRevision withContent(RevisionContent newContent, Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, status, createdBy, createdAt, now, true, newContent, termsOrNull,
                unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull);
    }

    /** The same revision with a new terms snapshot, which replaces the earlier one and its fees. */
    ListingRevision withTerms(RevisionTerms terms, Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, status, createdBy, createdAt, now, true, content, terms,
                unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull);
    }

    /** Submitted for review: Pending review, with the unit snapshot, the submitter and the time. */
    ListingRevision submitted(UnitSnapshot unitSnapshot, String submitterId, Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, RevisionStatus.PENDING_REVIEW, createdBy, createdAt, now, true,
                content, termsOrNull, unitSnapshot, now, submitterId, copiedFromRevisionNoOrNull);
    }

    /** Withdrawn before a decision (listing section 3.3): Cancelled, its content and snapshots kept for history. */
    ListingRevision withdrawn(Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, RevisionStatus.CANCELLED, createdBy, createdAt, now, true, content,
                termsOrNull, unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull);
    }

    /**
     * Decided by a review (listing-publish US-4 AC-3; E06-S4 spec E06S4-D9): the status of the outcome, its content,
     * snapshots and submission kept.
     */
    ListingRevision decided(ReviewOutcome outcome, Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, outcome.status(), createdBy, createdAt, now, true, content,
                termsOrNull, unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull);
    }

    /**
     * An approved revision still waiting, cancelled by a later approval (listing-publish BR-6; E06-S4 spec E06S4-D9):
     * Cancelled, its content and snapshots kept for history.
     */
    ListingRevision cancelled(Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, RevisionStatus.CANCELLED, createdBy, createdAt, now, true, content,
                termsOrNull, unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull);
    }

    public String id() {
        return id;
    }

    public int revisionNo() {
        return revisionNo;
    }

    public RevisionStatus status() {
        return status;
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

    /** True when the content, snapshots and submission were loaded. */
    public boolean fullForm() {
        return fullForm;
    }

    public RevisionContent content() {
        requireFullForm();
        return content;
    }

    public RevisionTerms termsOrNull() {
        requireFullForm();
        return termsOrNull;
    }

    public UnitSnapshot unitSnapshotOrNull() {
        requireFullForm();
        return unitSnapshotOrNull;
    }

    public Instant submittedAtOrNull() {
        requireFullForm();
        return submittedAtOrNull;
    }

    public String submittedByOrNull() {
        requireFullForm();
        return submittedByOrNull;
    }

    /** The number of the revision this one was copied from; null for a revision not copied. */
    public Integer copiedFromRevisionNoOrNull() {
        requireFullForm();
        return copiedFromRevisionNoOrNull;
    }

    private void requireFullForm() {
        if (!fullForm) {
            throw new IllegalStateException("a revision header carries no content");
        }
    }
}
