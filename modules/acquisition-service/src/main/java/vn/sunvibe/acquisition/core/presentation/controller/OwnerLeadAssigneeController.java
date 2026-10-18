package vn.sunvibe.acquisition.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.acquisition.core.application.usecase.LeadAssigneeQueryUseCase;
import vn.sunvibe.acquisition.core.presentation.converter.LeadAssigneeResponseMapper;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadAssigneeResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.SecurityError;

import java.util.List;

/**
 * The staff members an owner lead may be given to (acquisition-owner-lead-queue-ui spec 4.1). The path sits beside
 * the owner-leads resource, not under it, so it can never be read as a lead id.
 */
@RestController
@RequestMapping(OwnerLeadAssigneeController.PATH)
@Tag(name = "Owner leads (staff)")
@RequiredArgsConstructor
// acquisition-owner-lead-queue-ui spec 4.1: only the people who hand out leads; SUPER_ADMIN passes hasRole('ADMIN')
// through the role hierarchy. A SALE cannot assign, so it has no use for the list.
@PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN')")
public class OwnerLeadAssigneeController {

    public static final String PATH = "/api/v1/staff/owner-lead-assignees";

    private final LeadAssigneeQueryUseCase assigneeUseCase;
    private final LeadAssigneeResponseMapper responseMapper;

    /**
     * GET /api/v1/staff/owner-lead-assignees
     * <p>
     * Every person the assign call accepts, sorted by name; no paging.
     */
    @GetMapping
    @Operation(summary = "List the staff an owner lead may be assigned to (SALE_LEADER, SALES_MANAGER, ADMIN, "
            + "SUPER_ADMIN)",
            description = "Accounts that exist, may sign in and hold SALE or SALE_LEADER, sorted by name "
                    + "(Vietnamese order), then id; at most 500. Each item holds the user id, the full name and the "
                    + "assignee roles, nothing else. Not paged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The people who may hold a lead",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = LeadAssigneeResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not SALE_LEADER, SALES_MANAGER or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class)))
    })
    public ResponseEntity<List<LeadAssigneeResponse>> list() {
        return ResponseEntity.ok(responseMapper.toResponses(assigneeUseCase.listEligible()));
    }
}
