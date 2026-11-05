package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Use Case - completion, all or nothing (property-create intake spec 5.3, US-6). */
public interface IntakeCompletionUseCase {

    /** A repeat with the same hash answers the stored result; a duplicate found here answers 409 after its case commits. */
    CompletionResult complete(String leadId, IntakeActor actor, Long expectedVersion, String previewHash);

    /** @param mode CREATE or LINK */
    @Schema(name = "IntakeCompletionResult")
    record CompletionResult(String propertyId, String propertyCode, List<ResultUnit> units,
                            @Schema(allowableValues = {"CREATE", "LINK"}) String mode, Instant completedAt) {
    }

    @Schema(name = "IntakeCompletionUnit")
    record ResultUnit(String unitId, String unitCode) {
    }
}
