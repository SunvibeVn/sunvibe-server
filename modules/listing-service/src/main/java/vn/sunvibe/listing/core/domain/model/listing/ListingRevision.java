package vn.sunvibe.listing.core.domain.model.listing;

import vn.sunvibe.listing.core.domain.policy.listing.RevisionTermsMatchPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A revision of a listing (listing BR-2: the listing is a stable shell and its revisions are its content; section 3.3
 * for the statuses). Two forms (listing-publish E06-S2 spec E06S2-D19): the full form carries the content, the terms
 * snapshot, the unit snapshot and the submission; the header form of the newest-revisions read carries none of them,
 * and its content accessors throw, so no code can take a header for an empty revision. A Pending review revision holds
 * a title, a description, terms, a unit snapshot and its submission (listing-publish US-2 AC-4, AC-5). Other statuses
 * carry no content rule here: later slices make them. A revision copied from another names that revision's number,
 * always below its own (listing-publish E06-S4 spec E06S4-D14, D18), so a chain of copies ends. A Live or Replaced
 * revision carries the time it went live and a Replaced one the time it was replaced (E06-S6 spec E06S6-D6 "Times");
 * the stored rows' CHECKs guard that rule, and a stored revision loaded without its times keeps none.
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
    private final Instant wentLiveAtOrNull;
    private final Instant replacedAtOrNull;
    private final boolean feeNamesChecked;

    private ListingRevision(String id, int revisionNo, RevisionStatus status, String createdBy, Instant createdAt,
                            Instant updatedAt, boolean fullForm, RevisionContent content, RevisionTerms termsOrNull,
                            UnitSnapshot unitSnapshotOrNull, Instant submittedAtOrNull, String submittedByOrNull,
                            Integer copiedFromRevisionNoOrNull, Instant wentLiveAtOrNull, Instant replacedAtOrNull,
                            boolean feeNamesChecked) {
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
        this.wentLiveAtOrNull = wentLiveAtOrNull;
        this.replacedAtOrNull = replacedAtOrNull;
        this.feeNamesChecked = feeNamesChecked;
    }

    /** A new revision, in the status {@code DRAFT}, empty, created and last changed at {@code now}. */
    public static ListingRevision create(String id, int revisionNo, String createdBy, Instant now) {
        return new ListingRevision(id, revisionNo, RevisionStatus.DRAFT, createdBy, now, now, true,
                RevisionContent.EMPTY, null, null, null, null, null, null, null, false);
    }

    /**
     * A new Draft copied from a revision (listing-publish US-2 AC-6; E06-S4 spec E06S4-D14): created by the copier at
     * {@code now}, with the source's title, description, features and terms snapshot with its fees, source, pending
     * start date and capture time, naming the source's number; no unit snapshot and no submission. Its fee names are not
     * checked: it is a Draft, submitted again (listing-publish E06-S7 spec E06S7-D11).
     */
    public static ListingRevision copyOf(String id, int revisionNo, ListingRevision source, String createdBy,
                                         Instant now) {
        source.requireFullForm();
        return new ListingRevision(id, revisionNo, RevisionStatus.DRAFT, createdBy, now, now, true, source.content,
                source.termsOrNull, null, null, null, source.revisionNo, null, null, false);
    }

    /** A stored revision in its header form: number, status, creator and times; no content loaded. */
    public static ListingRevision load(String id, int revisionNo, RevisionStatus status, String createdBy,
                                       Instant createdAt, Instant updatedAt) {
        return load(id, revisionNo, status, createdBy, createdAt, updatedAt, null, null);
    }

    /** A stored revision in its header form with the time it went live and the time it was replaced, if any. */
    public static ListingRevision load(String id, int revisionNo, RevisionStatus status, String createdBy,
                                       Instant createdAt, Instant updatedAt, Instant wentLiveAtOrNull,
                                       Instant replacedAtOrNull) {
        return new ListingRevision(id, revisionNo, status, createdBy, createdAt, updatedAt, false, null, null, null,
                null, null, null, wentLiveAtOrNull, replacedAtOrNull, false);
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
        return load(id, revisionNo, status, createdBy, createdAt, updatedAt, content, termsOrNull, unitSnapshotOrNull,
                submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull, null, null);
    }

    /**
     * A stored revision in its full form, with the number of the revision it was copied from, the time it went live and
     * the time it was replaced, if any; its fee names read as not checked.
     */
    public static ListingRevision load(String id, int revisionNo, RevisionStatus status, String createdBy,
                                       Instant createdAt, Instant updatedAt, RevisionContent content,
                                       RevisionTerms termsOrNull, UnitSnapshot unitSnapshotOrNull,
                                       Instant submittedAtOrNull, String submittedByOrNull,
                                       Integer copiedFromRevisionNoOrNull, Instant wentLiveAtOrNull,
                                       Instant replacedAtOrNull) {
        return load(id, revisionNo, status, createdBy, createdAt, updatedAt, content, termsOrNull, unitSnapshotOrNull,
                submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull, wentLiveAtOrNull, replacedAtOrNull,
                false);
    }

    /**
     * A stored revision in its full form with every stored value, whether its fee names passed the machine check at its
     * submission included (listing-publish E06-S7 spec E06S7-D11).
     */
    public static ListingRevision load(String id, int revisionNo, RevisionStatus status, String createdBy,
                                       Instant createdAt, Instant updatedAt, RevisionContent content,
                                       RevisionTerms termsOrNull, UnitSnapshot unitSnapshotOrNull,
                                       Instant submittedAtOrNull, String submittedByOrNull,
                                       Integer copiedFromRevisionNoOrNull, Instant wentLiveAtOrNull,
                                       Instant replacedAtOrNull, boolean feeNamesChecked) {
        return new ListingRevision(id, revisionNo, status, createdBy, createdAt, updatedAt, true, content, termsOrNull,
                unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull, wentLiveAtOrNull,
                replacedAtOrNull, feeNamesChecked);
    }

    /** The same revision with a new title, description and features. */
    ListingRevision withContent(RevisionContent newContent, Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, status, createdBy, createdAt, now, true, newContent, termsOrNull,
                unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull, wentLiveAtOrNull,
                replacedAtOrNull, feeNamesChecked);
    }

    /** The same revision with a new terms snapshot, which replaces the earlier one and its fees. */
    ListingRevision withTerms(RevisionTerms terms, Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, status, createdBy, createdAt, now, true, content, terms,
                unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull, wentLiveAtOrNull,
                replacedAtOrNull, feeNamesChecked);
    }

    /**
     * Submitted for review: Pending review, with the unit snapshot, the submitter and the time; its fee names passed the
     * machine check of the submission (listing-publish E06-S7 spec E06S7-D10, D11).
     */
    ListingRevision submitted(UnitSnapshot unitSnapshot, String submitterId, Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, RevisionStatus.PENDING_REVIEW, createdBy, createdAt, now, true,
                content, termsOrNull, unitSnapshot, now, submitterId, copiedFromRevisionNoOrNull, wentLiveAtOrNull,
                replacedAtOrNull, true);
    }

    /** Withdrawn before a decision (listing section 3.3): Cancelled, its content and snapshots kept for history. */
    ListingRevision withdrawn(Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, RevisionStatus.CANCELLED, createdBy, createdAt, now, true, content,
                termsOrNull, unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull,
                wentLiveAtOrNull, replacedAtOrNull, feeNamesChecked);
    }

    /**
     * Decided by a review (listing-publish US-4 AC-3; E06-S4 spec E06S4-D9): the status of the outcome, its content,
     * snapshots and submission kept.
     */
    ListingRevision decided(ReviewOutcome outcome, Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, outcome.status(), createdBy, createdAt, now, true, content,
                termsOrNull, unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull,
                wentLiveAtOrNull, replacedAtOrNull, feeNamesChecked);
    }

    /**
     * An approved revision still waiting, cancelled by a later approval (listing-publish BR-6; E06-S4 spec E06S4-D9):
     * Cancelled, its content and snapshots kept for history.
     */
    ListingRevision cancelled(Instant now) {
        requireFullForm();
        return new ListingRevision(id, revisionNo, RevisionStatus.CANCELLED, createdBy, createdAt, now, true, content,
                termsOrNull, unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull,
                wentLiveAtOrNull, replacedAtOrNull, feeNamesChecked);
    }

    /**
     * Put live (listing-publish US-5 AC-4; E06-S6 spec E06S6-D6): an Approved revision becomes Live with the time it went
     * live; its content, snapshots and submission kept.
     */
    ListingRevision wentLive(Instant now) {
        requireFullForm();
        if (status != RevisionStatus.APPROVED) {
            throw new IllegalStateException("only an approved revision goes live");
        }
        return new ListingRevision(id, revisionNo, RevisionStatus.LIVE, createdBy, createdAt, now, true, content,
                termsOrNull, unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull, now,
                null, feeNamesChecked);
    }

    /**
     * Replaced by a newer live revision (listing-publish BR-6; E06-S6 spec E06S6-D6): the Live revision becomes Replaced
     * with the time it was replaced, keeping the time it went live.
     */
    ListingRevision replaced(Instant now) {
        requireFullForm();
        if (status != RevisionStatus.LIVE) {
            throw new IllegalStateException("only the live revision is replaced");
        }
        return new ListingRevision(id, revisionNo, RevisionStatus.REPLACED, createdBy, createdAt, now, true, content,
                termsOrNull, unitSnapshotOrNull, submittedAtOrNull, submittedByOrNull, copiedFromRevisionNoOrNull,
                wentLiveAtOrNull, now, feeNamesChecked);
    }

    /**
     * The parts of this revision that differ from the live revision (listing-publish US-4 AC-2; E06-S6 spec E06S6-D12),
     * in the fixed order of {@link ComparedPart}: the title and the description when the stored texts differ (null
     * equals only null), the features when any of the thirteen values differs, and the terms exactly when the terms
     * match rule does not read this snapshot as equal to the live one by value; a change of the source, the pending start
     * date or the capture time alone is no change, and no snapshot differs.
     */
    public List<ComparedPart> changedPartsFrom(ListingRevision live) {
        requireFullForm();
        live.requireFullForm();
        List<ComparedPart> parts = new ArrayList<>();
        if (!Objects.equals(content.titleOrNull(), live.content.titleOrNull())) {
            parts.add(ComparedPart.TITLE);
        }
        if (!Objects.equals(content.descriptionOrNull(), live.content.descriptionOrNull())) {
            parts.add(ComparedPart.DESCRIPTION);
        }
        if (!Objects.equals(content.features(), live.content.features())) {
            parts.add(ComparedPart.FEATURES);
        }
        if (RevisionTermsMatchPolicy.match(termsOrNull, live.termsOrNull, null) != TermsMatch.IN_EFFECT) {
            parts.add(ComparedPart.TERMS);
        }
        return List.copyOf(parts);
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

    /** The time the revision went live; null for a revision that never did, or one loaded without its times. */
    public Instant wentLiveAtOrNull() {
        return wentLiveAtOrNull;
    }

    /** The time the revision was replaced by a newer live one; null otherwise. */
    public Instant replacedAtOrNull() {
        return replacedAtOrNull;
    }

    /**
     * True when the revision's fee names passed the machine check at its submission (listing-publish E06-S7 spec
     * E06S7-D11): set by a submission, false for a new revision, a copy and a revision submitted before the check.
     */
    public boolean feeNamesChecked() {
        return feeNamesChecked;
    }

    private void requireFullForm() {
        if (!fullForm) {
            throw new IllegalStateException("a revision header carries no content");
        }
    }
}
