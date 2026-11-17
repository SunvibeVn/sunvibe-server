package vn.sunvibe.brokerage.core.presentation.controller;

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
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AgreementQueryUseCase;
import vn.sunvibe.brokerage.core.presentation.converter.AgreementRequestConverter;
import vn.sunvibe.brokerage.core.presentation.converter.AgreementResponseConverter;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementConfirmationRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementRejectionRequest;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementOwnerListResponse;
import vn.sunvibe.brokerage.core.presentation.resolver.OwnerActorResolver;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * The owner's brokerage agreements in their account (brokerage-agreement US-2 AC-1 to AC-5; E05-S5 spec 4, S5-D15). The
 * path rule {@code /api/v1/me/**} asks for a session and no role: the owner may hold none. Who is an owner, and whose
 * agreement an id is, the use cases decide, and every other caller gets the answer of nothing (US-2 AC-1, spec S5-D18).
 * Every path runs without open-in-view (ADR-012 decision 8).
 */
@RestController
@RequestMapping(MeBrokerageAgreementController.PATH)
@RequiredArgsConstructor
@Tag(name = "Brokerage agreement")
@SecurityRequirement(name = "bearerAuth")
public class MeBrokerageAgreementController {

    public static final String PATH = "/api/v1/me/brokerage-agreements";

    private final AgreementCommandUseCase commands;
    private final AgreementQueryUseCase queries;
    private final OwnerActorResolver owners;
    private final AgreementRequestConverter requests;
    private final AgreementResponseConverter responses;

    @GetMapping
    @Operation(operationId = "listMyBrokerageAgreements",
            summary = "The signed-in account's brokerage agreements with every version sent to them; each read is logged")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "At most 50 agreements, newest sending first; an account"
                    + " that is not an owner gets an empty list"),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BrokerageAgreementOwnerListResponse list() {
        return responses.toOwnerList(queries.ownerAgreements(owners.userId()));
    }

    @PostMapping("/confirmations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "confirmMyBrokerageAgreementVersions",
            summary = "Confirm one or several versions sent to the account, with the current password and the statement"
                    + " read; all or none")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Every version was confirmed, each recorded on its own; no"
                    + " body (read the list again)"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (one entry per field: currentPassword,"
                    + " statementVersion, versions, versions[i].agreementId or versions[i].versionNo; the one entry"
                    + " currentPassword alone is a wrong password) or MALFORMED_REQUEST (the body cannot be read)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: a version is not one sent to this account; one"
                    + " answer for every caller and id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AGREEMENT_STATEMENT_OUTDATED (the statement changed; no"
                    + " entry), AGREEMENT_NOT_AWAITING_OWNER (one versions[i] entry per version no longer waiting or past"
                    + " its 7 days), AGREEMENT_NOT_ELIGIBLE (entries versions[i].reasonGroups.<GROUP>, in the order"
                    + " BASIS, PROPERTY, UNIT, OWNER) or CONCURRENT_MODIFICATION (no entry)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "423", description = "ACCOUNT_LOCKED: too many wrong passwords; no entry",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void confirm(@Valid @RequestBody BrokerageAgreementConfirmationRequest request) {
        commands.confirmByOwner(requests.toConfirmCommand(request), owners.userId());
    }

    @PostMapping("/{agreementId}/rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "rejectMyBrokerageAgreementVersion",
            summary = "Decline the version waiting for the account's answer, with an optional private reason")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The version was declined and the agreement is drafting"
                    + " again; no body (read the list again)"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (entry versionNo or reason) or"
                    + " MALFORMED_REQUEST (the body cannot be read)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No valid access token (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (security chain)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: not a version sent to this account; one answer"
                    + " for every caller and id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AGREEMENT_NOT_AWAITING_OWNER (the version no longer waits"
                    + " or its 7 days passed; no entry) or CONCURRENT_MODIFICATION (no entry)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void reject(@PathVariable("agreementId") String agreementId,
                       @Valid @RequestBody BrokerageAgreementRejectionRequest request) {
        commands.rejectByOwner(requests.toRejectCommand(agreementId, request), owners.userId());
    }
}
