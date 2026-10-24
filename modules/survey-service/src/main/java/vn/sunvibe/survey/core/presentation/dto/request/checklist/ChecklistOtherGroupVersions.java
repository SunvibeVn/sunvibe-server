package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The versions of the groups a G1 save cascades into, as the caller read them (survey-checklist spec A-12). */
@Schema(description = "The versions of the groups a G1 save cascades into, as the caller read them")
public record ChecklistOtherGroupVersions(
        @Schema(nullable = true)
        Long parts,
        @Schema(nullable = true)
        Long dimensions,
        @Schema(nullable = true)
        Long condition,
        @Schema(nullable = true)
        Long safety,
        @Schema(nullable = true)
        Long amenities,
        @Schema(nullable = true)
        Long terms) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistOtherGroupVersions[" + parts + "]";
    }
}
