package vn.sunvibe.property.core.application.usecase.intake;

/** Use Case - a manager's processing restriction (property-create intake spec 4.11, D5-16). */
public interface IntakeSuspensionUseCase {

    /** @param unitIdOrNull a unit mapped by a COMPLETED intake of the lead; null for the whole lead */
    IntakeBlockView suspend(String leadId, IntakeActor actor, String unitIdOrNull, String reason, Long expectedVersion);

    IntakeBlockView release(String leadId, IntakeActor actor, String suspensionId, String releaseReason, Long expectedVersion);
}
