package vn.sunvibe.survey.core.domain.policy.checklist;

/** Why a PASSED revision can no longer be used (survey-checklist submission spec 4.7). */
public enum UsabilityReason {
    /** The lead's intent changed after the revision: its cycle ended. */
    INTENT_CHANGED,
    /** The lead is closed. */
    LEAD_CLOSED,
    /** A frozen photo is under a security hold; its release restores the revision. */
    EVIDENCE_HELD,
    /** A frozen photo lost its content for good. */
    EVIDENCE_GONE,
    /** A manager withdrew the revision from use (property-create requalification spec 4): the mark is never removed. */
    REQUALIFIED
}
