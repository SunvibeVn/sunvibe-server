package vn.sunvibe.brokerage.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AgreementHistoryUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AgreementListUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AgreementQueryUseCase;
import vn.sunvibe.brokerage.core.presentation.converter.AgreementRequestConverter;
import vn.sunvibe.brokerage.core.presentation.converter.AgreementResponseConverter;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementActivationRefusalRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementActivationRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementCreateRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDraftRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementDraftSubmissionRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementNewDraftRequest;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementCreatedResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementDetailResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementHistoryResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementPageResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementSurveyTermsResponse;
import vn.sunvibe.brokerage.core.presentation.resolver.StaffViewerResolver;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.util.List;

/**
 * Brokerage agreements in the staff workspace (brokerage-agreement US-1 AC-1 to AC-5, US-3, US-4 AC-1 to AC-3 part;
 * E05-S2 spec 4, E05-S3 spec 4, E05-S4 spec 4, E05-S6 spec 4). {@code /api/v1/staff/**} has no path rule, so this class
 * carries the staff rule and the activation's two operations a narrower method rule; the lead's visibility, the assignee
 * and the activator's independence are checked in the use cases, and the actor is the principal, never a body field
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
    private final AgreementListUseCase lists;
    private final AgreementHistoryUseCase histories;
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

    /**
     * The agreements in the caller's scope (brokerage-agreement US-4 AC-3, BR-16; E05-S7 spec S7-D1 to S7-D6, S7-D13).
     * Each parameter is bound alone with no bean constraint: the converter refuses a bound or a code with the module's
     * one body, and a value of the wrong type reaches the exception handler, never a 500.
     */
    @GetMapping
    @Operation(operationId = "listBrokerageAgreements", summary = "The brokerage agreements in the caller's scope, each"
            + " with its state and its advertising answer at the read; the read is not logged")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page: newest first, then by id; with endsWithin30Days,"
                    + " soonest end date first, then by id"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: one entry naming the parameter, never its"
                    + " value: status (with its allowedValues), page, size or endsWithin30Days",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller is not staff",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BrokerageAgreementPageResponse list(
            @Parameter(description = "Keep only the agreements in these stored states (repeat the parameter); every"
                    + " state when left out", array = @ArraySchema(schema = @Schema(allowableValues = {"DRAFTING",
                    "AWAITING_CONFIRMATION", "ACTIVE", "SUSPENDED", "EXPIRED", "TERMINATED", "CANCELLED"})))
            @RequestParam(name = "status", required = false) List<String> status,
            @Parameter(description = "Keep only the agreements in effect whose right ends within the next 30 days"
                    + " (Vietnam time)", schema = @Schema(type = "boolean", defaultValue = "false"))
            @RequestParam(name = "endsWithin30Days", defaultValue = "false") boolean endsWithin30Days,
            @Parameter(description = "Zero-based page number, 0 to 9999", schema = @Schema(type = "integer",
                    format = "int32", minimum = "0", maximum = "9999", defaultValue = "0"))
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Agreements per page, 1 to 50", schema = @Schema(type = "integer",
                    format = "int32", minimum = "1", maximum = "50", defaultValue = "20"))
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return responses.toAgreementPage(lists.list(requests.toListQuery(status, endsWithin30Days, page, size),
                viewers.current()));
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

    /**
     * The history of an agreement (brokerage-agreement US-4 AC-4, BR-16, BR-17; E05-S7 spec S7-D7, S7-D8, S7-D13). The
     * parameters are checked before the agreement is looked up, so a refusal tells nothing of the agreement.
     */
    @GetMapping("/{agreementId}/history")
    @Operation(operationId = "getBrokerageAgreementHistory", summary = "The events of a brokerage agreement, newest first:"
            + " what happened, the version, who acted and when; no reason; the read is not logged")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of the agreement's events, newest first"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED: one entry naming the parameter, never its"
                    + " value: page or size",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller is not staff",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the agreement does not exist, the id is"
                    + " malformed or its basis lead is not visible: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BrokerageAgreementHistoryResponse history(
            @PathVariable("agreementId") String agreementId,
            @Parameter(description = "Zero-based page number, 0 to 9999", schema = @Schema(type = "integer",
                    format = "int32", minimum = "0", maximum = "9999", defaultValue = "0"))
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Events per page, 1 to 100", schema = @Schema(type = "integer",
                    format = "int32", minimum = "1", maximum = "100", defaultValue = "50"))
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return responses.toHistory(histories.history(requests.toHistoryQuery(agreementId, page, size),
                viewers.current()));
    }

    @GetMapping("/{agreementId}/survey-terms")
    @Operation(operationId = "getBrokerageAgreementSurveyTerms",
            summary = "The survey's values for the draft's terms, with their sources and dates; a proposal, never stored")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The proposal of the unit's surveyed space; every part null"
                    + " and no source when the survey holds none or no longer stands as the intake's source"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller is not staff",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the agreement does not exist, the id is"
                    + " malformed or its basis lead is not visible: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AGREEMENT_NOT_DRAFT: the agreement has no draft",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public BrokerageAgreementSurveyTermsResponse surveyTerms(@PathVariable("agreementId") String agreementId) {
        return responses.toSurveyTerms(queries.surveyTerms(agreementId, viewers.current()));
    }

    @PutMapping("/{agreementId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "updateBrokerageAgreementDraft",
            summary = "Replace the whole content of the agreement's draft, against the data version read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The draft was saved; no body (read the detail again)"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (one entry per field: expectedDataVersion,"
                    + " transactionType with its allowedValues, startDate, endDate, exteriorPhotoConsent, a terms path"
                    + " such as terms.mandatoryFees[2].amount, or a feePolicy path such as"
                    + " feePolicy.formula.tiers[1].fromMonths; feePolicy.formula.type, feePolicy.renewal.type and"
                    + " feePolicy.earlyLeaseEnd.type outside their place come with the place's allowedValues) or"
                    + " MALFORMED_REQUEST (the body, a date or a number cannot be read; a fraction is not a whole"
                    + " number)",
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

    @PostMapping("/{agreementId}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "openBrokerageAgreementDraft",
            summary = "Open the next draft version, a copy of the latest one, once the latest version did not take effect"
                    + " (the owner declined it or did not answer in time, or a manager refused its activation or did not"
                    + " activate it in time), against the data version read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The draft was opened; no body (read the detail again)"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (entry expectedDataVersion) or"
                    + " MALFORMED_REQUEST (the body or the number cannot be read)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller is not staff; AGREEMENT_NOT_ASSIGNEE:"
                    + " the caller is not the basis lead's assigned Sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the agreement does not exist, the id is"
                    + " malformed or its basis lead is not visible: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AGREEMENT_NEW_DRAFT_NOT_ALLOWED (a version still waits for"
                    + " the owner or for activation, or the agreement is not drafting; no entry),"
                    + " CONCURRENT_MODIFICATION (entry expectedDataVersion for a stale value, none for a change committed"
                    + " in between) or AGREEMENT_NOT_ELIGIBLE (one reasonGroups.<GROUP> entry per group, in the order"
                    + " BASIS, PROPERTY, UNIT, OWNER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void openDraft(@PathVariable("agreementId") String agreementId,
                          @Valid @RequestBody BrokerageAgreementNewDraftRequest request) {
        commands.openDraft(requests.toOpenDraftCommand(agreementId, request), viewers.current());
    }

    @PostMapping("/{agreementId}/draft/submission")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "submitBrokerageAgreementDraft",
            summary = "Send the agreement's complete draft to the owner, against the data version read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The draft was sent: the version waits for the owner and its"
                    + " content is locked; no body (read the detail again)"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (entry expectedDataVersion) or"
                    + " MALFORMED_REQUEST (the body or the number cannot be read)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller is not staff; AGREEMENT_NOT_ASSIGNEE:"
                    + " the caller is not the basis lead's assigned Sale",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the agreement does not exist, the id is"
                    + " malformed or its basis lead is not visible: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AGREEMENT_NOT_DRAFT (the agreement has no draft),"
                    + " CONCURRENT_MODIFICATION (entry expectedDataVersion for a stale value, none for a change committed"
                    + " in between), AGREEMENT_VERSION_INCOMPLETE (one entry per missing part, in the order photoConsent,"
                    + " terms.monthlyRent, terms.deposit, terms.minimumTermMonths, terms.paymentPeriodMonths,"
                    + " terms.mandatoryFees, feePolicy.formula, feePolicy.dueDays, feePolicy.renewal,"
                    + " feePolicy.earlyLeaseEnd, feePolicy.protectionDays), AGREEMENT_NOT_ELIGIBLE (one"
                    + " reasonGroups.<GROUP> entry per group, in the order BASIS, PROPERTY, UNIT, OWNER) or"
                    + " AGREEMENT_SENDING_NOT_AVAILABLE (sending to owners is not open on this server; no entry)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void submitDraft(@PathVariable("agreementId") String agreementId,
                            @Valid @RequestBody BrokerageAgreementDraftSubmissionRequest request) {
        commands.sendDraft(requests.toSendCommand(agreementId, request), viewers.current());
    }

    /**
     * brokerage-agreement BR-11, BR-16 (E05-S6 spec S6-D1 step 2): only a manager role decides an activation; the
     * independence from the version's authors and the lead's assignee is checked in the use case.
     */
    @PostMapping("/{agreementId}/activation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN')")
    @Operation(operationId = "activateBrokerageAgreementVersion",
            summary = "Activate the version the owner confirmed, against the signed document, the activation statement"
                    + " and the data version read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The version was activated: ACTIVATED until its start, else"
                    + " EFFECTIVE, and the agreement ACTIVE; no body (read the detail again)"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (one entry per field: expectedDataVersion,"
                    + " versionNo, statementVersion, documentNumber, signedOn (2000-01-01 to today in Vietnam),"
                    + " sunvibeSignatoryName, sunvibeSignatoryTitle) or MALFORMED_REQUEST (the body, the date or a"
                    + " number cannot be read)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller is not a SALE_LEADER, SALES_MANAGER or"
                    + " ADMIN; AGREEMENT_ACTIVATOR_NOT_INDEPENDENT: the caller is the basis lead's assignee or an author"
                    + " of the version",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the agreement does not exist, the id is"
                    + " malformed or its basis lead is not visible: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AGREEMENT_NOT_AWAITING_ACTIVATION (the version is not the"
                    + " confirmed one awaiting activation, or its 14 days have passed; no entry),"
                    + " CONCURRENT_MODIFICATION (entry expectedDataVersion for a stale value, none for a change committed"
                    + " in between), AGREEMENT_STATEMENT_OUTDATED (the activation statement changed; no entry),"
                    + " AGREEMENT_VERSION_PERIOD_ENDED (the end date has passed; no entry) or AGREEMENT_NOT_ELIGIBLE (one"
                    + " reasonGroups.<GROUP> entry per group, in the order BASIS, PROPERTY, UNIT, OWNER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void activate(@PathVariable("agreementId") String agreementId,
                         @Valid @RequestBody BrokerageAgreementActivationRequest request) {
        commands.activate(requests.toActivateCommand(agreementId, request), viewers.current());
    }

    /** brokerage-agreement US-3 AC-3, E-7 (E05-S6 spec S6-D4): the same role rule as the activation. */
    @PostMapping("/{agreementId}/activation-refusal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SALE_LEADER','SALES_MANAGER','ADMIN')")
    @Operation(operationId = "refuseBrokerageAgreementActivation",
            summary = "Refuse to activate the version the owner confirmed, with a reason type and an optional note,"
                    + " against the data version read")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The activation was refused and the agreement is drafting"
                    + " again; no body (read the detail again)"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (one entry per field: expectedDataVersion,"
                    + " versionNo, reasonType with its allowedValues, note) or MALFORMED_REQUEST (the body or a number"
                    + " cannot be read)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN: the caller is not a SALE_LEADER, SALES_MANAGER or"
                    + " ADMIN; AGREEMENT_ACTIVATOR_NOT_INDEPENDENT: the caller is the basis lead's assignee or an author"
                    + " of the version",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: the agreement does not exist, the id is"
                    + " malformed or its basis lead is not visible: one answer for all three",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "AGREEMENT_NOT_AWAITING_ACTIVATION (the version is not the"
                    + " confirmed one awaiting activation, or its 14 days have passed; no entry) or"
                    + " CONCURRENT_MODIFICATION (entry expectedDataVersion for a stale value, none for a change committed"
                    + " in between)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void refuseActivation(@PathVariable("agreementId") String agreementId,
                                 @Valid @RequestBody BrokerageAgreementActivationRefusalRequest request) {
        commands.refuseActivation(requests.toRefuseActivationCommand(agreementId, request), viewers.current());
    }
}
