package vn.sunvibe.property.core.application.usecase.intake;

import java.util.List;

/** Use Case - where an intake goes: a new property, or a link to one the owner holds (property-create intake spec 4.7, 4.8). */
public interface IntakeTargetUseCase {

    IntakeBlockView target(String leadId, IntakeActor actor, TargetCommand command, Long expectedVersion);

    /**
     * @param mode                   CREATE or LINK
     * @param propertyIdOrNull       only with LINK
     * @param canonicalSpaceIdOrNull only with CREATE
     * @param mappings               only with LINK
     */
    record TargetCommand(String mode, String propertyIdOrNull, String canonicalSpaceIdOrNull, List<Mapping> mappings) {

        public record Mapping(String spaceId, String unitId) {
        }
    }
}
