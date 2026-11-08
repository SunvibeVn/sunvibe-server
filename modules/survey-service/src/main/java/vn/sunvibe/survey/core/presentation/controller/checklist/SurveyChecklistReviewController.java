package vn.sunvibe.survey.core.presentation.controller.checklist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyChecklistQueryUseCase;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyChecklistRequalificationCommandUseCase;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyChecklistReviewCommandUseCase;
import vn.sunvibe.survey.core.presentation.converter.ChecklistRequestConverter;
import vn.sunvibe.survey.core.presentation.converter.ChecklistResponseConverter;
import vn.sunvibe.survey.core.presentation.resolver.SurveyViewerResolver;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistDecisionRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistRequalificationRequest;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDecidedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRequalifiedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistReviewQueueResponse;

/**
 * The review of survey checklists (survey-checklist submission spec 7.1, property-create requalification spec 7): the
 * review queue, the decision on a submitted revision and the withdrawal of a PASSED revision from use. Sale staff and
 * ADMIN read the queue; a decision or a withdrawal needs SALE_LEADER, SALES_MANAGER or ADMIN, checked before any
 * lookup.
 */
@RestController
@RequestMapping(SurveyChecklistController.PATH)
@Tag(name = "Survey checklists (staff)")
@RequiredArgsConstructor
// survey-checklist spec 4.4: sale staff and ADMIN only; SUPER_ADMIN passes hasRole('ADMIN') through the role hierarchy
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class SurveyChecklistReviewController {

    private final SurveyChecklistQueryUseCase queryUseCase;
    private final SurveyChecklistReviewCommandUseCase reviewUseCase;
    private final SurveyChecklistRequalificationCommandUseCase requalificationUseCase;
    private final SurveyViewerResolver viewerResolver;
    private final ChecklistRequestConverter requestConverter;
    private final ChecklistResponseConverter responseConverter;

    /**
     * GET /api/v1/staff/survey-checklists/review-queue
     * <p>
     * The revisions waiting for a decision on the caller's leads (survey-checklist submission spec 7.1, S-18).
     */
    @GetMapping("/review-queue")
    @Operation(operationId = "getSurveyChecklistReviewQueue",
            summary = "List the revisions waiting for a decision (SALE: own leads only; SALE_LEADER, SALES_MANAGER, "
                    + "ADMIN, SUPER_ADMIN)",
            description = "SUBMITTED revisions of visible leads in SURVEY_SCHEDULED or ON_HOLD whose requirement has "
                    + "not changed, oldest submission first, with whether the caller can decide each one and why not.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of the queue",
                    content = @Content(schema = @Schema(implementation = ChecklistReviewQueueResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: page or size",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistReviewQueueResponse> reviewQueue(
            @Parameter(description = "Zero-based page number")
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "Page must be 0 or more.") int page,
            @Parameter(description = "Revisions per page, 1 to 50")
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "Size must be from 1 to 50.")
            @Max(value = 50, message = "Size must be from 1 to 50.") int size) {
        return ResponseEntity.ok(responseConverter.toReviewQueue(queryUseCase.reviewQueue(viewerResolver.current(), page,
                size)));
    }

    /**
     * POST /api/v1/staff/survey-checklists/{checklistId}/decision
     * <p>
     * An independent reviewer decides a submitted revision and the lead moves with it (survey-checklist submission spec
     * 4.2, 4.5, 7.1). The role is checked before any lookup.
     */
    @PostMapping("/{checklistId}/decision")
    @PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN')")
    @Operation(operationId = "decideSurveyChecklist",
            summary = "Decide a submitted survey checklist revision (SALE_LEADER, SALES_MANAGER, ADMIN, SUPER_ADMIN, "
                    + "outside the revision's excluded set)",
            description = "PASSED moves the lead to QUALIFIED; NEEDS_CHANGES DOCUMENTS_ONLY keeps it for a new revision "
                    + "on the same visit; NEEDS_CHANGES RESURVEY_REQUIRED and FAILED TEMPORARY move it to CONTACTED; "
                    + "FAILED OUT_OF_SCOPE and NOT_ACCEPTED close it as REJECTED. The reviewer's text stays in the "
                    + "decision.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The revision, the lead and the History entry",
                    content = @Content(schema = @Schema(implementation = ChecklistDecidedResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on expectedVersion, outcome, "
                    + "needsChangesKind, failedKind, reason, notes, limitations or rejectedEvidence.* (a missing or "
                    + "unknown key too); MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller's role may not decide; "
                    + "CHECKLIST_REVIEW_NOT_ALLOWED (field reviewer): the caller is in the excluded set or cannot "
                    + "sign in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION (field expectedVersion); "
                    + "CHECKLIST_DECISION_NOT_ALLOWED (fields status, lead.status, lead.intentCycle, visit, "
                    + "media.serving, and for PASSED hazards, evidence.rejected, evidence.unavailable, appointment, "
                    + "evidence.coverage)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistDecidedResponse> decide(@PathVariable("checklistId") String checklistId,
                                                           @RequestBody ChecklistDecisionRequest request) {
        return ResponseEntity.ok(responseConverter.toDecided(reviewUseCase.decide(viewerResolver.current(),
                requestConverter.toCommand(checklistId, request))));
    }

    /**
     * POST /api/v1/staff/survey-checklists/{checklistId}/requalification
     * <p>
     * A manager withdraws the lead's current PASSED revision from use and the lead goes back to a new survey
     * (property-create requalification spec 4, 5.3, 7). The role is checked before any lookup.
     */
    @PostMapping("/{checklistId}/requalification")
    @PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN')")
    @Operation(operationId = "requalifySurveyChecklist",
            summary = "Withdraw the current PASSED survey checklist revision from use (SALE_LEADER, SALES_MANAGER, "
                    + "ADMIN, SUPER_ADMIN)",
            description = "The revision stays exactly as it is and its result stops being used; the mark is never "
                    + "removed. A QUALIFIED lead moves to CONTACTED; an ON_HOLD lead stays held and can then be moved "
                    + "to CONTACTED by hand. The reason, source and scope stay on the revision, out of History and "
                    + "lists. A repeat answers 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The revision, the lead and the History entry",
                    content = @Content(schema = @Schema(implementation = ChecklistRequalifiedResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on expectedVersion, reasonKind, reason, "
                    + "eventSource, scope or basisDate (a missing or unknown key too); MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller's role may not withdraw a revision",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION (field expectedVersion); "
                    + "SURVEY_NOT_ALLOWED (field checklist): the revision is not the current PASSED of the lead's "
                    + "cycle; INVALID_STATE_TRANSITION (field status): the lead is neither QUALIFIED nor ON_HOLD",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistRequalifiedResponse> requalify(@PathVariable("checklistId") String checklistId,
                                                                  @RequestBody ChecklistRequalificationRequest request) {
        return ResponseEntity.ok(responseConverter.toRequalified(requalificationUseCase.mark(viewerResolver.current(),
                requestConverter.toCommand(checklistId, request))));
    }
}
