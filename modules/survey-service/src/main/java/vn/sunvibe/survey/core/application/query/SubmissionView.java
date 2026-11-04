package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;

import java.util.List;

/** A submission with its frozen photos (survey-checklist submission spec 7.2). */
public record SubmissionView(ChecklistSubmission submission, List<EvidenceView> evidence) {

    public SubmissionView {
        evidence = List.copyOf(evidence);
    }
}
