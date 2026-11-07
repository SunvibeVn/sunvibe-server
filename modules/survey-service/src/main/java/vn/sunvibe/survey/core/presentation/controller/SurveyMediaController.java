package vn.sunvibe.survey.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import vn.sunvibe.survey.core.application.query.media.MediaCreateResult;
import vn.sunvibe.survey.core.application.usecase.media.SurveyMediaCommandUseCase;
import vn.sunvibe.survey.core.application.usecase.media.SurveyMediaQueryUseCase;
import vn.sunvibe.survey.core.presentation.converter.MediaRequestConverter;
import vn.sunvibe.survey.core.presentation.converter.MediaResponseConverter;
import vn.sunvibe.survey.core.presentation.resolver.SurveyViewerResolver;
import vn.sunvibe.survey.core.presentation.dto.request.media.MediaCreateRequest;
import vn.sunvibe.survey.core.presentation.dto.request.media.MediaHoldReleaseRequest;
import vn.sunvibe.survey.core.presentation.dto.request.media.MediaHoldRequest;
import vn.sunvibe.survey.core.presentation.dto.request.media.MediaRemovalRequest;
import vn.sunvibe.survey.core.presentation.dto.response.media.ChecklistMediaResponse;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaContentResponse;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaCreateResponse;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaStateResponse;

/**
 * The survey photos of staff (survey-media spec 7.1): private photos of a checklist draft, uploaded by its holder.
 * Sale staff and ADMIN only; a checklist or photo of a lead the caller may not see answers 404 exactly like one that
 * does not exist (spec 4.3).
 */
@RestController
@RequestMapping(SurveyMediaController.STAFF)
@Tag(name = "Survey photos (staff)")
@RequiredArgsConstructor
// survey-media spec 7: sale staff and ADMIN only; SUPER_ADMIN passes hasRole('ADMIN') through the role hierarchy
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class SurveyMediaController {

    public static final String STAFF = "/api/v1/staff";
    /** The create path, also matched by the JSON body limit (spec A-11). */
    public static final String CREATE = "/survey-checklists/{checklistId}/media";
    /** The hold paths, also matched by the JSON body limit (spec A-11). */
    public static final String HOLD = "/survey-media/{mediaId}/hold";
    public static final String HOLD_RELEASE = "/survey-media/{mediaId}/hold-release";
    /** The removal path, also matched by the JSON body limit (spec A-11). */
    public static final String REMOVAL = "/survey-media/{mediaId}/removal";
    /** The deselection path, also matched by the JSON body limit (checklist submission spec 7.1). */
    public static final String DESELECTION = "/survey-checklists/{checklistId}/media/{mediaId}/deselection";

    private final SurveyMediaCommandUseCase commandUseCase;
    private final SurveyMediaQueryUseCase queryUseCase;
    private final SurveyViewerResolver viewerResolver;
    private final MediaRequestConverter requestConverter;
    private final MediaResponseConverter responseConverter;

    /**
     * GET /api/v1/staff/survey-checklists/{checklistId}/media
     * <p>
     * The checklist's photos for the viewer (spec 7.1, 7.2); 204 while {@code knownVersion} is current (A-30). Every
     * list read counts in the hourly list budget and is access-logged once per 10 minutes (A-6, A-26).
     */
    @GetMapping(CREATE)
    @Operation(operationId = "listSurveyMedia",
            summary = "Read the photos of a survey checklist (SALE: own leads only; SALE_LEADER, SALES_MANAGER, "
                    + "ADMIN, SUPER_ADMIN)",
            description = "Selected photos by number, the newest 100 not accepted and removed ones, totals, limits, "
                    + "coverage and the switches as they apply to the viewer. Send the version of the last answer as "
                    + "knownVersion to get 204 while nothing changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The photos",
                    content = @Content(schema = @Schema(implementation = ChecklistMediaResponse.class))),
            @ApiResponse(responseCode = "204", description = "Nothing changed since knownVersion",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on knownVersion (malformed)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED (field budget): the hourly list budget",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait",
                            schema = @Schema(type = "integer")),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistMediaResponse> list(
            @PathVariable("checklistId") String checklistId,
            @Parameter(description = "setVersion.identificationVersion.conditionVersion.checklistVersion.stateEpoch."
                    + "viewDigest of the last answer")
            @RequestParam(value = "knownVersion", required = false) String knownVersion) {
        return queryUseCase.list(viewerResolver.current(), viewerResolver.currentCanHold(), checklistId, knownVersion)
                .map(view -> ResponseEntity.ok(responseConverter.toList(view)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * POST /api/v1/staff/survey-checklists/{checklistId}/media
     * <p>
     * The checklist holder creates a photo record awaiting its bytes (spec 4.4 step 1); the same upload key and body
     * again answers that photo with 200.
     */
    @PostMapping(CREATE)
    @Operation(operationId = "createSurveyMedia",
            summary = "Create a survey photo record before sending its bytes (the checklist holder only)",
            description = "Declares the file (size and SHA-256) with its placement, category and an optional caption. "
                    + "The photo then waits up to 60 minutes for its bytes. The same uploadKey and body again answers "
                    + "the same photo (200). Unknown keys are refused; the body is at most 16 KiB.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The photo record, awaiting its content",
                    content = @Content(schema = @Schema(implementation = SurveyMediaCreateResponse.class))),
            @ApiResponse(responseCode = "200", description = "Replay: the photo this uploadKey created with the same "
                    + "body",
                    content = @Content(schema = @Schema(implementation = SurveyMediaCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on uploadKey, spaceId (also not a "
                    + "space of the checklist's G1), category (also not allowed at the placement), caption, "
                    + "sizeBytes or contentSha256; a missing or unknown key too",
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
                    + "IDEMPOTENCY_KEY_REUSED (field uploadKey); MEDIA_UPLOAD_DISABLED; MEDIA_DUPLICATE_CONTENT or "
                    + "MEDIA_FROM_OTHER_VISIT (field contentSha256, allowedValues = the matching photo id); "
                    + "MEDIA_LIMIT_REACHED (field selected or pending)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE: the body is above 16 KiB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED: field records or bytes (the caller's "
                    + "rolling hour) or expensive (processing outcomes of the hour)",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait",
                            schema = @Schema(type = "integer")),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "MEDIA_INTAKE_PAUSED: uploads are paused for a moment; "
                    + "send the same request again later",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyMediaCreateResponse> create(@PathVariable("checklistId") String checklistId,
                                                            @RequestBody MediaCreateRequest request) {
        MediaCreateResult result = commandUseCase.create(viewerResolver.current(), viewerResolver.currentCanHold(),
                requestConverter.toCommand(checklistId, request));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(responseConverter.toCreated(result));
    }

    /**
     * POST /api/v1/staff/survey-media/{mediaId}/removal
     * <p>
     * The checklist holder removes a photo (spec 4.7): a correction is needed for a received photo another person
     * uploaded; removing again answers the photo as it is.
     */
    @PostMapping(REMOVAL)
    @Operation(operationId = "removeSurveyMedia",
            summary = "Remove a survey photo",
            description = "The checklist holder removes a photo. A photo whose bytes were received and that another "
                    + "person uploaded needs a correction. The removed photo is no longer served; its record stays "
                    + "in the removed list. Removing it again answers it as it is.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The removed photo",
                    content = @Content(schema = @Schema(implementation = SurveyMediaContentResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on correction (missing where needed), "
                    + "correction.reason, correction.basis.confirmedOn (not from the visit's arrival date to today) "
                    + "or correction.basis.text; MALFORMED_REQUEST for an unknown or missing key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such photo, or the caller may not see its "
                    + "lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CHECKLIST_READ_ONLY; CHECKLIST_TAKEOVER_REQUIRED; "
                    + "MEDIA_REFERENCED: a submission uses the photo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE: the body is above 16 KiB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "the lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyMediaContentResponse> remove(@PathVariable("mediaId") String mediaId,
                                                             @RequestBody MediaRemovalRequest request) {
        return ResponseEntity.ok(responseConverter.toContent(commandUseCase.remove(viewerResolver.current(),
                viewerResolver.currentCanHold(), requestConverter.toCommand(mediaId, request))));
    }

    /**
     * POST /api/v1/staff/survey-checklists/{checklistId}/media/{mediaId}/deselection
     * <p>
     * The holder takes a carried photo out of a draft revision (survey-checklist submission spec 4.6, A-12); it stays on
     * the revision that froze it. Deselecting again changes nothing. Answers the revision's media list.
     */
    @PostMapping(DESELECTION)
    @Operation(operationId = "deselectSurveyMedia",
            summary = "Take a carried photo out of a draft revision",
            description = "The revision's holder takes out a photo carried from an earlier revision. The photo itself "
                    + "and the earlier revision are not touched. A photo another person uploaded needs a correction. "
                    + "Taking it out again answers the list as it is.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The media list of the revision",
                    content = @Content(schema = @Schema(implementation = ChecklistMediaResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on correction (missing where needed), "
                    + "correction.reason, correction.basis.confirmedOn (not from the visit's arrival date to today) "
                    + "or correction.basis.text; MALFORMED_REQUEST for an unknown or missing key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such revision or photo, the photo was not "
                    + "carried into this revision, or the caller may not see its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CHECKLIST_READ_ONLY; CHECKLIST_TAKEOVER_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE: the body is above 16 KiB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "the lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED (field budget): only a repeat, which "
                    + "changes nothing and counts in the hourly list budget",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait",
                            schema = @Schema(type = "integer")),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistMediaResponse> deselect(@PathVariable("checklistId") String checklistId,
                                                           @PathVariable("mediaId") String mediaId,
                                                           @RequestBody MediaRemovalRequest request) {
        boolean changed = commandUseCase.deselect(viewerResolver.current(),
                requestConverter.toDeselection(checklistId, mediaId, request));
        return ResponseEntity.ok(responseConverter.toList(queryUseCase.listAfterDeselection(viewerResolver.current(),
                viewerResolver.currentCanHold(), checklistId, changed)));
    }

    /**
     * POST /api/v1/staff/survey-media/{mediaId}/hold
     * <p>
     * An ADMIN holds a photo for a security reason (spec A-23): ADMIN only, checked before any lookup (a role 403
     * writes no event); on any checklist state; a photo already held answers unchanged.
     */
    @PostMapping(HOLD)
    // survey-media spec A-23: ADMIN only (SUPER_ADMIN through the role hierarchy), before any lookup
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "holdSurveyMedia",
            summary = "Hold a survey photo (ADMIN)",
            description = "Puts a security hold on a processing or ready photo: only an ADMIN can then read it, its "
                    + "caption is hidden from others and its content is kept until the release. A photo already "
                    + "held answers unchanged, keeping the first reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The held photo (unchanged when already held)",
                    content = @Content(schema = @Schema(implementation = SurveyMediaStateResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on reason; MALFORMED_REQUEST for "
                    + "another reason value or an unknown or missing key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such photo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "MEDIA_NOT_AVAILABLE (field status): the photo is "
                    + "removed, or not processing or ready",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE: the body is above 16 KiB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyMediaStateResponse> hold(@PathVariable("mediaId") String mediaId,
                                                         @RequestBody MediaHoldRequest request) {
        return ResponseEntity.ok(responseConverter.toState(commandUseCase.hold(viewerResolver.current(), mediaId,
                request.reason())));
    }

    /**
     * POST /api/v1/staff/survey-media/{mediaId}/hold-release
     * <p>
     * An ADMIN releases a photo's hold (spec A-23); a photo not held answers unchanged.
     */
    @PostMapping(HOLD_RELEASE)
    // survey-media spec A-23: ADMIN only (SUPER_ADMIN through the role hierarchy), before any lookup
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "releaseSurveyMediaHold",
            summary = "Release the hold of a survey photo (ADMIN)",
            description = "Ends the hold: the photo is served again unless it was removed, in which case its content "
                    + "deletion is requested now. A photo not held answers unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The photo after the release (unchanged when not held)",
                    content = @Content(schema = @Schema(implementation = SurveyMediaStateResponse.class))),
            @ApiResponse(responseCode = "400", description = "MALFORMED_REQUEST for an unknown key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such photo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE: the body is above 16 KiB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyMediaStateResponse> release(@PathVariable("mediaId") String mediaId,
                                                            @RequestBody MediaHoldReleaseRequest request) {
        return ResponseEntity.ok(responseConverter.toState(commandUseCase.release(viewerResolver.current(), mediaId)));
    }
}
