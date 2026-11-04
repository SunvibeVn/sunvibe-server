package vn.sunvibe.survey.core.domain.model.checklist;

/** What a reviewer decides on a submitted revision (survey-checklist submission spec 4.1, 4.5). */
public enum DecisionOutcome {
    /** Accepted; the lead becomes QUALIFIED. */
    PASSED,
    /** Returned, with a {@link NeedsChangesKind}. */
    NEEDS_CHANGES,
    /** Not accepted, with a {@link FailedKind}. */
    FAILED;

    /** The outcome a revision of this status carries, null before a decision. */
    public static DecisionOutcome ofStatus(ChecklistStatus status) {
        return switch (status) {
            case PASSED -> PASSED;
            case NEEDS_CHANGES -> NEEDS_CHANGES;
            case FAILED -> FAILED;
            default -> null;
        };
    }

    /** The status the revision takes. */
    public ChecklistStatus status() {
        return switch (this) {
            case PASSED -> ChecklistStatus.PASSED;
            case NEEDS_CHANGES -> ChecklistStatus.NEEDS_CHANGES;
            case FAILED -> ChecklistStatus.FAILED;
        };
    }
}
