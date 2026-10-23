package vn.sunvibe.survey.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO - ProxyRequest (Record)
 * <p>
 * Makes a visit record a proxy record (survey-schedule US-3 AC-1a, BR-9): why someone other than the performer enters
 * it, and on what basis. Managers only.
 */
@Schema(description = "Reason and basis of a proxy visit record")
public record ProxyRequest(
        @NotNull(message = "Proxy reason is required.")
        @Size(max = 500, message = "Proxy reason must be at most 500 characters.")
        @Schema(maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,

        @NotNull(message = "Basis is required.")
        @Valid
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        BasisRequest basis) {

    @Override
    public String toString() {
        return "ProxyRequest[hidden]";
    }
}
