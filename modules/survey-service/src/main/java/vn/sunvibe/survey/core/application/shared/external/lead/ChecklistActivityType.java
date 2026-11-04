package vn.sunvibe.survey.core.application.shared.external.lead;

/** The lead History entry of a checklist command (survey-checklist BR-15, spec A-13). */
public enum ChecklistActivityType {
    /** The assignee started a checklist draft. */
    CHECKLIST_STARTED,
    /** A new assignee took a checklist draft over. */
    CHECKLIST_TAKEN_OVER,
    /** The holder saved one group of a checklist draft. */
    CHECKLIST_SAVED,
    /** The holder's photo content was committed; the detail is its evidence category (survey-media spec 5.3, A-10). */
    CHECKLIST_MEDIA_ADDED,
    /**
     * The holder removed a processing or ready photo; the detail is its evidence category (survey-media spec 5.3,
     * A-10).
     */
    CHECKLIST_MEDIA_REMOVED,
    /** The holder submitted a revision (survey-checklist submission spec 4.9). */
    CHECKLIST_SUBMITTED,
    /** The assignee started a new revision on the same visit (survey-checklist submission spec 4.9). */
    CHECKLIST_REVISION_STARTED
}
