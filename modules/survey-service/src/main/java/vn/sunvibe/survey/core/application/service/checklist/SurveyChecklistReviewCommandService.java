package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.survey.core.application.command.checklist.DecideChecklistCommand;
import vn.sunvibe.survey.core.application.command.checklist.StartRevisionCommand;
import vn.sunvibe.survey.core.application.command.checklist.SubmitChecklistCommand;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistDecideResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistRevisionResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistSubmitResult;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyChecklistReviewCommandUseCase;

/**
 * Use Case Implementation - submission, review and revisions (survey-checklist submission spec 4.3..4.6, 5.4, 5.5). One
 * transaction per command, every rule read from rows taken under the locks of spec 5.4, in the one lock order: lead,
 * checklist, media sets in id order, photos in id order, activity. Each command runs in its own class:
 * {@link ChecklistSubmitter}, {@link ChecklistDecider} and {@link ChecklistRevisionStarter}, sharing
 * {@link ChecklistReviewSteps}.
 */
@Service
@Transactional
public class SurveyChecklistReviewCommandService implements SurveyChecklistReviewCommandUseCase {

    private final ChecklistSubmitter submitter;
    private final ChecklistRevisionStarter revisionStarter;
    private final ChecklistDecider decider;

    public SurveyChecklistReviewCommandService(ChecklistSubmitter submitter, ChecklistRevisionStarter revisionStarter,
                                               ChecklistDecider decider) {
        this.submitter = submitter;
        this.revisionStarter = revisionStarter;
        this.decider = decider;
    }

    /** Submits a revision in this transaction; {@link ChecklistSubmitter#submit} holds the check order. */
    @Override
    public ChecklistSubmitResult submit(SurveyViewer viewer, SubmitChecklistCommand command) {
        return submitter.submit(viewer, command);
    }

    /**
     * Decides a submitted revision outside this class's transaction: {@link ChecklistDecider#decide} runs the decision
     * and the lead move in one transaction of their own, then builds the answer after it ends (spec 9.5).
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChecklistDecideResult decide(SurveyViewer viewer, DecideChecklistCommand command) {
        return decider.decide(viewer, command);
    }

    /** Starts the next revision in this transaction; {@link ChecklistRevisionStarter#startRevision} holds the order. */
    @Override
    public ChecklistRevisionResult startRevision(SurveyViewer viewer, StartRevisionCommand command) {
        return revisionStarter.startRevision(viewer, command);
    }
}
