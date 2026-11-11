package vn.sunvibe.acquisition.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO - LeadVersionInput (Record)
 * <p>
 * One lead of a bulk assignment and the version the caller saw (acquisition-owner-lead-bulk-assign spec 4.1). A lead id
 * that is not a ULID is not refused here: its outcome is NOT_FOUND. Messages are English and never repeat the value.
 */
@Schema(description = "One lead of a bulk assignment and the version the caller saw")
public record LeadVersionInput(
        @NotBlank(message = "Lead id is required.")
        @Size(max = 26, message = "Lead id must be at most 26 characters.")
        @Schema(description = "Lead id (ULID)", maxLength = 26, requiredMode = Schema.RequiredMode.REQUIRED)
        String leadId,

        @NotNull(message = "Expected version is required.")
        @PositiveOrZero(message = "Expected version must be 0 or more.")
        @Schema(description = "The lead version the caller last saw (the version of the list item)", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long expectedVersion) {
}
