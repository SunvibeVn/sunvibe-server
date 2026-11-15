package vn.sunvibe.survey.core.application.usecase.checklist;

import java.util.List;

/**
 * The standing of a lead's source for a cycle, without the source content (ADR-012 decision 2, row 2): what
 * {@link SurveyIntakeSourceUseCase#currentSource} answers as {@code revisionId}, {@code usable} and {@code reasonOrNull}
 * (null, false, null when the cycle has no current PASSED) and what {@link SurveyIntakeSourceUseCase#markedRevisions}
 * answers.
 */
public record SourceStandingView(String revisionIdOrNull, boolean usable, String reasonOrNull,
                                 List<IntakeSourceView.MarkedRevision> markedRevisions) {

    public SourceStandingView {
        markedRevisions = List.copyOf(markedRevisions);
    }
}
