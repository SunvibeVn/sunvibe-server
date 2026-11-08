package vn.sunvibe.survey.core.application.usecase.checklist;

import vn.sunvibe.survey.core.application.command.checklist.SaveChecklistGroupCommand;
import vn.sunvibe.survey.core.application.command.checklist.StartChecklistCommand;
import vn.sunvibe.survey.core.application.command.checklist.TakeOverChecklistCommand;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistStartResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistTakeoverResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistView;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/**
 * Use Case - checklist commands (survey-checklist spec 4.8, 5.5). Each runs in one transaction that holds a share lock
 * on the lead row, never changes the lead and writes its History entry last.
 */
public interface SurveyChecklistCommandUseCase {

    /** Start a DRAFT on the lead's current visit (survey-checklist US-1 AC-1, BR-14). */
    ChecklistStartResult start(SurveyViewer viewer, StartChecklistCommand command);

    /** Take a draft over from its holder, with a private checkpoint (survey-checklist BR-14). */
    ChecklistTakeoverResult takeOver(SurveyViewer viewer, TakeOverChecklistCommand command);

    /**
     * Save one group (survey-checklist US-1, BR-14, BR-15, BR-21..BR-24, survey-checklist spec 5.5 steps 1..10): a content equal to the
     * stored one is a no-op answer (survey-checklist spec A-17); a G1 save cascades into the groups it changes (survey-checklist spec A-12).
     */
    ChecklistView save(SurveyViewer viewer, SaveChecklistGroupCommand command);
}
