package vn.sunvibe.brokerage.core.presentation.exception;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementPeriodException;
import vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Error mapping of the brokerage agreement endpoints (E05-S2 spec 4, ADR-010; plan decision E05S2-P2, E05S2-P3): each
 * refusal with its status, code, fixed message and entries, the period and the exists refusals, and a lock failure as a
 * conflict. No body quotes a request value.
 */
class BrokerageExceptionHandlerAgreementTest {

    private static final String CORRELATION_ID = "01J0000000000000000000TEST";

    private final BrokerageExceptionHandler handler = new BrokerageExceptionHandler();

    @BeforeEach
    void setUp() {
        MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationId.MDC_KEY);
    }

    @Test
    void handleRefused_eachReason_statusCodeMessageAndEntries() {
        assertBody(handler.handleRefused(AgreementRefusedException.invalid("transactionType", List.of("LONG_TERM_RENT"))),
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed.",
                List.of(ValidationError.withAllowed("transactionType", "This value is not accepted.",
                        List.of("LONG_TERM_RENT"))));
        assertBody(handler.handleRefused(AgreementRefusedException.notAssignee()), HttpStatus.FORBIDDEN,
                "AGREEMENT_NOT_ASSIGNEE", "Only the lead's assigned Sale may draft an agreement.", List.of());
        assertBody(handler.handleRefused(AgreementRefusedException.notEligible(List.of(ReasonGroup.BASIS,
                        ReasonGroup.OWNER))), HttpStatus.CONFLICT, "AGREEMENT_NOT_ELIGIBLE",
                "This unit cannot get an agreement now.",
                List.of(ValidationError.of("reasonGroups.BASIS", "This group stops the agreement."),
                        ValidationError.of("reasonGroups.OWNER", "This group stops the agreement.")));
        assertBody(handler.handleRefused(AgreementRefusedException.exists()), HttpStatus.CONFLICT, "AGREEMENT_EXISTS",
                "This unit already has an agreement in progress.", List.of());
    }

    /** E05-S5 spec S5-D14: the owner's refusals with their status, code, fixed message and entries. */
    @Test
    void refused_ownerReasons_statusCodeAndEntries() {
        assertBody(handler.handleRefused(AgreementRefusedException.accountLocked()), HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                "Too many wrong passwords. Try again later.", List.of());
        assertBody(handler.handleRefused(AgreementRefusedException.notAwaitingOwner(List.of(1))), HttpStatus.CONFLICT,
                "AGREEMENT_NOT_AWAITING_OWNER", "This version is not waiting for your answer.",
                List.of(ValidationError.of("versions[1]", "This version cannot be answered now.")));
        assertBody(handler.handleRefused(AgreementRefusedException.notAwaitingOwner(List.of())), HttpStatus.CONFLICT,
                "AGREEMENT_NOT_AWAITING_OWNER", "This version is not waiting for your answer.", List.of());
        assertBody(handler.handleRefused(AgreementRefusedException.notEligibleItems(Map.of(0, List.of(ReasonGroup.UNIT)))),
                HttpStatus.CONFLICT, "AGREEMENT_NOT_ELIGIBLE", "This unit cannot get an agreement now.",
                List.of(ValidationError.of("versions[0].reasonGroups.UNIT", "This group stops the agreement.")));
        assertBody(handler.handleRefused(AgreementRefusedException.statementOutdated()), HttpStatus.CONFLICT,
                "AGREEMENT_STATEMENT_OUTDATED", "The confirmation text has changed. Read it again.", List.of());
        assertBody(handler.handleRefused(AgreementRefusedException.newDraftNotAllowed()), HttpStatus.CONFLICT,
                "AGREEMENT_NEW_DRAFT_NOT_ALLOWED", "A new draft can be opened only when no version is pending.",
                List.of());
        assertBody(handler.handleContent(new AgreementContentException("reason")), HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED", "Validation failed.", List.of(ValidationError.of("reason",
                        "This value is not accepted.")));
    }

    /** E05-S6 spec S6-D16: the activation's refusals with their status, code and fixed message, and no entry. */
    @Test
    void handleRefused_activationReasons_statusCodeMessageNoEntry() {
        assertBody(handler.handleRefused(AgreementRefusedException.notIndependent()), HttpStatus.FORBIDDEN,
                "AGREEMENT_ACTIVATOR_NOT_INDEPENDENT", "Only a manager who did not draft this version and is not the"
                        + " lead's assigned Sale may decide its activation.", List.of());
        assertBody(handler.handleRefused(AgreementRefusedException.notAwaitingActivation()), HttpStatus.CONFLICT,
                "AGREEMENT_NOT_AWAITING_ACTIVATION", "This version is not waiting for activation.", List.of());
        assertBody(handler.handleRefused(AgreementRefusedException.periodEnded()), HttpStatus.CONFLICT,
                "AGREEMENT_VERSION_PERIOD_ENDED", "This version's period has ended.", List.of());
    }

    @Test
    void handlePeriod_field_validationFailedWithThatField() {
        assertBody(handler.handlePeriod(new AgreementPeriodException("endDate")), HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED", "Validation failed.",
                List.of(ValidationError.of("endDate", "This value is not accepted.")));
    }

    @Test
    void handleExists_conflictAgreementExists() {
        assertBody(handler.handleExists(new AgreementExistsException()), HttpStatus.CONFLICT, "AGREEMENT_EXISTS",
                "This unit already has an agreement in progress.", List.of());
    }

    @Test
    void handleConflict_lockFailure_concurrentModification() {
        assertBody(handler.handleConflict(new PessimisticLockingFailureException("x")), HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION", "Something changed at the same time. Try again.", List.of());
        assertBody(handler.handleConflict(new CannotAcquireLockException("x")), HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION", "Something changed at the same time. Try again.", List.of());
    }

    /** E05-S3 spec S3-D8: a refused content value names its field path only, never the value. */
    @Test
    void handleContent_fieldEntryWithoutValue() {
        assertBody(handler.handleContent(new AgreementContentException("amount").within("terms.mandatoryFees[2].")),
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed.",
                List.of(ValidationError.of("terms.mandatoryFees[2].amount", "This value is not accepted.")));
    }

    @Test
    void handleRefused_notDraftAndStale_conflictBodies() {
        assertBody(handler.handleRefused(AgreementRefusedException.notDraft()), HttpStatus.CONFLICT,
                "AGREEMENT_NOT_DRAFT", "This agreement has no draft to edit.", List.of());
        assertBody(handler.handleRefused(AgreementRefusedException.stale()), HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION", "Something changed at the same time. Try again.",
                List.of(ValidationError.of("expectedDataVersion", "This value is out of date.")));
    }

    private static void assertBody(ResponseEntity<ErrorResponse<ValidationError>> response, HttpStatus status,
                                   String code, String message, List<ValidationError> errors) {
        assertEquals(status, response.getStatusCode());
        assertEquals(code, response.getBody().code());
        assertEquals(message, response.getBody().message());
        assertEquals(errors, response.getBody().errors());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
    }
}
