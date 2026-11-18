package vn.sunvibe.property.core.application.usecase.intake;

import java.util.Optional;

/**
 * Use Case - where a COMPLETED intake came from: its lead, intent cycle, source revision and the survey space each mapped
 * unit came from (brokerage-agreement E05-S3 spec S3-D17). Published for brokerage-service's survey proposal (ADR-009),
 * read-only, in the caller's transaction.
 * <p>
 * It has no viewer of its own: it is called only by an operation that has already checked that the caller may see the
 * lead, and its adapter never exposes it over HTTP. Only an adapter in vn.sunvibe.integration calls it
 * (ViewerlessReadArchTest).
 */
public interface IntakeOriginUseCase {

    /** The origin of the intake; empty for an unknown id and for an intake that is not COMPLETED. */
    Optional<IntakeOriginView> originOf(String intakeId);
}
