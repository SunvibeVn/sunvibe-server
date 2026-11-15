package vn.sunvibe.property.core.application.usecase.intake;

import java.util.List;

/**
 * The latest COMPLETED intake of a lead, as {@link CompletedIntakeUseCase} publishes it.
 *
 * @param intakeId      the intake row
 * @param propertyId    its result property
 * @param readiness     its readiness, as {@link InventoryIntakeReadinessUseCase#readiness} computes it
 * @param ownerIdOrNull the declared owner of the result property; null when it has none
 * @param holdings      every ownership row of the result property valid today, any relation and verification, ordered
 *                      by user and relation
 */
public record CompletedIntakeView(String intakeId, String propertyId, IntakeBlockView.Readiness readiness,
                                  String ownerIdOrNull, List<OwnershipHolding> holdings) {

    public CompletedIntakeView {
        holdings = List.copyOf(holdings);
    }

    /** One ownership row valid today, its values as stored. */
    public record OwnershipHolding(String userId, String relationType, String verificationState) {
    }
}
