package vn.sunvibe.acquisition.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.acquisition.core.application.query.LeadListQuery;
import vn.sunvibe.acquisition.core.application.query.LeadScope;
import vn.sunvibe.acquisition.core.application.usecase.OwnerLeadCommandUseCase;
import vn.sunvibe.acquisition.core.application.usecase.OwnerLeadQueryUseCase;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.presentation.converter.LeadViewerConverter;
import vn.sunvibe.acquisition.core.presentation.converter.OwnerLeadRequestMapper;
import vn.sunvibe.acquisition.core.presentation.converter.OwnerLeadResponseMapper;
import vn.sunvibe.acquisition.core.presentation.dto.request.AddLeadNoteRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.AssignLeadRequest;
import vn.sunvibe.acquisition.core.presentation.dto.request.ChangeLeadStatusRequest;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadNoteResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.LeadPageResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.OwnerLeadDetailResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.OwnerLeadSummaryResponse;
import vn.sunvibe.acquisition.core.presentation.dto.response.SecurityError;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The staff owner-lead queue (acquisition-owner-lead-queue spec 5.5, 6). Only sale staff and ADMIN get in; a SALE
 * sees and works only the leads assigned to them, and a lead they may not see answers 404 exactly like a lead that
 * does not exist (acquisition-owner-lead-queue BR-2).
 */
@RestController
@RequestMapping(OwnerLeadController.PATH)
@Tag(name = "Owner leads (staff)")
@RequiredArgsConstructor
// acquisition-owner-lead-queue BR-1: sale staff and ADMIN only; SUPER_ADMIN passes hasRole('ADMIN') through the role
// hierarchy
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class OwnerLeadController {

    public static final String PATH = "/api/v1/staff/owner-leads";

    private final OwnerLeadQueryUseCase queryUseCase;
    private final OwnerLeadCommandUseCase commandUseCase;
    private final LeadViewerConverter viewerConverter;
    private final OwnerLeadRequestMapper requestMapper;
    private final OwnerLeadResponseMapper responseMapper;
    private final RoleHierarchy roleHierarchy;

    /**
     * GET /api/v1/staff/owner-leads
     * <p>
     * One page of the queue, newest first. Listing is not recorded in the access log.
     */
    @GetMapping
    @Operation(summary = "List owner leads (SALE, SALE_LEADER, SALES_MANAGER, ADMIN, SUPER_ADMIN)",
            description = "scope=mine (default): leads assigned to the caller; unassigned: status NEW; all: every "
                    + "lead. A SALE may only use mine. The items hold no phone, email, address or coordinates.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of leads",
                    content = @Content(schema = @Schema(implementation = LeadPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on scope, status, wardCode, page or "
                    + "size",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or a SALE asked "
                    + "for scope unassigned or all",
                    content = @Content(schema = @Schema(implementation = SecurityError.class)))
    })
    public ResponseEntity<LeadPageResponse> list(
            @Parameter(schema = @Schema(allowableValues = {"mine", "unassigned", "all"}, defaultValue = "mine"))
            @RequestParam(name = "scope", defaultValue = "mine")
            @Pattern(regexp = "mine|unassigned|all", message = "Scope must be mine, unassigned or all.")
            String scope,
            @Parameter(description = "Keep only these statuses (repeat the parameter); all when left out")
            @RequestParam(name = "status", required = false) List<OwnerLeadStatus> status,
            @Parameter(description = "Keep only leads of this ward", example = "20242",
                    schema = @Schema(maxLength = 10))
            @RequestParam(name = "wardCode", required = false)
            @Size(max = 10, message = "Ward code must be at most 10 characters.") String wardCode,
            @Parameter(description = "Zero-based page number")
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "Page must be 0 or more.") int page,
            @Parameter(description = "Leads per page, 1 to 100")
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "Size must be from 1 to 100.")
            @Max(value = 100, message = "Size must be from 1 to 100.") int size) {

        LeadViewer viewer = viewer();
        LeadScope leadScope = LeadScope.valueOf(scope.toUpperCase(Locale.ROOT));
        // acquisition-owner-lead-queue US-2 AC-1: a SALE works only the leads assigned to them, so asking for the
        // unassigned leads or the whole queue is refused before anything is read
        if (viewer.access() == LeadAccess.OWN && leadScope != LeadScope.MINE) {
            throw new AccessDeniedException("A SALE may only list the leads assigned to them.");
        }
        // An empty status parameter converts to null: it filters nothing
        Set<OwnerLeadStatus> statuses = status == null ? Set.of()
                : status.stream().filter(Objects::nonNull).collect(Collectors.toSet());

        LeadListQuery query = new LeadListQuery(leadScope, statuses, wardCode, page, size);
        return ResponseEntity.ok(responseMapper.toPage(queryUseCase.list(viewer, query)));
    }

    /**
     * GET /api/v1/staff/owner-leads/{leadId}
     * <p>
     * The full lead, and one access-log row for the caller (acquisition-owner-lead-queue BR-7).
     */
    @GetMapping("/{leadId}")
    @Operation(summary = "Open an owner lead (SALE: own leads only; SALE_LEADER, SALES_MANAGER, ADMIN, SUPER_ADMIN)",
            description = "Contact data, consent, possible duplicates and history. Every call is recorded in the "
                    + "access log. A lead the caller may not see answers 404 like a lead that does not exist.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The lead",
                    content = @Content(schema = @Schema(implementation = OwnerLeadDetailResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such lead, or the caller may not see it",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OwnerLeadDetailResponse> get(@PathVariable("leadId") String leadId) {
        return ResponseEntity.ok(responseMapper.toDetail(queryUseCase.get(viewer(), leadId)));
    }

    /**
     * POST /api/v1/staff/owner-leads/{leadId}/assign
     * <p>
     * Give the lead to a SALE or SALE_LEADER; the previous holder loses access at once
     * (acquisition-owner-lead-queue US-3).
     */
    @PostMapping("/{leadId}/assign")
    @Operation(summary = "Assign an owner lead (SALE_LEADER, SALES_MANAGER, ADMIN, SUPER_ADMIN)",
            description = "The current assignee again changes nothing (200, same version).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The lead after the assignment",
                    content = @Content(schema = @Schema(implementation = OwnerLeadSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on assigneeUserId, expectedVersion "
                    + "or note; MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not SALE_LEADER, SALES_MANAGER or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION (field expectedVersion) or "
                    + "INVALID_STATE_TRANSITION (field status: the lead is closed)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "INELIGIBLE_ASSIGNEE (field assigneeUserId): unknown "
                    + "user, cannot sign in, or neither SALE nor SALE_LEADER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    // acquisition-owner-lead-queue BR-1: only leaders, sales managers and ADMIN hand out leads
    @PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN')")
    public ResponseEntity<OwnerLeadSummaryResponse> assign(@PathVariable("leadId") String leadId,
                                                           @Valid @RequestBody AssignLeadRequest request) {
        return ResponseEntity.ok(responseMapper.toSummary(
                commandUseCase.assign(viewer(), requestMapper.toCommand(leadId, request))));
    }

    /**
     * POST /api/v1/staff/owner-leads/{leadId}/status-changes
     * <p>
     * Move the lead along the pipeline (acquisition module brief BR-3).
     */
    @PostMapping("/{leadId}/status-changes")
    @Operation(summary = "Change the status of an owner lead (SALE: own leads only; SALE_LEADER, SALES_MANAGER, "
            + "ADMIN, SUPER_ADMIN)",
            description = "Closing (DUPLICATE, REJECTED, WITHDRAWN) needs a reason; DUPLICATE also needs "
                    + "duplicateOfLeadId, a lead the caller can see.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The lead after the change",
                    content = @Content(schema = @Schema(implementation = OwnerLeadSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on toStatus, expectedVersion, "
                    + "reason, duplicateOfLeadId or note; MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such lead, or the caller may not see it",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION (field toStatus, "
                    + "allowedValues lists the statuses allowed now) or CONCURRENT_MODIFICATION (field "
                    + "expectedVersion)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OwnerLeadSummaryResponse> changeStatus(@PathVariable("leadId") String leadId,
                                                                 @Valid @RequestBody ChangeLeadStatusRequest request) {
        return ResponseEntity.ok(responseMapper.toSummary(
                commandUseCase.changeStatus(viewer(), requestMapper.toCommand(leadId, request))));
    }

    /**
     * POST /api/v1/staff/owner-leads/{leadId}/activities
     * <p>
     * Record a note, optionally with the next action time (acquisition-owner-lead-queue BR-8).
     */
    @PostMapping("/{leadId}/activities")
    @Operation(summary = "Add a note to an owner lead (SALE: own leads only; SALE_LEADER, SALES_MANAGER, ADMIN, "
            + "SUPER_ADMIN)",
            description = "A note with nextActionAt also updates the lead (new version); without it the lead "
                    + "row is unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The new history entry and the lead after it",
                    content = @Content(schema = @Schema(implementation = LeadNoteResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on note or nextActionAt; "
                    + "MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such lead, or the caller may not see it",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION (field status): the lead is "
                    + "closed; or CONCURRENT_MODIFICATION (no field): a note with nextActionAt lost to a change "
                    + "saved at the same time",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LeadNoteResponse> addNote(@PathVariable("leadId") String leadId,
                                                    @Valid @RequestBody AddLeadNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseMapper.toNote(
                commandUseCase.addNote(viewer(), requestMapper.toCommand(leadId, request))));
    }

    private LeadViewer viewer() {
        return viewerConverter.toViewer(SecurityContextHolder.getContext().getAuthentication(), roleHierarchy);
    }
}
