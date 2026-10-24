package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSource;

import java.time.Instant;
import java.time.LocalDate;

/** Who last changed a unit and who observed or heard its value (survey-checklist spec 4.5, 7.2). */
@Schema(description = "Who last changed a unit and who observed or heard its value")
public record ChecklistUnitMeta(
        String enteredByUserId,
        @Schema(nullable = true)
        String enteredByName,
        Instant enteredAt,
        @Schema(nullable = true, description = "The performer for on-site values, the receiver for owner statements, "
                + "null for the owner form or a note")
        String observedByUserId,
        @Schema(nullable = true)
        String observedByName,
        @Schema(nullable = true)
        LocalDate observedOn,
        @Schema(nullable = true, description = "address, askingPrice or availableFrom for an owner form value")
        String formField,
        @Schema(nullable = true, description = "The source the unit last held, also after it was cleared")
        ChecklistSource lastSource,
        @Schema(nullable = true, description = "The save whose correction covered the last change")
        String correctionSaveId) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistUnitMeta[" + enteredByUserId + "]";
    }
}
