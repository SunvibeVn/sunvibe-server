package vn.sunvibe.survey.core.application.usecase;

import java.util.List;
import java.util.Optional;

/**
 * Use Case - SurveyIntakeSourceUseCase
 * <p>
 * What survey-service publishes for the inventory intake (property-create intake spec 5.1, ADR-009): the frozen source of a
 * cycle's current unmarked PASSED. Only inside the caller's transaction, so one read sees one state of the record. It
 * writes nothing, and a closed lead is the intake gate's question, not this one's.
 */
public interface SurveyIntakeSourceUseCase {

    /** The current unmarked PASSED of the cycle; empty when the cycle has none. */
    Optional<IntakeSourceView> currentSource(String leadId, int intentCycle);

    /** Every marked revision of the lead, in any cycle, also when no PASSED is current (spec D5-12, 4.11). */
    List<IntakeSourceView.MarkedRevision> markedRevisions(String leadId);
}
