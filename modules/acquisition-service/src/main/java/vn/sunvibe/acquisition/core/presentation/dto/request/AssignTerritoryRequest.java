package vn.sunvibe.acquisition.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO - AssignTerritoryRequest (Record)
 * <p>
 * Give a ward to a staff member (acquisition-owner-lead-queue spec 6), replacing whoever had it.
 */
@Schema(description = "Give a ward to a SALE or SALE_LEADER")
public record AssignTerritoryRequest(
        @NotBlank(message = "User id is required.")
        @Size(max = 26, message = "User id must be at most 26 characters.")
        @Schema(description = "User id (ULID) of the person who looks after the ward", maxLength = 26,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String userId) {
}
