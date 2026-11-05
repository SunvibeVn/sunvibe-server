package vn.sunvibe.survey.core.application.usecase;

import vn.sunvibe.survey.core.application.command.RequalifyCommand;
import vn.sunvibe.survey.core.application.query.ChecklistRequalifyResult;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/** Use Case - the requalification of a PASSED revision (property-create requalification spec 4, 5.3). */
public interface SurveyChecklistRequalificationCommandUseCase {

    /**
     * A manager withdraws the lead's current PASSED revision from use (property-create US-8, BR-17): the mark, its reason
     * and the lead's move, in one transaction under the lead's lock. The revision's content stays as it is.
     */
    ChecklistRequalifyResult mark(SurveyViewer viewer, RequalifyCommand command);
}
