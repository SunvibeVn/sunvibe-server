package vn.sunvibe.acquisition.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO - AssignLeadRequest (Record)
 * <p>
 * Give a lead to a staff member (acquisition-owner-lead-queue spec 6), guarded by the version the caller last saw.
 * Messages are English and never repeat the value.
 */
@Schema(description = "Give an owner lead to a SALE or SALE_LEADER")
public record AssignLeadRequest(
        @NotBlank(message = "Assignee user id is required.")
        @Size(max = 26, message = "Assignee user id must be at most 26 characters.")
        @Schema(description = "User id (ULID) of the new assignee", maxLength = 26,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String assigneeUserId,

        @NotNull(message = "Expected version is required.")
        @PositiveOrZero(message = "Expected version must be 0 or more.")
        @Schema(description = "The lead version the caller last saw", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long expectedVersion,

        @Size(max = 2000, message = "Note must be at most 2000 characters.")
        @Schema(description = "Optional; kept in the history entry of the assignment", maxLength = 2000)
        String note) {

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "AssignLeadRequest[hidden]";
    }
}
