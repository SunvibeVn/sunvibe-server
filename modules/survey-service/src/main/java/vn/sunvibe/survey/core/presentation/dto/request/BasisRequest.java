package vn.sunvibe.survey.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO - BasisRequest (Record)
 * <p>
 * What a proxy record or a resumption relies on (survey-schedule BR-9): the Vietnam date of the confirmation (not in
 * the future, not before the event it confirms) and a short text.
 */
@Schema(description = "The basis of a proxy visit record or a resumption")
public record BasisRequest(
        @NotNull(message = "Confirmation date is required.")
        @Schema(description = "Vietnam date", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate confirmedOn,

        @NotNull(message = "Basis text is required.")
        @Size(max = 500, message = "Basis text must be at most 500 characters.")
        @Schema(maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        String text) {

    @Override
    public String toString() {
        return "BasisRequest[" + confirmedOn + "]";
    }
}
