package vn.sunvibe.acquisition.core.application.query;

import java.util.List;

/**
 * Result of a bulk assignment (acquisition-owner-lead-bulk-assign spec 3.1 step 5, D-3): one outcome per lead of the
 * command, in the order of the command, and nothing else.
 */
public record AssignLeadsResult(List<LeadOutcome> outcomes) {

    public AssignLeadsResult {
        outcomes = List.copyOf(outcomes);
    }

    /** The outcome of one lead, named by the id the caller sent. */
    public record LeadOutcome(String leadId, Outcome outcome) {
    }

    /** acquisition-owner-lead-bulk-assign BR-7: exactly one per lead, checked in the order of the constants. */
    public enum Outcome {
        /** The lead was given to the assignee, with the effects of one assignment. */
        ASSIGNED,
        /** The lead is already held by the assignee: nothing was written, the version is unchanged. */
        ALREADY_ASSIGNED,
        /** The lead changed since the caller saw it (another version): nothing was written. */
        STALE,
        /** The lead is closed (DUPLICATE, REJECTED, WITHDRAWN): nothing was written. */
        CLOSED,
        /**
         * No such lead, an id that is not a lead id, or a lead the caller may not see; the same outcome for all three
         * (acquisition-owner-lead-queue BR-2).
         */
        NOT_FOUND
    }
}
