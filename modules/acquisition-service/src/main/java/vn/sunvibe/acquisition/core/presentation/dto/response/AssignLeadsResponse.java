package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.acquisition.core.application.query.AssignLeadsResult;

import java.util.List;

/**
 * DTO - AssignLeadsResponse (Record)
 * <p>
 * Result of a bulk assignment (acquisition-owner-lead-bulk-assign spec 4.1, D-3): one outcome per lead of the request,
 * in request order. Only the lead id and the outcome: no summary, request code or name.
 */
@Schema(description = "One outcome per lead of the request, in request order")
public record AssignLeadsResponse(
        @Schema(description = "One item per item of leads, same order and length",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<LeadAssignOutcomeResponse> results) {

    /** The outcome of one lead of the request. */
    @Schema(description = "The outcome of one lead of a bulk assignment")
    public record LeadAssignOutcomeResponse(
            @Schema(description = "The leadId of that request item, as sent", requiredMode = Schema.RequiredMode.REQUIRED)
            String leadId,

            @Schema(description = "ASSIGNED: given to the assignee; ALREADY_ASSIGNED: already theirs, nothing written; "
                    + "STALE: changed since the caller saw it, nothing written; CLOSED: the lead is closed; "
                    + "NOT_FOUND: no such lead, or the caller may not see it",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            AssignLeadsResult.Outcome outcome) {
    }
}
