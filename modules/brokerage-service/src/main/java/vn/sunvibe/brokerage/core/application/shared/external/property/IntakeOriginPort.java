package vn.sunvibe.brokerage.core.application.shared.external.property;

import java.util.Optional;

/**
 * Port - where the intake a version rests on came from (E05-S3 spec S3-D11, S3-D17; ADR-009). Viewer-less: brokerage
 * calls it only after it checked that its caller may see the agreement's basis lead. It joins the caller's transaction.
 */
public interface IntakeOriginPort {

    /** The origin of a COMPLETED intake; empty for an unknown id or an intake in another status. */
    Optional<IntakeOriginFacts> originOf(String intakeId);
}
