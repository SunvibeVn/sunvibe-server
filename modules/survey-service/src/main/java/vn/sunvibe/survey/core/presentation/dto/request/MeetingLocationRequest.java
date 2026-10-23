package vn.sunvibe.survey.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO - MeetingLocationRequest (Record)
 * <p>
 * Where staff meet the owner (survey-schedule BR-7): an address, a ward of the catalog in its province, an optional
 * pin (both coordinates or none). Checked by the intake location rules; never logged.
 */
@Schema(description = "A meeting place: address, province and ward of the catalog, optional pin")
public record MeetingLocationRequest(
        @NotNull(message = "Address is required.")
        @Size(max = 255, message = "Address must be at most 255 characters.")
        @Schema(description = "1..255 characters, no phone number", maxLength = 255,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String addressText,

        @NotNull(message = "Province is required.")
        @Size(max = 10, message = "Province code must be at most 10 characters.")
        @Schema(example = "48", maxLength = 10, requiredMode = Schema.RequiredMode.REQUIRED)
        String provinceCode,

        @NotNull(message = "Ward is required.")
        @Size(max = 10, message = "Ward code must be at most 10 characters.")
        @Schema(example = "20242", maxLength = 10, requiredMode = Schema.RequiredMode.REQUIRED)
        String wardCode,

        @Schema(description = "Optional; with longitude, inside the served area, never for a special-zone ward",
                example = "16.0678")
        BigDecimal latitude,

        @Schema(description = "Optional; with latitude", example = "108.2208")
        BigDecimal longitude) {

    @Override
    public String toString() {
        return "MeetingLocationRequest[hidden]";
    }
}
