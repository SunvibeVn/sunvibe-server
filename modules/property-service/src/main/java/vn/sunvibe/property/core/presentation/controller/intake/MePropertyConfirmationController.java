package vn.sunvibe.property.core.presentation.controller.intake;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.property.core.application.usecase.intake.OwnerConfirmationDetails;
import vn.sunvibe.property.core.application.usecase.intake.OwnerConfirmationItem;
import vn.sunvibe.property.core.application.usecase.intake.OwnerConfirmationUseCase;
import vn.sunvibe.property.core.presentation.resolver.IntakeActorResolver;
import vn.sunvibe.property.core.presentation.dto.request.intake.OwnerConfirmationRequests;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.util.List;

/**
 * The owner's confirmations (property-create intake spec 7.3). The path rule {@code /api/v1/me/**} asks for a session and no role:
 * the owner may hold none yet. The use case binds every {@code {id}} to the signed-in account.
 */
@RestController
@RequestMapping(MePropertyConfirmationController.PATH)
@RequiredArgsConstructor
@Tag(name = "Owner confirmations")
@SecurityRequirement(name = "bearerAuth")
public class MePropertyConfirmationController {

    public static final String PATH = "/api/v1/me/property-confirmations";

    private final OwnerConfirmationUseCase confirmations;
    private final IntakeActorResolver actors;

    /** The list of spec 7.3. */
    @Schema(name = "OwnerConfirmationList")
    public record Items(List<OwnerConfirmationItem> items) {
    }

    @GetMapping
    @Operation(summary = "The requests and live confirmations of the signed-in account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The list; before the code only id, status and expiry show"),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the intake switch is off",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Items list() {
        return new Items(confirmations.list(actors.userId()));
    }

    @PostMapping("/{id}/details")
    @Operation(summary = "What the code opens: the parts and the statement to confirm")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The details"),
            @ApiResponse(responseCode = "400", description = "CONFIRMATION_INVALID or VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such request for this account",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(type = "object")))
    })
    public OwnerConfirmationDetails details(@PathVariable("id") String id,
                                            @Valid @RequestBody OwnerConfirmationRequests.Code request) {
        return confirmations.details(actors.userId(), id, request.code());
    }

    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirm with the code, the password and the statement version")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Confirmed"),
            @ApiResponse(responseCode = "400",
                    description = "CONFIRMATION_INVALID, or VALIDATION_FAILED on currentPassword",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such request for this account",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(type = "object")))
    })
    public void confirm(@PathVariable("id") String id, @Valid @RequestBody OwnerConfirmationRequests.Confirm request) {
        confirmations.confirm(actors.userId(), id, request.code(), request.currentPassword(), request.statementVersion());
    }

    @PostMapping("/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Decline a request; staff cannot read that it was declined")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Declined"),
            @ApiResponse(responseCode = "400", description = "CONFIRMATION_INVALID or VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such request for this account",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(type = "object")))
    })
    public void decline(@PathVariable("id") String id, @Valid @RequestBody OwnerConfirmationRequests.Code request) {
        confirmations.decline(actors.userId(), id, request.code());
    }

    @PostMapping("/{id}/check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Enter the phrase the manager read out")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The owner check is done"),
            @ApiResponse(responseCode = "400", description = "CONFIRMATION_INVALID or VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such request for this account",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(type = "object")))
    })
    public void check(@PathVariable("id") String id, @Valid @RequestBody OwnerConfirmationRequests.Phrase request) {
        confirmations.enterPhrase(actors.userId(), id, request.phrase());
    }

    @PostMapping("/{id}/withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Withdraw a live confirmation with the password")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Withdrawn"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on currentPassword",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No live confirmation of this request for this account",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(type = "object")))
    })
    public void withdraw(@PathVariable("id") String id, @Valid @RequestBody OwnerConfirmationRequests.Withdrawal request) {
        confirmations.withdraw(actors.userId(), id, request.currentPassword());
    }
}
