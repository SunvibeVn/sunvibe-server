package vn.sunvibe.survey.core.application.usecase;

import vn.sunvibe.survey.core.application.query.ChecklistView;
import vn.sunvibe.survey.core.application.query.CheckpointView;
import vn.sunvibe.survey.core.application.query.ReviewQueueItem;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.domain.repository.PageResult;

/** Use Case - checklist reads for the readers of the lead (survey-checklist spec 4.4, A-10). Access-logged. */
public interface SurveyChecklistQueryUseCase {

    ChecklistView read(SurveyViewer viewer, String checklistId);

    CheckpointView readCheckpoint(SurveyViewer viewer, String checklistId, int takeoverNo);

    /**
     * The SUBMITTED revisions of the viewer's leads in a live cycle, oldest submission first (survey-checklist submission
     * spec 7.1, S-18, plan PD-4). The page is zero-based.
     */
    PageResult<ReviewQueueItem> reviewQueue(SurveyViewer viewer, int page, int size);
}
