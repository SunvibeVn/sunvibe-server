package vn.sunvibe.brokerage.core.presentation.exception;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The refusals of the stops (brokerage-agreement E05-S8 spec S8-D12; ADR-010): each with its status, its stable code and
 * its fixed message that quotes no value, no entry.
 */
class BrokerageExceptionHandlerStopTest {

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
    void handleRefused_eachStopReason_statusCodeFixedMessage() {
        Map<AgreementRefusedException, List<Object>> expected = Map.of(
                AgreementRefusedException.assigneeOnly(), List.of(HttpStatus.FORBIDDEN, "AGREEMENT_ASSIGNEE_ONLY",
                        "Only the lead's assigned Sale may do this."),
                AgreementRefusedException.releaserNotIndependent(), List.of(HttpStatus.FORBIDDEN,
                        "AGREEMENT_RELEASER_NOT_INDEPENDENT",
                        "Only a manager who is not the lead's assigned Sale may lift the suspension."),
                AgreementRefusedException.terminationNotAllowed(), List.of(HttpStatus.FORBIDDEN,
                        "AGREEMENT_TERMINATION_NOT_ALLOWED", "Only a manager may end an agreement as Sunvibe's decision."),
                AgreementRefusedException.notInEffect(), List.of(HttpStatus.CONFLICT, "AGREEMENT_NOT_IN_EFFECT",
                        "This agreement is not in effect."),
                AgreementRefusedException.ended(), List.of(HttpStatus.CONFLICT, "AGREEMENT_ENDED",
                        "This agreement has ended."),
                AgreementRefusedException.alreadySuspended(), List.of(HttpStatus.CONFLICT,
                        "AGREEMENT_ALREADY_SUSPENDED", "This agreement is already suspended."),
                AgreementRefusedException.notSuspended(), List.of(HttpStatus.CONFLICT, "AGREEMENT_NOT_SUSPENDED",
                        "This agreement is not suspended."),
                AgreementRefusedException.tookEffect(), List.of(HttpStatus.CONFLICT, "AGREEMENT_TOOK_EFFECT",
                        "This agreement has taken effect: end it instead."));

        expected.forEach((refusal, values) -> {
            ResponseEntity<ErrorResponse<ValidationError>> response = handler.handleRefused(refusal);

            assertEquals(values.get(0), response.getStatusCode(), values.get(1).toString());
            assertEquals(values.get(1), response.getBody().code());
            assertEquals(values.get(2), response.getBody().message());
            assertEquals(List.of(), response.getBody().errors());
            assertEquals(CORRELATION_ID, response.getBody().correlationId());
        });
    }
}
