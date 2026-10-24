package vn.sunvibe.acquisition.core.application.usecase.survey;

/** The History event of a survey checklist command (survey-checklist BR-15, spec A-13). */
public enum SurveyChecklistEvent {
    /** The assignee started a checklist draft. */
    CHECKLIST_STARTED,
    /** A new assignee took a checklist draft over. */
    CHECKLIST_TAKEN_OVER,
    /** The holder saved one group of a checklist draft. */
    CHECKLIST_SAVED
}
