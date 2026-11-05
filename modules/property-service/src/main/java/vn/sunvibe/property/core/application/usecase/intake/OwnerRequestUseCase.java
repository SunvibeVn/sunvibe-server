package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Use Case - the assignee asks an account to confirm (property-create intake spec 4.6 step 1, property-create BR-5). */
public interface OwnerRequestUseCase {

    /** The same answer for an address that matched and one that did not: routing is not proof. */
    IssuedRequest request(String leadId, IntakeActor actor, String ownerEmail, Long expectedVersion);

    /** The code shows here once, for the Sale to read out; only its hash is kept. */
    @Schema(name = "OwnerRequestIssued")
    record IssuedRequest(String requestId, String code, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedRequest[" + requestId + "]";
        }
    }
}
