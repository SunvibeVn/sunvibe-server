package vn.sunvibe.brokerage.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AgreementQueryUseCase;
import vn.sunvibe.brokerage.core.presentation.converter.AgreementRequestConverter;
import vn.sunvibe.brokerage.core.presentation.converter.AgreementResponseConverter;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementCreateRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDraftRequest;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementCreatedResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementDetailResponse;
import vn.sunvibe.brokerage.core.presentation.resolver.StaffViewerResolver;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

/**
 * Brokerage agreements in the staff workspace (brokerage-agreement US-1 AC-1 to AC-5, US-4 AC-3 part; E05-S2 spec 4,
 * E05-S3 spec 4). {@code /api/v1/staff/**} has no path rule, so this class carries the staff rule; the lead's visibility
 * and the assignee are checked in the use cases, and the actor is the principal, never a body field
 * (brokerage-agreement BR-16). Every path runs without open-in-view (ADR-012 decision 8).
 */
@RestController
@RequestMapping(BrokerageAgreementController.PATH)
@RequiredArgsConstructor
@Tag(name = "Brokerage agreement")
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class BrokerageAgreementController {

    public static final String PATH = "/api/v1/staff/brokerage-agreements";

    private final AgreementCommandUseCase commands;
    private final AgreementQueryUseCase queries;
    private final StaffViewerResolver viewers;
    private final AgreementRequestConverter requests;
    private final AgreementResponseConverter responses;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createBrokerageAgreement",
            summary = "Draft a brokerage agreement for a unit of the lead's intake basis")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The draft was created; only its id is answered"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (field entries; transactionType with its"
                    + " allowedValues) or MALFORMED_REQUEST (the body or a date cannot be read)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller is not staff; AGREEMENT_NOT_ASSIGNEE:"
                    + " the caller is not the lead's assigned Sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the lead does not exist or is not visible",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AGREEMENT_NOT_ELIGIBLE (one reasonGroups.<GROUP> entry per"
                    + " group, in the order BASIS, PROPERTY, UNIT, OWNER), AGREEMENT_EXISTS (the unit already has an"
                    + " agreement in progress) or CONCURRENT_MODIFICATION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BrokerageAgreementCreatedResponse create(@Valid @RequestBody BrokerageAgreementCreateRequest request) {
        return responses.toCreated(commands.create(requests.toCommand(request), viewers.current()));
    }

    @GetMapping("/{agreementId}")
    @Operation(operationId = "getBrokerageAgreement", summary = "The detail of a brokerage agreement; each read is logged")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The agreement and its versions"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not staff",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "The agreement does not exist, the id is malformed or its"
                    + " basis lead is not visible: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BrokerageAgreementDetailResponse detail(@PathVariable("agreementId") String agreementId) {
        return responses.toDetail(queries.detail(agreementId, viewers.current()));
    }

    @PutMapping("/{agreementId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "updateBrokerageAgreementDraft",
            summary = "Replace the whole content of the agreement's draft, against the data version read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The draft was saved; no body (read the detail again)"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (one entry per field: expectedDataVersion,"
                    + " transactionType with its allowedValues, startDate, endDate, exteriorPhotoConsent or a terms path"
                    + " such as terms.mandatoryFees[2].amount) or MALFORMED_REQUEST (the body, a date or a number"
                    + " cannot be read; a fraction is not a whole number)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller is not staff; AGREEMENT_NOT_ASSIGNEE:"
                    + " the caller is not the basis lead's assigned Sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the agreement does not exist, the id is"
                    + " malformed or its basis lead is not visible: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AGREEMENT_NOT_DRAFT (the agreement has no draft to edit),"
                    + " AGREEMENT_NOT_ELIGIBLE (one reasonGroups.<GROUP> entry per group, in the order BASIS, PROPERTY,"
                    + " UNIT, OWNER) or CONCURRENT_MODIFICATION (entry expectedDataVersion for a stale value, none for"
                    + " a change committed in between)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void updateDraft(@PathVariable("agreementId") String agreementId,
                            @Valid @RequestBody BrokerageAgreementDraftRequest request) {
        commands.editDraft(requests.toDraftCommand(agreementId, request), viewers.current());
    }
}
