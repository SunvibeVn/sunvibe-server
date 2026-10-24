package vn.sunvibe.survey.core.domain.model.checklist;

/** Why the holder of a draft is recorded (survey-checklist spec 4.5, BR-14). */
public enum TakeoverKind {
    /** Takeover #1: someone other than the visit's performer started the draft (intake declaration). */
    START_BY_OTHER,
    /** A new assignee took the draft over from its holder, with a private checkpoint. */
    TAKEOVER
}
