package vn.sunvibe.listing.core.domain.model.listing;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The one decision of a reviewed revision (listing-publish US-4 AC-3, BR-7; E06-S4 spec E06S4-D6, D8): stored once, never
 * changed. Its shape per outcome is held here a second time, behind the request's (E06S4-D6): an approval carries the
 * checklist version it confirmed and no reason, group or note; a return 1 to 5 distinct reasons and a note, no group; a
 * rejection a group and a note, no reason. The reasons are kept in {@link ChangeReason} order. The reasons, the group and
 * the note are private (listing BR-2, E06S4-D20): {@link #toString()} names the revision and the outcome only.
 */
public record RevisionDecision(String revisionId, int revisionNo, ReviewOutcome outcome, String decidedBy,
                               Instant decidedAt, Integer checklistVersionOrNull, List<ChangeReason> changeReasons,
                               RejectionGroup rejectionGroupOrNull, ReviewNote noteOrNull) {

    /** E06S4-D6: a return names 1 to 5 reasons. */
    public static final int MAX_CHANGE_REASONS = 5;

    public RevisionDecision {
        if (revisionId == null || outcome == null || decidedBy == null || decidedAt == null) {
            throw new IllegalArgumentException("a decision needs its revision, outcome, decider and time");
        }
        if (revisionNo < 1) {
            throw new IllegalArgumentException("a decision names a revision numbered from 1");
        }
        List<ChangeReason> reasons = changeReasons == null ? List.of() : List.copyOf(changeReasons);
        boolean valid = switch (outcome) {
            case APPROVED -> checklistVersionOrNull != null && checklistVersionOrNull >= 1
                    && checklistVersionOrNull <= ReviewCheck.CHECKLIST_VERSION && reasons.isEmpty()
                    && rejectionGroupOrNull == null && noteOrNull == null;
            case NEEDS_CHANGES -> checklistVersionOrNull == null && !reasons.isEmpty()
                    && reasons.size() <= MAX_CHANGE_REASONS && EnumSet.copyOf(reasons).size() == reasons.size()
                    && rejectionGroupOrNull == null && noteOrNull != null;
            case REJECTED -> checklistVersionOrNull == null && reasons.isEmpty() && rejectionGroupOrNull != null
                    && noteOrNull != null;
        };
        if (!valid) {
            throw new IllegalArgumentException("a decision does not have its outcome's shape");
        }
        changeReasons = reasons.stream().sorted().toList();
    }

    /**
     * An approval that confirmed every check of the current checklist once (E06S4-D6).
     *
     * @throws IllegalArgumentException the checks are not the five codes of version 1, each once
     */
    public static RevisionDecision approved(String revisionId, int revisionNo, List<ReviewCheck> checks,
                                            String decidedBy, Instant now) {
        if (checks == null || checks.size() != ReviewCheck.VERSION_1.size()
                || !Set.copyOf(checks).equals(Set.copyOf(ReviewCheck.VERSION_1))) {
            throw new IllegalArgumentException("an approval confirms each check of the checklist once");
        }
        return new RevisionDecision(revisionId, revisionNo, ReviewOutcome.APPROVED, decidedBy, now,
                ReviewCheck.CHECKLIST_VERSION, List.of(), null, null);
    }

    /** A return for changes with its reasons and note (E06S4-D6). */
    public static RevisionDecision needsChanges(String revisionId, int revisionNo, List<ChangeReason> reasons,
                                                ReviewNote note, String decidedBy, Instant now) {
        return new RevisionDecision(revisionId, revisionNo, ReviewOutcome.NEEDS_CHANGES, decidedBy, now, null, reasons,
                null, note);
    }

    /** A rejection with its group and note (E06S4-D6). */
    public static RevisionDecision rejected(String revisionId, int revisionNo, RejectionGroup group, ReviewNote note,
                                            String decidedBy, Instant now) {
        return new RevisionDecision(revisionId, revisionNo, ReviewOutcome.REJECTED, decidedBy, now, null, List.of(),
                group, note);
    }

    /** listing BR-2, E06S4-D20: no reason, group or note. */
    @Override
    public String toString() {
        return "RevisionDecision[" + revisionId + ", " + outcome + "]";
    }
}
