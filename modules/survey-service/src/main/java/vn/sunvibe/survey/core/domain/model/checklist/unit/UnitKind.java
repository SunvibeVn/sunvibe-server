package vn.sunvibe.survey.core.domain.model.checklist.unit;

/** What a provenance unit is (survey-checklist spec 4.5, A-19, A-26). */
public enum UnitKind {
    /** A field wrapper, or a plain on-site field such as the rental scope. */
    FIELD,
    /** An authored note: the writer's words, no observer (spec A-26). */
    NOTE,
    /** An observed space of G1. */
    SPACE,
    /** The G5 hazard list wrapper ("none seen" is a recorded value). */
    HAZARD_LIST,
    /** One hazard. */
    HAZARD,
    /** A G6 item or other item: an entry of a list. */
    ITEM,
    /** One charge override of a space (PARTS only). */
    OVERRIDE,
    /** The fee list of a space (PARTS only). */
    SPACE_FEES
}
