package vn.sunvibe.survey.core.presentation.controller;

import java.io.IOException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.survey.core.application.command.ContentBody;
import vn.sunvibe.survey.core.application.command.UploadContentCommand;
import vn.sunvibe.survey.core.application.query.MediaContentResult;
import vn.sunvibe.survey.core.application.usecase.SurveyMediaContentUseCase;
import vn.sunvibe.survey.core.presentation.converter.MediaResponseMapper;
import vn.sunvibe.survey.core.presentation.converter.SurveyViewerResolver;
import vn.sunvibe.survey.core.presentation.dto.response.media.SurveyMediaContentResponse;

/**
 * The raw-bytes upload of a survey photo (survey-media spec 4.4 step 2, 7.1): no request-body binding, the stream goes
 * to the use case as it arrives, and no transaction or connection is open while it moves (A-13). An answer decided
 * before the body slots closes the connection without reading the body; every later answer follows the drained body.
 */
@RestController
@RequestMapping(SurveyMediaController.STAFF)
@Tag(name = "Survey photos (staff)")
@RequiredArgsConstructor
// survey-media spec 7: sale staff and ADMIN only; SUPER_ADMIN passes hasRole('ADMIN') through the role hierarchy
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class SurveyMediaContentController {

    public static final String CONTENT = "/survey-media/{mediaId}/content";
    private static final String CONTENT_DIGEST = "Content-Digest";

    private final SurveyMediaContentUseCase contentUseCase;
    private final SurveyViewerResolver viewerResolver;
    private final MediaResponseMapper responseMapper;

    /**
     * PUT /api/v1/staff/survey-media/{mediaId}/content
     * <p>
     * The photo's creator sends its bytes once (spec 4.4 step 2); the same bytes again answers the photo as it is.
     */
    @PutMapping(CONTENT)
    @Operation(operationId = "uploadSurveyMediaContent",
            summary = "Send the bytes of a survey photo record (its creator, the checklist holder)",
            description = "The raw file, exactly the declared size and SHA-256. Errors decided from the headers, 401, "
                    + "403, 404 and the slot refusals answer at once and close the connection; every later answer comes "
                    + "after the server read the rest of the body. A network error means the state is unknown: read the "
                    + "photos before sending again.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary"))),
            parameters = {
                    @Parameter(in = ParameterIn.HEADER, name = "Content-Length", required = true,
                            description = "Equal to the declared sizeBytes, at most 20971520",
                            schema = @Schema(type = "integer", format = "int64")),
                    @Parameter(in = ParameterIn.HEADER, name = CONTENT_DIGEST, required = true,
                            description = "RFC 9530: sha-256=:<base64>:, equal to the declared contentSha256",
                            schema = @Schema(type = "string"))
            })
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "The bytes are received; the photo is PROCESSING",
                    content = @Content(schema = @Schema(implementation = SurveyMediaContentResponse.class))),
            @ApiResponse(responseCode = "400", description = "MALFORMED_REQUEST: field Content-Length (missing or not "
                    + "the declared size), Content-Digest (missing, malformed, not the declared hash or not matching "
                    + "the body) or body (ended early); VALIDATION_FAILED on spaceId when the space left G1",
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
                    + "MEDIA_NOT_AVAILABLE (field uploadedBy or status); MEDIA_CONTENT_ALREADY_RECEIVED; "
                    + "MEDIA_UPLOAD_IN_PROGRESS; MEDIA_UPLOAD_EXPIRED; MEDIA_UPLOAD_DISABLED; MEDIA_DUPLICATE_CONTENT "
                    + "or MEDIA_FROM_OTHER_VISIT (field contentSha256, allowedValues = the matching photo id)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE: Content-Length above 20 MiB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "MALFORMED_REQUEST (field Content-Type): only "
                    + "application/octet-stream",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED: field expensive (processing outcomes of "
                    + "the hour) or bodies (3 uploads at once per user); at once, the connection closes",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait",
                            schema = @Schema(type = "integer")),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "MEDIA_SERVER_BUSY (at once, with Retry-After); "
                    + "MEDIA_INTAKE_PAUSED or MEDIA_STORE_UNAVAILABLE: send the same request again later",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait, on MEDIA_SERVER_BUSY",
                            schema = @Schema(type = "integer")),
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyMediaContentResponse> upload(@PathVariable("mediaId") String mediaId,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) throws IOException {
        ContentBody body = new ContentBody(request.getInputStream());
        try {
            MediaContentResult result = contentUseCase.upload(viewerResolver.current(),
                    viewerResolver.currentCanHold(), new UploadContentCommand(mediaId, request.getContentType(),
                            request.getContentLengthLong(), request.getHeader(CONTENT_DIGEST)), body);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseMapper.toContent(result));
        } catch (RuntimeException refused) {
            if (!body.touched()) {
                // spec A-13: decided before any byte was read: the connection closes after the answer
                response.setHeader(HttpHeaders.CONNECTION, "close");
            }
            throw refused;
        }
    }
}
