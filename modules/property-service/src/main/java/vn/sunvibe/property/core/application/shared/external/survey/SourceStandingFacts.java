package vn.sunvibe.property.core.application.shared.external.survey;

import java.util.List;

/**
 * The standing of a lead's source for a cycle, without its content: what {@link IntakeSourcePort#currentSource} answers as
 * revision, usability and reason (null, false, null when the cycle has no current PASSED) and what
 * {@link IntakeSourcePort#markedRevisions} answers.
 */
public record SourceStandingFacts(String revisionIdOrNull, boolean usable, String reasonOrNull,
                                  List<IntakeSource.MarkedRevision> markedRevisions) {

    public SourceStandingFacts {
        markedRevisions = List.copyOf(markedRevisions);
    }
}
