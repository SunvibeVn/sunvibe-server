package vn.sunvibe.property.core.application.usecase.intake;

/** Use Case - what a completion would write, and the hash it must name (property-create intake spec 7.1, US-6 AC-1). */
public interface IntakePreviewUseCase {

    IntakePreviewView preview(String leadId, IntakeActor actor);
}
