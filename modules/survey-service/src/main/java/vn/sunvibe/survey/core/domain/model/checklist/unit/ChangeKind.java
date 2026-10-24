package vn.sunvibe.survey.core.domain.model.checklist.unit;

/** How a save changes a provenance unit (survey-checklist spec A-19). */
public enum ChangeKind {
    /** A unit that never held a value in this checklist (no metadata, no tombstone) gets one. */
    FIRST_FILL,
    /**
     * A new entry of a list: a space, hazard, G6 item or other item whose key has neither metadata nor a tombstone; the
     * same identity sent again after its removal is REPLACED (spec A-29).
     */
    ADDED_ENTRY,
    /** A recorded unit, or one coming back from a tombstone, gets another value, state, source or date. */
    REPLACED,
    /** A recorded unit goes back to UNKNOWN; its metadata is kept. */
    CLEARED,
    /** A recorded unit leaves the content (an entry removed, a space removed, a scope switch). */
    REMOVED
}
