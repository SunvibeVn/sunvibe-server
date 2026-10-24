package vn.sunvibe.survey.core.application.usecase;

import vn.sunvibe.survey.core.application.query.ChecklistView;
import vn.sunvibe.survey.core.application.query.CheckpointView;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/** Use Case - checklist reads for the readers of the lead (survey-checklist spec 4.4, A-10). Access-logged. */
public interface SurveyChecklistQueryUseCase {

    ChecklistView read(SurveyViewer viewer, String checklistId);

    CheckpointView readCheckpoint(SurveyViewer viewer, String checklistId, int takeoverNo);
}
