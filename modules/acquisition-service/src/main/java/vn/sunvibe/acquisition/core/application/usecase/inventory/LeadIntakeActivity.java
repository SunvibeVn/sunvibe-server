package vn.sunvibe.acquisition.core.application.usecase.inventory;

import java.time.Instant;

/**
 * One inventory intake event for the lead History (property-create intake spec 4.12): the kind, the actor, and the intake
 * with its number within the lead. No text: nothing here can carry a property, owner, code or address (BR-14).
 *
 * @param actorUserIdOrNull null for an owner's withdrawal, so no owner name appears
 * @param intakeIdOrNull    null with the number, for a suspension recorded before any intake
 */
public record LeadIntakeActivity(LeadIntakeEvent event, String actorUserIdOrNull, String intakeIdOrNull,
                                 Integer intakeNoOrNull, Instant occurredAt) {
}
