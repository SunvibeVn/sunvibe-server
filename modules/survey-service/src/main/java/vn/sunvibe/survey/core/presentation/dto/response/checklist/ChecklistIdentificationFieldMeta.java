package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The metadata of the plain fields of G1 (survey-checklist spec A-26). */
@Schema(description = "The metadata of the plain fields of G1")
public record ChecklistIdentificationFieldMeta(
        @Schema(nullable = true)
        ChecklistUnitMeta rentalScope,
        @Schema(nullable = true)
        ChecklistUnitMeta differenceNote,
        @Schema(nullable = true)
        ChecklistUnitMeta otherPartsNote,
        @Schema(nullable = true)
        ChecklistUnitMeta generalNote) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistIdentificationFieldMeta[hidden]";
    }
}
