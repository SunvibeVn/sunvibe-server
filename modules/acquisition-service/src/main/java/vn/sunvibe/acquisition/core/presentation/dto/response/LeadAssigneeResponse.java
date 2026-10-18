package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO - LeadAssigneeResponse (Record)
 * <p>
 * One staff member an owner lead may be given to (acquisition-owner-lead-queue-ui spec 4.1). Only the id, the full
 * name and the assignee roles: no phone, e-mail or username.
 */
@Schema(description = "A staff member an owner lead may be assigned to")
public record LeadAssigneeResponse(
        @Schema(description = "User id; the value to send as assigneeUserId", example = "01J9Z3K8M2Q4R5T6V7W8X9Y0ZA")
        String userId,
        @Schema(description = "Full name", example = "Nguyễn Văn An") String fullName,
        @Schema(description = "Assignee roles the person holds, SALE before SALE_LEADER; never empty")
        List<AssigneeRole> roles) {

    /** The roles that make a person eligible for an owner lead. */
    @Schema(description = "A role that makes a person eligible for an owner lead")
    public enum AssigneeRole {
        /** Works the leads assigned to them. */
        SALE,
        /** Works leads like a SALE and also hands leads out. */
        SALE_LEADER
    }
}
