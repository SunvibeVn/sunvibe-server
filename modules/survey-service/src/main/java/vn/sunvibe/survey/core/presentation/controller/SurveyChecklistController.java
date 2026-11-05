package vn.sunvibe.survey.core.presentation.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.survey.core.application.usecase.SurveyChecklistCommandUseCase;
import vn.sunvibe.survey.core.application.usecase.SurveyChecklistQueryUseCase;
import vn.sunvibe.survey.core.application.usecase.SurveyChecklistRequalificationCommandUseCase;
import vn.sunvibe.survey.core.application.usecase.SurveyChecklistReviewCommandUseCase;
import vn.sunvibe.survey.core.presentation.converter.ChecklistRequestMapper;
import vn.sunvibe.survey.core.presentation.converter.ChecklistResponseMapper;
import vn.sunvibe.survey.core.presentation.converter.SurveyViewerResolver;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistDecisionRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistRequalificationRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistRevisionRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveIdentificationRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSavePartsRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveDimensionsRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveConditionRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveSafetyRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveAmenitiesRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSaveTermsRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSubmitRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistTakeoverRequest;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistCheckpointResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDecidedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRequalifiedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistReviewQueueResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRevisionStartedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSubmittedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistTakenOverResponse;

/**
 * The survey checklists of staff (survey-checklist spec 7.1): read a checklist, take a draft over, read a takeover
 * checkpoint and save one group of a draft. Sale staff and ADMIN only; a checklist of a lead the caller may not see
 * answers 404 exactly like one that does not exist, and only the lead's eligible assignee takes over (survey-checklist
 * spec 4.4). No operation deletes a checklist (spec I-6).
 */
@RestController
@RequestMapping(SurveyChecklistController.PATH)
@Tag(name = "Survey checklists (staff)")
@RequiredArgsConstructor
// survey-checklist spec 4.4: sale staff and ADMIN only; SUPER_ADMIN passes hasRole('ADMIN') through the role hierarchy
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class SurveyChecklistController {

    public static final String PATH = "/api/v1/staff/survey-checklists";

    private final SurveyChecklistQueryUseCase queryUseCase;
    private final SurveyChecklistCommandUseCase commandUseCase;
    private final SurveyChecklistReviewCommandUseCase reviewUseCase;
    private final SurveyChecklistRequalificationCommandUseCase requalificationUseCase;
    private final SurveyViewerResolver viewerResolver;
    private final ChecklistRequestMapper requestMapper;
    private final ChecklistResponseMapper responseMapper;

    private static final String SAVE_400 = "VALIDATION_FAILED, every failing rule on its JSON path (content.*, "
            + "expectedVersion, correction or correction.*; a missing or unknown key too); MALFORMED_REQUEST";
    private static final String SAVE_409 = "CHECKLIST_READ_ONLY (the reason is on the next read); "
            + "CHECKLIST_TAKEOVER_REQUIRED: the caller must take the draft over first; CONCURRENT_MODIFICATION "
            + "(field expectedVersion or otherGroupVersions.<group>)";

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
        return ResponseEntity.ok(responseMapper.toReviewQueue(queryUseCase.reviewQueue(viewerResolver.current(), page,
                size)));
    }

    /**
     * GET /api/v1/staff/survey-checklists/{checklistId}
     * <p>
     * The checklist with its groups, readiness and history (survey-checklist spec 7.2); access-logged.
     */
    @GetMapping("/{checklistId}")
    @Operation(operationId = "getSurveyChecklist",
            summary = "Read a survey checklist (SALE: own leads only; SALE_LEADER, SALES_MANAGER, ADMIN, SUPER_ADMIN)",
            description = "The checklist as stored, with the viewer's editability, the readiness computed now, the "
                    + "effective terms per space, the earlier hazards of the requirement, takeovers, corrections and "
                    + "editors. Reading it is access-logged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The checklist",
                    content = @Content(schema = @Schema(implementation = ChecklistResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistResponse> read(@PathVariable("checklistId") String checklistId) {
        return ResponseEntity.ok(responseMapper.toResponse(queryUseCase.read(viewerResolver.current(), checklistId)));
    }

    /**
     * POST /api/v1/staff/survey-checklists/{checklistId}/takeovers
     * <p>
     * The new assignee takes the draft over from its holder, keeping a private checkpoint (survey-checklist BR-14).
     */
    @PostMapping("/{checklistId}/takeovers")
    @Operation(operationId = "takeOverSurveyChecklist",
            summary = "Take a survey checklist draft over (the lead's eligible assignee only)",
            description = "The caller becomes the holder; every group as it was is kept as a checkpoint only readers "
                    + "of the lead can read. Unknown keys are refused.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The checklist and its History entry",
                    content = @Content(schema = @Schema(implementation = ChecklistTakenOverResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on expectedVersion, reason, basis or "
                    + "basis.* (a missing or unknown key too); MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CHECKLIST_READ_ONLY (the reason is on the next read); "
                    + "CHECKLIST_NOT_ALLOWED (field holder): the caller already holds it; CONCURRENT_MODIFICATION "
                    + "(field expectedVersion)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistTakenOverResponse> takeOver(@PathVariable("checklistId") String checklistId,
                                                               @RequestBody ChecklistTakeoverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseMapper.toTakenOver(commandUseCase.takeOver(
                viewerResolver.current(), requestMapper.toCommand(checklistId, request))));
    }

    /**
     * POST /api/v1/staff/survey-checklists/{checklistId}/submission
     * <p>
     * The holder freezes the revision and its photos for review (survey-checklist submission spec 4.3, 7.1).
     */
    @PostMapping("/{checklistId}/submission")
    @Operation(operationId = "submitSurveyChecklist",
            summary = "Submit a survey checklist revision for review (the holder, who is the lead's eligible assignee)",
            description = "Freezes the revision and every selected photo with its hash. The lead keeps its status and "
                    + "version. The same caller repeating a submit gets the revision back and nothing is written.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The revision; historyEntry is null on a repeat",
                    content = @Content(schema = @Schema(implementation = ChecklistSubmittedResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on expectedVersion (a missing or "
                    + "unknown key too); MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CHECKLIST_READ_ONLY; CHECKLIST_TAKEOVER_REQUIRED; "
                    + "CONCURRENT_MODIFICATION (field expectedVersion); CHECKLIST_SUBMISSION_NOT_AVAILABLE (field "
                    + "submission); CHECKLIST_NOT_READY (one error per failing gate: appointment, readiness.missing, "
                    + "media.pending, media.limit, media.unavailable)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistSubmittedResponse> submit(@PathVariable("checklistId") String checklistId,
                                                             @RequestBody ChecklistSubmitRequest request) {
        return ResponseEntity.ok(responseMapper.toSubmitted(reviewUseCase.submit(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
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
        return ResponseEntity.ok(responseMapper.toDecided(reviewUseCase.decide(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
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
        return ResponseEntity.ok(responseMapper.toRequalified(requalificationUseCase.mark(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
    }

    /**
     * POST /api/v1/staff/survey-checklists/{checklistId}/revisions
     * <p>
     * The assignee starts the next revision of a visit returned for documents (survey-checklist submission spec 4.6).
     */
    @PostMapping("/{checklistId}/revisions")
    @Operation(operationId = "startSurveyChecklistRevision",
            summary = "Start the next revision of a survey checklist returned for documents (the lead's eligible "
                    + "assignee)",
            description = "A DRAFT on the same visit with the groups as submitted and every usable photo carried. Who "
                    + "did not hold the previous revision declares where the information comes from.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The new revision and its History entry",
                    content = @Content(schema = @Schema(implementation = ChecklistRevisionStartedResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on intake or intake.* (a missing or "
                    + "unknown key too); MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CHECKLIST_NOT_ALLOWED (field status); "
                    + "CHECKLIST_ALREADY_STARTED: a later revision exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistRevisionStartedResponse> startRevision(
            @PathVariable("checklistId") String checklistId, @RequestBody ChecklistRevisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseMapper.toRevisionStarted(
                reviewUseCase.startRevision(viewerResolver.current(), requestMapper.toCommand(checklistId, request))));
    }

    /**
     * GET /api/v1/staff/survey-checklists/{checklistId}/takeovers/{takeoverNo}
     * <p>
     * The checkpoint of a takeover: every group as it was just before (survey-checklist spec 4.5); access-logged.
     */
    @GetMapping("/{checklistId}/takeovers/{takeoverNo}")
    @Operation(operationId = "getSurveyChecklistCheckpoint",
            summary = "Read a takeover checkpoint (SALE: own leads only; SALE_LEADER, SALES_MANAGER, ADMIN, "
                    + "SUPER_ADMIN)",
            description = "The takeover and the groups as they were just before it. An intake declaration has no "
                    + "checkpoint. Reading it is access-logged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The takeover and its checkpoint",
                    content = @Content(schema = @Schema(implementation = ChecklistCheckpointResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on takeoverNo: not a number",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist or checkpoint, or the "
                    + "caller may not see its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistCheckpointResponse> checkpoint(@PathVariable("checklistId") String checklistId,
                                                                  @PathVariable("takeoverNo") int takeoverNo) {
        return ResponseEntity.ok(responseMapper.toCheckpoint(queryUseCase.readCheckpoint(viewerResolver.current(),
                checklistId, takeoverNo)));
    }

    /**
     * PUT /api/v1/staff/survey-checklists/{checklistId}/identification
     * <p>
     * Save the identification group of a draft (survey-checklist spec 5.5, 7.1).
     */
    @PutMapping("/{checklistId}/identification")
    @Operation(operationId = "saveSurveyChecklistIdentification",
            summary = "Save the identification group of a survey checklist draft (the holder, who is the lead's "
                    + "eligible assignee)",
            description = "G1 identification: observed address, asset type, fit, rental scope and spaces. A space left "
                    + "out of spaces is removed and its entries in the other groups go with it; a scope change "
                    + "clears what the new scope does not hold. otherGroupVersions names each group such a save "
                    + "changes. The same content again changes nothing (200, no new version). Every key is sent; "
                    + "unknown keys and server fields are refused. A change of what someone else observed or heard "
                    + "needs a correction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The checklist after the save",
                    content = @Content(schema = @Schema(implementation = ChecklistResponse.class))),
            @ApiResponse(responseCode = "400", description = SAVE_400,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = SAVE_409,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistResponse> saveIdentification(@PathVariable("checklistId") String checklistId,
            @RequestBody ChecklistSaveIdentificationRequest request) {
        return ResponseEntity.ok(responseMapper.toResponse(commandUseCase.save(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
    }

    /**
     * PUT /api/v1/staff/survey-checklists/{checklistId}/parts
     * <p>
     * Save the parts group of a draft (survey-checklist spec 5.5, 7.1).
     */
    @PutMapping("/{checklistId}/parts")
    @Operation(operationId = "saveSurveyChecklistParts",
            summary = "Save the parts group of a survey checklist draft (the holder, who is the lead's eligible "
                    + "assignee)",
            description = "G2 parts: access route, shared areas, floors and the parts of each space. The same content "
                    + "again changes nothing (200, no new version). Every key is sent; unknown keys and server "
                    + "fields are refused. A change of what someone else observed or heard needs a correction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The checklist after the save",
                    content = @Content(schema = @Schema(implementation = ChecklistResponse.class))),
            @ApiResponse(responseCode = "400", description = SAVE_400,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = SAVE_409,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistResponse> saveParts(@PathVariable("checklistId") String checklistId,
            @RequestBody ChecklistSavePartsRequest request) {
        return ResponseEntity.ok(responseMapper.toResponse(commandUseCase.save(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
    }

    /**
     * PUT /api/v1/staff/survey-checklists/{checklistId}/dimensions
     * <p>
     * Save the dimensions group of a draft (survey-checklist spec 5.5, 7.1).
     */
    @PutMapping("/{checklistId}/dimensions")
    @Operation(operationId = "saveSurveyChecklistDimensions",
            summary = "Save the dimensions group of a survey checklist draft (the holder, who is the lead's eligible "
                    + "assignee)",
            description = "G3 dimensions: area, bedrooms and bathrooms of each space. The same content again changes "
                    + "nothing (200, no new version). Every key is sent; unknown keys and server fields are refused. "
                    + "A change of what someone else observed or heard needs a correction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The checklist after the save",
                    content = @Content(schema = @Schema(implementation = ChecklistResponse.class))),
            @ApiResponse(responseCode = "400", description = SAVE_400,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = SAVE_409,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistResponse> saveDimensions(@PathVariable("checklistId") String checklistId,
            @RequestBody ChecklistSaveDimensionsRequest request) {
        return ResponseEntity.ok(responseMapper.toResponse(commandUseCase.save(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
    }

    /**
     * PUT /api/v1/staff/survey-checklists/{checklistId}/condition
     * <p>
     * Save the condition group of a draft (survey-checklist spec 5.5, 7.1).
     */
    @PutMapping("/{checklistId}/condition")
    @Operation(operationId = "saveSurveyChecklistCondition",
            summary = "Save the condition group of a survey checklist draft (the holder, who is the lead's eligible "
                    + "assignee)",
            description = "G4 condition: occupancy, availability and the checks of the main space, kitchen and "
                    + "bathroom of each space; the shared wet areas in separate parts only. The same content again "
                    + "changes nothing (200, no new version). Every key is sent; unknown keys and server fields are "
                    + "refused. A change of what someone else observed or heard needs a correction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The checklist after the save",
                    content = @Content(schema = @Schema(implementation = ChecklistResponse.class))),
            @ApiResponse(responseCode = "400", description = SAVE_400,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = SAVE_409,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistResponse> saveCondition(@PathVariable("checklistId") String checklistId,
            @RequestBody ChecklistSaveConditionRequest request) {
        return ResponseEntity.ok(responseMapper.toResponse(commandUseCase.save(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
    }

    /**
     * PUT /api/v1/staff/survey-checklists/{checklistId}/safety
     * <p>
     * Save the safety group of a draft (survey-checklist spec 5.5, 7.1).
     */
    @PutMapping("/{checklistId}/safety")
    @Operation(operationId = "saveSurveyChecklistSafety",
            summary = "Save the safety group of a survey checklist draft (the holder, who is the lead's eligible "
                    + "assignee)",
            description = "G5 safety: egress, access restrictions and hazards (a new hazard carries a client ULID). "
                    + "The same content again changes nothing (200, no new version). Every key is sent; unknown keys "
                    + "and server fields are refused. A change of what someone else observed or heard needs a "
                    + "correction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The checklist after the save",
                    content = @Content(schema = @Schema(implementation = ChecklistResponse.class))),
            @ApiResponse(responseCode = "400", description = SAVE_400,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = SAVE_409,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistResponse> saveSafety(@PathVariable("checklistId") String checklistId,
            @RequestBody ChecklistSaveSafetyRequest request) {
        return ResponseEntity.ok(responseMapper.toResponse(commandUseCase.save(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
    }

    /**
     * PUT /api/v1/staff/survey-checklists/{checklistId}/amenities
     * <p>
     * Save the amenities group of a draft (survey-checklist spec 5.5, 7.1).
     */
    @PutMapping("/{checklistId}/amenities")
    @Operation(operationId = "saveSurveyChecklistAmenities",
            summary = "Save the amenities group of a survey checklist draft (the holder, who is the lead's eligible "
                    + "assignee)",
            description = "G6 amenities: furnishing and the checked items of the property and of each space. The same "
                    + "content again changes nothing (200, no new version). Every key is sent; unknown keys and "
                    + "server fields are refused. A change of what someone else observed or heard needs a "
                    + "correction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The checklist after the save",
                    content = @Content(schema = @Schema(implementation = ChecklistResponse.class))),
            @ApiResponse(responseCode = "400", description = SAVE_400,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = SAVE_409,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistResponse> saveAmenities(@PathVariable("checklistId") String checklistId,
            @RequestBody ChecklistSaveAmenitiesRequest request) {
        return ResponseEntity.ok(responseMapper.toResponse(commandUseCase.save(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
    }

    /**
     * PUT /api/v1/staff/survey-checklists/{checklistId}/terms
     * <p>
     * Save the terms group of a draft (survey-checklist spec 5.5, 7.1).
     */
    @PutMapping("/{checklistId}/terms")
    @Operation(operationId = "saveSurveyChecklistTerms",
            summary = "Save the terms group of a survey checklist draft (the holder, who is the lead's eligible "
                    + "assignee)",
            description = "G8 terms as surveyed or offered: rent, deposit, payment period, minimum term, charges, "
                    + "fees, house rules, pets and handover. The same content again changes nothing (200, no new "
                    + "version). Every key is sent; unknown keys and server fields are refused. A change of what "
                    + "someone else observed or heard needs a correction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The checklist after the save",
                    content = @Content(schema = @Schema(implementation = ChecklistResponse.class))),
            @ApiResponse(responseCode = "400", description = SAVE_400,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = SAVE_409,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistResponse> saveTerms(@PathVariable("checklistId") String checklistId,
            @RequestBody ChecklistSaveTermsRequest request) {
        return ResponseEntity.ok(responseMapper.toResponse(commandUseCase.save(viewerResolver.current(),
                requestMapper.toCommand(checklistId, request))));
    }
}
