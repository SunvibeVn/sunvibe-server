package vn.sunvibe.brokerage.core.application.shared.external.property;

import java.util.Optional;

/**
 * The intake basis of a lead, owned by property-service; the adapter lives in the app module (ADR-009) and joins the
 * caller's transaction. Called only after the caller was shown able to see the lead.
 */
public interface IntakeBasisPort {

    /** The lead's latest COMPLETED intake with its readiness and ownership today; empty when the lead has none. */
    Optional<IntakeBasisFacts> latestBasis(String leadId);
}
