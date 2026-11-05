package vn.sunvibe.property.core.application.usecase.intake;

/**
 * Use Case - the readiness of a lead's latest result, computed on every read (property-create intake spec 4.11). Published for the
 * features that come after the intake; a lead with no result answers NONE.
 */
public interface InventoryIntakeReadinessUseCase {

    IntakeBlockView.Readiness readiness(String leadId);
}
