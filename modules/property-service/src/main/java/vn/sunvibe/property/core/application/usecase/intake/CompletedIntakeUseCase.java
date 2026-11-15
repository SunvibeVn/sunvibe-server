package vn.sunvibe.property.core.application.usecase.intake;

import java.util.Optional;

/**
 * Use Case - the lead's latest COMPLETED intake with its readiness and the ownership of its result property today, read
 * in one transaction (brokerage-agreement eligibility, E05-S1 spec 5.4). Published for brokerage-service (ADR-009).
 * <p>
 * It has no viewer of its own: it is called only by an operation that has already checked that the caller may see the
 * lead, and its adapter never exposes it over HTTP.
 */
public interface CompletedIntakeUseCase {

    /**
     * The latest COMPLETED intake of the lead; empty for an unknown or malformed lead id and for a lead with none. The
     * readiness is the one {@link InventoryIntakeReadinessUseCase#readiness} computes.
     */
    Optional<CompletedIntakeView> latest(String leadId);
}
