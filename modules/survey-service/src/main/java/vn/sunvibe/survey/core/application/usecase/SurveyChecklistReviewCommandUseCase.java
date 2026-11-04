package vn.sunvibe.survey.core.application.usecase;

import vn.sunvibe.survey.core.application.command.DecideChecklistCommand;
import vn.sunvibe.survey.core.application.command.StartRevisionCommand;
import vn.sunvibe.survey.core.application.command.SubmitChecklistCommand;
import vn.sunvibe.survey.core.application.query.ChecklistDecideResult;
import vn.sunvibe.survey.core.application.query.ChecklistRevisionResult;
import vn.sunvibe.survey.core.application.query.ChecklistSubmitResult;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;

/** Use Case - submission, review and revisions of a checklist (survey-checklist submission spec 4.3..4.6, 5.4, 5.5). */
public interface SurveyChecklistReviewCommandUseCase {

    /**
     * The holder freezes the revision with its photos (survey-checklist US-2): the lead's version and status stay. A
     * repeat by the same caller answers the revision and writes nothing (spec A-4).
     */
    ChecklistSubmitResult submit(SurveyViewer viewer, SubmitChecklistCommand command);

    /**
     * An independent reviewer decides a submitted revision (survey-checklist US-3): the decision, its photo marks and
     * the lead's move, in one transaction under the lead's lock.
     */
    ChecklistDecideResult decide(SurveyViewer viewer, DecideChecklistCommand command);

    /**
     * The assignee starts the next revision of a visit returned for documents (survey-checklist D3-4): its groups as
     * submitted and its usable photos carried, a DRAFT with the caller as holder; the lead's version stays.
     */
    ChecklistRevisionResult startRevision(SurveyViewer viewer, StartRevisionCommand command);
}
