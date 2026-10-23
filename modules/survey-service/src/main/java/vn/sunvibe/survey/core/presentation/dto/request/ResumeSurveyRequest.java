package vn.sunvibe.survey.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO - ResumeSurveyRequest (Record)
 * <p>
 * Resume the survey work of a held lead from its current visit (survey-schedule BR-11): a reason and the owner's
 * confirmation that the requirement and scope have not changed.
 */
@Schema(description = "Resume the survey work of a held owner lead")
public record ResumeSurveyRequest(
        @NotNull(message = "Expected lead version is required.")
        @PositiveOrZero(message = "Expected lead version must be 0 or more.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedLeadVersion,

        @NotNull(message = "Reason is required.")
        @Size(max = 500, message = "Reason must be at most 500 characters.")
        @Schema(maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,

        @NotNull(message = "Basis is required.")
        @Valid
        @Schema(description = "Dated from the day the lead went on hold", requiredMode = Schema.RequiredMode.REQUIRED)
        BasisRequest basis) {

    @Override
    public String toString() {
        return "ResumeSurveyRequest[hidden]";
    }
}
