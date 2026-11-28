package vn.sunvibe.listing.core.domain.model.listing;

import vn.sunvibe.listing.core.domain.exception.RevisionNotSubmittableException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.policy.listing.ListingDisplayPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.RevisionSubmitRules;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregate - a listing: a stable shell (code, unit, transaction type, agreement, state) whose revisions are its content
 * (listing BR-2). One unit, one transaction type and one agreement for life, and a code that never changes (listing
 * BR-1, listing-detail BR-15): there is no setter. Its invariants (listing sections 3.2, 3.3) hold on create and load:
 * at least one revision, numbered 1..n without gaps, at most one {@code DRAFT} or {@code PENDING_REVIEW}, at most one
 * {@code APPROVED}, at most one {@code LIVE}.
 *
 * <p>A listing loaded for a command (listing-publish E06-S2 spec E06S2-D19) holds a bounded working set: its open
 * revision and the command's target, or, for a decision or a put live, the target, the approved revision still waiting
 * and the live revision; and the greatest revision number. The aggregate checks the invariants it can see on that set,
 * and the unique revision indexes guard the rest of the table. Each command records one history entry with identifiers
 * only (listing BR-3) and one revision change per revision it changes (a put live: the replaced and the live revision),
 * which the repository writes after the listing row's versioned update. Only a put live changes the listing's state
 * (E06-S6 spec E06S6-D6); {@link #stateChanged()} tells the repository whether to write it. A successful put live also
 * sets the publication of its live revision (E06-S7 spec E06S7-D3), which the repository writes in the same write.
 */
public final class Listing {

    private final String id;
    private final ListingCode code;
    private final String unitId;
    private final TransactionType transactionType;
    private final String agreementId;
    private final ListingState loadedState;
    private ListingState state;
    private final String createdBy;
    private final Instant createdAt;
    private final long rowVersion;
    private Instant updatedAt;
    private int newestRevisionNo;
    private List<ListingRevision> revisions;
    private List<ListingHistoryEntry> pendingHistory;
    private List<RevisionChange> pendingRevisionChanges;
    private List<RevisionDecision> pendingDecisions;
    private boolean publicationWriteDue;
    private Optional<ListingPublication> pendingPublication = Optional.empty();

    private Listing(String id, ListingCode code, String unitId, TransactionType transactionType, String agreementId,
                    ListingState state, String createdBy, Instant createdAt, Instant updatedAt, long rowVersion,
                    List<ListingRevision> revisions, List<ListingHistoryEntry> pendingHistory) {
        this(id, code, unitId, transactionType, agreementId, state, createdBy, createdAt, updatedAt, rowVersion,
                revisions == null ? 0 : revisions.size(), revisions, pendingHistory);
        if (revisions.isEmpty()) {
            throw new IllegalStateException("a listing has at least one revision");
        }
        for (int index = 0; index < this.revisions.size(); index++) {
            if (this.revisions.get(index).revisionNo() != index + 1) {
                throw new IllegalStateException("the revisions of a listing are numbered 1..n without gaps");
            }
        }
    }

    private Listing(String id, ListingCode code, String unitId, TransactionType transactionType, String agreementId,
                    ListingState state, String createdBy, Instant createdAt, Instant updatedAt, long rowVersion,
                    int newestRevisionNo, List<ListingRevision> revisions, List<ListingHistoryEntry> pendingHistory) {
        if (id == null || code == null || unitId == null || transactionType == null || agreementId == null
                || state == null || createdBy == null || createdAt == null || updatedAt == null) {
            throw new IllegalStateException("a listing needs its id, code, unit, type, agreement, state, creator and times");
        }
        if (revisions == null) {
            throw new IllegalStateException("a listing has at least one revision");
        }
        List<ListingRevision> byNumber = revisions.stream()
                .sorted(Comparator.comparingInt(ListingRevision::revisionNo))
                .toList();
        // listing section 3.3 and listing-publish BR-6: at most one open revision, one approved and one live
        requireAtMostOne(byNumber, RevisionStatus.DRAFT, RevisionStatus.PENDING_REVIEW);
        requireAtMostOne(byNumber, RevisionStatus.APPROVED);
        requireAtMostOne(byNumber, RevisionStatus.LIVE);
        this.id = id;
        this.code = code;
        this.unitId = unitId;
        this.transactionType = transactionType;
        this.agreementId = agreementId;
        this.loadedState = state;
        this.state = state;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.rowVersion = rowVersion;
        this.newestRevisionNo = newestRevisionNo;
        this.revisions = byNumber;
        this.pendingHistory = List.copyOf(pendingHistory);
        this.pendingRevisionChanges = List.of();
        this.pendingDecisions = List.of();
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

    /**
     * A stored listing loaded for a command (listing-publish E06-S2 spec E06S2-D19): its working set (its open revision
     * and the command's target, or, for a decision or a put live, the target, the approved revision still waiting and the
     * live revision, each in its full form, possibly none) and its greatest revision number. Checks: a greatest number
     * of at least 1; every revision in its full form, numbered from 1 to the greatest number, numbers distinct; at most
     * one open, one approved and one live revision among them; a Pending review revision complete (by the revision).
     */
    public static Listing loadForCommand(String id, ListingCode code, String unitId, TransactionType transactionType,
                                         String agreementId, ListingState state, String createdBy, Instant createdAt,
                                         Instant updatedAt, long rowVersion, int newestRevisionNo,
                                         List<ListingRevision> revisions) {
        if (newestRevisionNo < 1) {
            throw new IllegalStateException("a listing has at least one revision");
        }
        if (revisions == null) {
            throw new IllegalStateException("a working set is a list, possibly empty");
        }
        Set<Integer> numbers = new HashSet<>();
        for (ListingRevision revision : revisions) {
            if (!revision.fullForm()) {
                throw new IllegalStateException("a command works on revisions in their full form");
            }
            if (revision.revisionNo() > newestRevisionNo || !numbers.add(revision.revisionNo())) {
                throw new IllegalStateException("the revisions of a listing have distinct numbers up to the greatest");
            }
        }
        return new Listing(id, code, unitId, transactionType, agreementId, state, createdBy, createdAt, updatedAt,
                rowVersion, newestRevisionNo, revisions, List.of());
    }

    private static void requireAtMostOne(List<ListingRevision> revisions, RevisionStatus... statuses) {
        List<RevisionStatus> counted = List.of(statuses);
        if (revisions.stream().filter(revision -> counted.contains(revision.status())).count() > 1) {
            throw new IllegalStateException("a listing has at most one revision in each of " + counted);
        }
    }

    /** The loaded revision of that number, if the listing holds it. */
    public Optional<ListingRevision> revision(int revisionNo) {
        return revisions.stream().filter(revision -> revision.revisionNo() == revisionNo).findFirst();
    }

    /** listing section 3.3: only a Draft is edited, snapshotted or submitted; a Pending review revision is locked. */
    public void requireDraft(int revisionNo) {
        if (target(revisionNo).status() != RevisionStatus.DRAFT) {
            throw new RevisionStateException(RevisionStateException.Reason.REVISION_NOT_DRAFT);
        }
    }

    /**
     * A Draft that meets every submission rule (listing-publish US-2 AC-4, BR-2, BR-4): otherwise every violation, in
     * order, and never the text that broke one.
     */
    public void requireSubmittable(int revisionNo) {
        requireDraft(revisionNo);
        ListingRevision draft = target(revisionNo);
        List<RevisionViolation> violations = RevisionSubmitRules.violations(draft.content(), draft.termsOrNull());
        if (!violations.isEmpty()) {
            throw new RevisionNotSubmittableException(violations);
        }
    }

    /**
     * A new empty Draft numbered after the greatest number, created by the actor (listing-publish US-2 AC-6), refused
     * while a Draft or Pending review revision is open (listing-publish BR-6, listing section 3.3: one open revision).
     */
    public ListingRevision startRevision(String revisionId, String historyId, String actorId, Instant now) {
        boolean open = revisions.stream().anyMatch(revision -> revision.status() == RevisionStatus.DRAFT
                || revision.status() == RevisionStatus.PENDING_REVIEW);
        if (open) {
            throw new RevisionStateException(RevisionStateException.Reason.OPEN_REVISION_EXISTS);
        }
        ListingRevision started = ListingRevision.create(revisionId, newestRevisionNo + 1, actorId, now);
        newestRevisionNo = started.revisionNo();
        List<ListingRevision> next = new ArrayList<>(revisions);
        next.add(started);
        revisions = List.copyOf(next);
        record(ListingHistoryAction.REVISION_STARTED, started, RevisionChangeKind.ADDED, historyId, actorId, now);
        return started;
    }

    /** listing-publish US-2 AC-1, AC-3: a Draft's title, description and features replaced as a whole. */
    public ListingRevision saveContent(int revisionNo, RevisionContent content, String historyId, String actorId,
                                       Instant now) {
        requireDraft(revisionNo);
        ListingRevision saved = target(revisionNo).withContent(content, now);
        replace(saved);
        record(ListingHistoryAction.REVISION_SAVED, saved, RevisionChangeKind.UPDATED, historyId, actorId, now);
        return saved;
    }

    /** listing-publish US-2 AC-2, BR-4: a Draft's terms snapshot, replacing an earlier one and its fees. */
    public ListingRevision captureTerms(int revisionNo, RevisionTerms terms, String historyId, String actorId,
                                        Instant now) {
        requireDraft(revisionNo);
        ListingRevision captured = target(revisionNo).withTerms(terms, now);
        replace(captured);
        record(ListingHistoryAction.TERMS_CAPTURED, captured, RevisionChangeKind.TERMS_REPLACED, historyId, actorId,
                now);
        return captured;
    }

    /**
     * listing-publish US-2 AC-4, AC-5: a complete Draft becomes Pending review with the unit snapshot, its submitter and
     * time; the listing's state does not change.
     */
    public ListingRevision submit(int revisionNo, UnitSnapshot unitSnapshot, String historyId, String actorId,
                                  Instant now) {
        requireSubmittable(revisionNo);
        ListingRevision submitted = target(revisionNo).submitted(unitSnapshot, actorId, now);
        replace(submitted);
        record(ListingHistoryAction.REVISION_SUBMITTED, submitted, RevisionChangeKind.UPDATED, historyId, actorId, now);
        return submitted;
    }

    /** listing-publish US-2 AC-5, listing section 3.3: a Pending review revision withdrawn before a decision. */
    public ListingRevision withdraw(int revisionNo, String historyId, String actorId, Instant now) {
        if (target(revisionNo).status() != RevisionStatus.PENDING_REVIEW) {
            throw new RevisionStateException(RevisionStateException.Reason.REVISION_NOT_PENDING);
        }
        ListingRevision withdrawn = target(revisionNo).withdrawn(now);
        replace(withdrawn);
        record(ListingHistoryAction.REVISION_WITHDRAWN, withdrawn, RevisionChangeKind.UPDATED, historyId, actorId, now);
        return withdrawn;
    }

    /**
     * listing-publish US-4 AC-3, BR-6, BR-7 (E06-S4 spec E06S4-D8, D9): the one decision of a Pending review revision.
     * An approval first cancels an approved revision of the working set still waiting, recorded by the decider, so at
     * most one revision is approved at any write; then the target takes the outcome's status. Each step records one
     * history entry with identifiers only and one revision change, the cancelled revision first; the decision waits for
     * the repository. The decision itself changes no listing state; an approval's service then tries the put live
     * (E06-S6 spec E06S6-D7, {@link #tryPutLive}).
     */
    public ListingRevision decide(int revisionNo, RevisionDecision decision, String historyId, String cancelHistoryId,
                                  Instant now) {
        ListingRevision target = target(revisionNo);
        if (target.status() != RevisionStatus.PENDING_REVIEW) {
            throw new RevisionStateException(RevisionStateException.Reason.REVISION_NOT_PENDING);
        }
        if (decision.revisionNo() != revisionNo || !decision.revisionId().equals(target.id())) {
            throw new IllegalStateException("a decision names the revision it decides");
        }
        if (decision.outcome() == ReviewOutcome.APPROVED) {
            Optional<ListingRevision> waiting = revisions.stream()
                    .filter(revision -> revision.status() == RevisionStatus.APPROVED)
                    .findFirst();
            if (waiting.isPresent()) {
                ListingRevision cancelled = waiting.get().cancelled(now);
                replace(cancelled);
                record(ListingHistoryAction.APPROVAL_CANCELLED, cancelled, RevisionChangeKind.UPDATED, cancelHistoryId,
                        decision.decidedBy(), now);
            }
        }
        ListingRevision decided = target.decided(decision.outcome(), now);
        replace(decided);
        record(actionOf(decision.outcome()), decided, RevisionChangeKind.UPDATED, historyId, decision.decidedBy(), now);
        List<RevisionDecision> decisions = new ArrayList<>(pendingDecisions);
        decisions.add(decision);
        pendingDecisions = List.copyOf(decisions);
        return decided;
    }

    /**
     * listing-publish US-2 AC-6, AC-7, BR-6 (E06-S4 spec E06S4-D14; E06-S6 spec E06S6-D10): a Live, Approved or Needs
     * changes revision copied into a new Draft numbered after the greatest number, by the actor, with its content and
     * terms snapshot; the source stays as it is. Refused for any other source status (a Rejected, Replaced or withdrawn
     * revision included), then while a Draft or Pending review revision is open.
     */
    public ListingRevision copyRevision(int sourceNo, String revisionId, String historyId, String actorId,
                                        Instant now) {
        ListingRevision source = target(sourceNo);
        if (source.status() != RevisionStatus.NEEDS_CHANGES && source.status() != RevisionStatus.LIVE
                && source.status() != RevisionStatus.APPROVED) {
            throw new RevisionStateException(RevisionStateException.Reason.REVISION_NOT_COPYABLE);
        }
        boolean open = revisions.stream().anyMatch(revision -> revision.status() == RevisionStatus.DRAFT
                || revision.status() == RevisionStatus.PENDING_REVIEW);
        if (open) {
            throw new RevisionStateException(RevisionStateException.Reason.OPEN_REVISION_EXISTS);
        }
        ListingRevision copy = ListingRevision.copyOf(revisionId, newestRevisionNo + 1, source, actorId, now);
        newestRevisionNo = copy.revisionNo();
        List<ListingRevision> next = new ArrayList<>(revisions);
        next.add(copy);
        revisions = List.copyOf(next);
        record(ListingHistoryAction.REVISION_COPIED, copy, RevisionChangeKind.COPIED, historyId, actorId, now);
        return copy;
    }

    /**
     * listing-publish US-5 AC-1, AC-3, AC-4, BR-6, BR-11 (E06-S6 spec E06S6-D6): the try to put an Approved revision
     * live. A target not loaded is a defect; a target that is not Approved is refused. Then the display rule answers the
     * reasons that stop it, and with any reason nothing changes. Without: the live revision of the working set, if any,
     * becomes Replaced first, then the target becomes Live, so at most one revision is live at any write; an Unpublished
     * listing becomes Published. Each step records one history entry and one revision change: the replaced revision's
     * {@code REVISION_REPLACED}, then the target's action of the moment, both with the actor given (none for an automatic
     * try).
     *
     * @return the reasons that stop the put live, in their fixed order; empty when it went live
     */
    public List<DisplayReason> tryPutLive(int revisionNo, DisplayConditions conditions, PutLiveMoment moment,
                                          String actorIdOrNull, Instant now, String replacedHistoryId,
                                          String liveHistoryId) {
        ListingRevision target = target(revisionNo);
        if (target.status() != RevisionStatus.APPROVED) {
            throw new RevisionStateException(RevisionStateException.Reason.REVISION_NOT_APPROVED);
        }
        List<DisplayReason> reasons = ListingDisplayPolicy.putLiveReasons(conditions, target.termsOrNull(), now);
        if (!reasons.isEmpty()) {
            return reasons;
        }
        Optional<ListingRevision> live = revisions.stream()
                .filter(revision -> revision.status() == RevisionStatus.LIVE)
                .findFirst();
        if (live.isPresent()) {
            ListingRevision replaced = live.get().replaced(now);
            replace(replaced);
            record(ListingHistoryAction.REVISION_REPLACED, replaced, RevisionChangeKind.UPDATED, replacedHistoryId,
                    actorIdOrNull, now);
        }
        ListingRevision wentLive = target.wentLive(now);
        replace(wentLive);
        record(actionOf(moment), wentLive, RevisionChangeKind.UPDATED, liveHistoryId, actorIdOrNull, now);
        // listing section 3.2, US-5 AC-4: an Unpublished listing becomes Published; a Published one stays
        if (state == ListingState.UNPUBLISHED) {
            state = ListingState.PUBLISHED;
        }
        // listing-publish E06-S7 spec E06S7-D3: the publication row of the new live revision, written with this put live
        pendingPublication = ListingPublication.of(this, wentLive);
        publicationWriteDue = true;
        return List.of();
    }

    /**
     * True when a put live succeeded since the load (listing-publish E06-S7 spec E06S7-D3): the repository then replaces
     * the listing's publication row with {@link #pendingPublication()}, or deletes it and writes none when that is empty.
     */
    public boolean publicationWriteDue() {
        return publicationWriteDue;
    }

    /**
     * The publication of the revision the last successful put live put live (E06S7-D3); empty without one, or when the
     * live revision's unchecked fee names break a rule (E06S7-D11).
     */
    public Optional<ListingPublication> pendingPublication() {
        return pendingPublication;
    }

    /** True when a command changed the listing's state since it was loaded or created (E06-S6 spec E06S6-D6). */
    public boolean stateChanged() {
        return state != loadedState;
    }

    private static ListingHistoryAction actionOf(PutLiveMoment moment) {
        return switch (moment) {
            case SALE_COMMAND -> ListingHistoryAction.REVISION_PUT_LIVE;
            case APPROVAL -> ListingHistoryAction.REVISION_PUT_LIVE_ON_APPROVAL;
            case VALID_CONFIRMATION -> ListingHistoryAction.REVISION_PUT_LIVE_ON_CONFIRMATION;
        };
    }

    /** The decisions the last command made and the repository has not stored yet; unmodifiable. */
    public List<RevisionDecision> pendingDecisions() {
        return pendingDecisions;
    }

    private static ListingHistoryAction actionOf(ReviewOutcome outcome) {
        return switch (outcome) {
            case APPROVED -> ListingHistoryAction.REVISION_APPROVED;
            case NEEDS_CHANGES -> ListingHistoryAction.REVISION_RETURNED;
            case REJECTED -> ListingHistoryAction.REVISION_REJECTED;
        };
    }

    /** The service checks that a revision exists before it commands it, so a missing one is a defect. */
    private ListingRevision target(int revisionNo) {
        return revision(revisionNo)
                .orElseThrow(() -> new IllegalStateException("the command's revision is not loaded"));
    }

    private void replace(ListingRevision changed) {
        revisions = revisions.stream()
                .map(revision -> revision.revisionNo() == changed.revisionNo() ? changed : revision)
                .toList();
    }

    /** listing BR-3: one history entry with action, revision number, actor and time; nothing else. */
    private void record(ListingHistoryAction action, ListingRevision revision, RevisionChangeKind kind,
                        String historyId, String actorId, Instant now) {
        updatedAt = now;
        List<ListingHistoryEntry> history = new ArrayList<>(pendingHistory);
        history.add(new ListingHistoryEntry(historyId, action, revision.revisionNo(), actorId, now));
        pendingHistory = List.copyOf(history);
        List<RevisionChange> changes = new ArrayList<>(pendingRevisionChanges);
        changes.add(new RevisionChange(revision, kind));
        pendingRevisionChanges = List.copyOf(changes);
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

    /** The greatest revision number of the listing; a new revision takes the next one. */
    public int newestRevisionNo() {
        return newestRevisionNo;
    }

    /** Every revision, by number (on a listing loaded for a command, its working set); unmodifiable. */
    public List<ListingRevision> revisions() {
        return revisions;
    }

    /** The history rows the last command recorded and the repository has not stored yet. */
    public List<ListingHistoryEntry> pendingHistory() {
        return pendingHistory;
    }

    /** The revisions the commands added or changed and the repository has not written yet; unmodifiable. */
    public List<RevisionChange> pendingRevisionChanges() {
        return pendingRevisionChanges;
    }
}
