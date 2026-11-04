package vn.sunvibe.survey.core.domain.policy.checklist;

/** Why a missing item is listed (survey-checklist spec 4.7, A-8). */
public enum MissingProblem {
    /** A needed field is still UNKNOWN. */
    NOT_CHECKED,
    /** A main space, kitchen, bathroom or shared wet area was not or only partly entered (BR-17). */
    REQUIRED_AREA_NOT_ENTERED,
    /** The rental scope is not set. */
    SCOPE_MISSING,
    /** No observed space yet. */
    NO_SPACES,
    /** The fit is recorded but not IN_SCOPE (BR-2). */
    NOT_IN_SCOPE,
    /** The address differs from the lead and no note says how (BR-19). */
    DIFFERENCE_NOTE_MISSING,
    /** Photos not available for the checklist (survey-media spec A-1): one item, as survey-checklist US-2 AC-4. */
    EVIDENCE_PENDING,
    /** A required (placement, category) pair without a usable or processing photo (survey-media spec 4.6). */
    EVIDENCE_MISSING,
    /** A required pair whose only photos are still processing (survey-media spec 4.6). */
    EVIDENCE_PROCESSING,
    /** A selected photo is held or lost its content (survey-checklist submission spec 4.4, D3-2). */
    EVIDENCE_UNAVAILABLE
}
