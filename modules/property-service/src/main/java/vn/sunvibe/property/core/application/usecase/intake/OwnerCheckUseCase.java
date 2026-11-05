package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Use Case - the manager's owner check (property-create intake spec 4.6 step 6, D5-7). */
public interface OwnerCheckUseCase {

    OwnerCheckContext context(String leadId, IntakeActor actor);

    StartedCheck start(String leadId, IntakeActor actor, String proofId, String basis, Long expectedVersion);

    IntakeBlockView refuse(String leadId, IntakeActor actor, String proofId, String basis, Long expectedVersion);

    /** The phrase shows here once, for the manager to read out; only its hash is kept. */
    @Schema(name = "OwnerCheckStarted")
    record StartedCheck(String phrase, Instant expiresAt) {

        @Override
        public String toString() {
            return "StartedCheck[" + expiresAt + "]";
        }
    }
}
