package vn.sunvibe.property.core.application.usecase.intake;

/** Reads the intake block of a lead (property-create intake spec 4.1, 7.1). */
public interface IntakeReadUseCase {

    /**
     * The block for anyone on staff who can see the lead; it changes no intake, request, proof or case and writes one access
     * log row (property-create intake spec 4.12).
     *
     * @throws vn.sunvibe.property.core.application.exception.IntakeRefusedException NOT_FOUND when the lead is not visible
     */
    IntakeBlockView block(String leadId, IntakeActor actor);
}
