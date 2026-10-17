package vn.sunvibe.acquisition.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.acquisition.core.application.command.OwnerIntakeCommand;
import vn.sunvibe.acquisition.core.application.exception.IntakeValidationException;
import vn.sunvibe.acquisition.core.application.query.OwnerIntakeResult;
import vn.sunvibe.acquisition.core.application.usecase.OwnerIntakeSubmitUseCase;
import vn.sunvibe.acquisition.core.presentation.converter.OwnerIntakeRequestMapper;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest;
import vn.sunvibe.acquisition.core.presentation.dto.response.OwnerIntakeResponse;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.util.regex.Pattern;

/**
 * The public owner intake form (acquisition-owner-intake US-1). Anonymous on purpose: the path is a public POST
 * endpoint in SecurityConfig, rate-limited per client IP, and the answer is only the request code. While the form is
 * switched off, OwnerIntakeEnabledFilter answers 404 before this controller runs (acquisition-owner-intake BR-15).
 */
@RestController
@RequestMapping(OwnerIntakeController.PATH)
@Tag(name = "Owner intake")
@RequiredArgsConstructor
public class OwnerIntakeController {

    public static final String PATH = "/api/v1/public/owner-intake-requests";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final OwnerIntakeSubmitUseCase submitUseCase;
    private final OwnerIntakeRequestMapper requestMapper;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/v1/public/owner-intake-requests
     * <p>
     * Creates an owner lead, or returns the code of the lead an identical earlier request created
     * (acquisition-owner-intake BR-10); either way 202 with the request code.
     */
    @PostMapping
    @Operation(summary = "Send the owner intake form (anonymous, rate-limited): creates an owner lead",
            description = "202 with the request code, also when the same request is sent again (same "
                    + "Idempotency-Key and body within 24 hours, or the same phone, intent, ward and address within "
                    + "5 minutes); such a replay adds the header Idempotent-Replayed: true. Body at most 16 KB.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Request accepted",
                    headers = @Header(name = REPLAYED_HEADER, description = "true when an earlier request's code is "
                            + "returned", schema = @Schema(type = "string", allowableValues = "true")),
                    content = @Content(schema = @Schema(implementation = OwnerIntakeResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (field errors, also for a bad "
                    + "Idempotency-Key) or MALFORMED_REQUEST (body is not readable JSON)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the form is switched off on this server "
                    + "(app.acquisition.owner-intake.enabled); nothing was read or stored",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_REUSED: the key was used within 24 "
                    + "hours for a different body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "PAYLOAD_TOO_LARGE: body over 16 KB",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED: too many requests from this client; "
                    + "see Retry-After",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: nothing was stored",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OwnerIntakeResponse> submit(
            @Valid @RequestBody OwnerIntakeRequest request,
            @Parameter(description = "Optional; 8 to 64 letters, digits, '-' or '_' (the client sends a UUID)")
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {

        if (idempotencyKey != null && !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new IntakeValidationException(IDEMPOTENCY_KEY_HEADER,
                    "Idempotency-Key must be 8 to 64 letters, digits, '-' or '_'.");
        }

        // acquisition-owner-intake BR-5: the request as the owner typed it, before any normalization; unknown
        // fields were already dropped by parsing, and the IP and user agent are not part of it
        String snapshot = objectMapper.writeValueAsString(request);
        // Same client IP as the rate limiter: the direct peer address
        OwnerIntakeCommand command = requestMapper.toCommand(request, idempotencyKey, httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT), snapshot);

        OwnerIntakeResult result = submitUseCase.execute(command);

        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.ACCEPTED);
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(new OwnerIntakeResponse(result.requestCode()));
    }
}
