package vn.sunvibe.survey.core.domain.model.checklist;

/** Why a revision failed (survey-checklist D3-15, survey-checklist submission spec 4.5). */
public enum FailedKind {
    /** The property is out of scope: the lead closes as REJECTED. */
    OUT_OF_SCOPE,
    /** The property is not accepted: the lead closes as REJECTED. */
    NOT_ACCEPTED,
    /** Not possible for now: the lead goes back to CONTACTED and a new visit may follow. */
    TEMPORARY
}
