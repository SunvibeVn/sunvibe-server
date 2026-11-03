package vn.sunvibe.acquisition.core.application.usecase.survey;

/** The History event of a survey checklist command (survey-checklist BR-15, spec A-13). */
public enum SurveyChecklistEvent {
    /** The assignee started a checklist draft. */
    CHECKLIST_STARTED,
    /** A new assignee took a checklist draft over. */
    CHECKLIST_TAKEN_OVER,
    /** The holder saved one group of a checklist draft. */
    CHECKLIST_SAVED,
    /** The holder's photo content was committed; the detail is its evidence category (survey-media spec 5.3). */
    CHECKLIST_MEDIA_ADDED,
    /** The holder removed a processing or ready photo; the detail is its evidence category (survey-media spec 5.3). */
    CHECKLIST_MEDIA_REMOVED
}
