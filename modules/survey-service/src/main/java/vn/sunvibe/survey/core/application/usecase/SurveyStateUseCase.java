package vn.sunvibe.survey.core.application.usecase;

/**
 * Use Case - what survey publishes for the lead rules (survey-checklist submission spec 4.7, 4.8, plan PD-3). It reads
 * survey tables only and never calls the lead back: acquisition passes the cycle.
 */
public interface SurveyStateUseCase {

    /** The record of the lead's cycle; asked inside the lead command's transaction, after the row is locked. */
    LeadSurveyState surveyState(String leadId, int intentCycle);
}
