package vn.sunvibe.property.core.application.usecase.intake;

/** Use Case - a new checked proof takes over a result (property-create intake spec 4.2, 4.11, E-13). */
public interface IntakeRestorationUseCase {

    IntakeBlockView restore(String leadId, IntakeActor actor, Long expectedVersion);
}
