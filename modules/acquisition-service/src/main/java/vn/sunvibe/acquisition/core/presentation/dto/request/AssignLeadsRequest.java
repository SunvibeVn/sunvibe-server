package vn.sunvibe.acquisition.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.sunvibe.acquisition.core.application.command.AssignLeadsCommand;

import java.util.List;

/**
 * DTO - AssignLeadsRequest (Record)
 * <p>
 * Give several leads to one staff member in one action (acquisition-owner-lead-bulk-assign spec 4.1), each lead
 * guarded by the version the caller saw. The assignee and note limits are those of one assignment. Messages are
 * English and never repeat the value.
 */
@Schema(description = "Give several owner leads to one SALE or SALE_LEADER")
public record AssignLeadsRequest(
        @NotBlank(message = "Assignee user id is required.")
        @Size(max = 26, message = "Assignee user id must be at most 26 characters.")
        @Schema(description = "User id (ULID) of the new assignee", maxLength = 26,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String assigneeUserId,

        @Size(max = 2000, message = "Note must be at most 2000 characters.")
        @Schema(description = "Optional; kept in the history entry of every lead that is assigned", maxLength = 2000)
        String note,

        @NotNull(message = "Leads are required.")
        @Size(min = 1, max = AssignLeadsCommand.MAX_LEADS,
                message = "Leads must hold 1 to " + AssignLeadsCommand.MAX_LEADS + " leads.")
        @Schema(description = "1 to 20 leads, none of them twice", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull(message = "A lead is required.") @Valid LeadVersionInput> leads) {

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "AssignLeadsRequest[hidden]";
    }
}
