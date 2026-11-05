package vn.sunvibe.property.core.application.shared.external.survey;

import java.util.List;
import java.util.Optional;

/**
 * The frozen survey source of an intake, owned by survey-service; the adapter lives in the app module (ADR-009). Both reads
 * join the caller's transaction and refuse to run without one.
 */
public interface IntakeSourcePort {

    /** The current unmarked PASSED of the cycle; empty when the cycle has none. */
    Optional<IntakeSource> currentSource(String leadId, int intentCycle);

    /** Every marked revision of the lead, also when no PASSED is current (property-create intake D5-12, 4.11). */
    List<IntakeSource.MarkedRevision> markedRevisions(String leadId);
}
