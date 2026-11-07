package vn.sunvibe.survey.core.application.query.checklist;

import vn.sunvibe.survey.core.application.query.media.EvidenceView;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;

import java.util.List;

/** A submission with its frozen photos (survey-checklist submission spec 7.2). */
public record SubmissionView(ChecklistSubmission submission, List<EvidenceView> evidence) {

    public SubmissionView {
        evidence = List.copyOf(evidence);
    }
}
