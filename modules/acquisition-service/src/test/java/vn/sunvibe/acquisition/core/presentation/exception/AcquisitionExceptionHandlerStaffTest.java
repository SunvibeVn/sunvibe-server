package vn.sunvibe.acquisition.core.presentation.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.sunvibe.acquisition.core.application.exception.AdministrativeAreaNotFoundException;
import vn.sunvibe.acquisition.core.application.exception.ConcurrentLeadModificationException;
import vn.sunvibe.acquisition.core.application.exception.IneligibleAssigneeException;
import vn.sunvibe.acquisition.core.application.exception.OwnerLeadNotFoundException;
import vn.sunvibe.acquisition.core.application.shared.LeadVisibility;
import vn.sunvibe.acquisition.core.application.usecase.impl.OwnerLeadQueryService;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.testsupport.InMemoryConsentRecordRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryDuplicateCandidateRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadAccessLogRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadActivityRepository;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadRepository;
import vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.exception.ResourceNotFoundException;
import vn.sunvibe.shared.logging.CorrelationId;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Status mapping of the staff lead and territory endpoints (acquisition-owner-lead-queue spec 6, error table; ADR-010;
 * Q9-4, Q9-6, Q9-7, Q9-10). Each exception goes through the handler method Spring MVC would pick, as in
 * {@link AcquisitionExceptionHandlerTest}. 401 and 403 stay with Spring Security.
 */
class AcquisitionExceptionHandlerStaffTest {

    private static final String CORRELATION_ID = "01J0000000000000000000TEST";
    private static final String OTHER_CORRELATION_ID = "01J0000000000000000000OTHR";
    /** Stands for a lead id the caller sent: a 404 must not echo it. */
    private static final String LEAD_ID = "01J000000000000000000LEAD1";
    private static final String UNKNOWN_LEAD_ID = "01JZZZZZZZZZZZZZZZZZZZZZZZ";
    private static final String SALE = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String OTHER_SALE = "01JSALEBBBBBBBBBBBBBBBBBBB";

    private final AcquisitionExceptionHandler handler = new AcquisitionExceptionHandler();
    private final ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(AcquisitionExceptionHandler.class);

    @BeforeEach
    void setUp() {
        MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationId.MDC_KEY);
    }

    /**
     * Stand-in for the list endpoint, so the Spring exceptions get real query parameters. An instance method because
     * Bean Validation ignores static ones; the modules compile without {@code -parameters}, so the names come from
     * the annotations only.
     */
    @SuppressWarnings("unused")
    void list(@RequestParam(name = "scope", defaultValue = "mine") String scope,
                     @RequestParam(name = "status", required = false) List<OwnerLeadStatus> status,
                     @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
                     @RequestParam(name = "size", defaultValue = "20") @Min(1) int size) {
    }

    static Stream<Arguments> mapping() throws Exception {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Method list = listMethod();
        ConstraintViolationException pageViolation = new ConstraintViolationException(
                factory.getValidator().forExecutables().validateParameters(new AcquisitionExceptionHandlerStaffTest(),
                        list, new Object[]{"mine", null, -1, 20}).stream().collect(Collectors.toSet()));
        factory.close();

        return Stream.of(
                // 404 NOT_FOUND, no field
                Arguments.of(new OwnerLeadNotFoundException(), 404, ErrorCode.NOT_FOUND, List.of(), null),
                Arguments.of(AdministrativeAreaNotFoundException.ward(), 404, ErrorCode.NOT_FOUND, List.of(), null),
                Arguments.of(new ResourceNotFoundException("Thing " + LEAD_ID + " not found.") {
                }, 404, ErrorCode.NOT_FOUND, List.of(), null),
                // 400 VALIDATION_FAILED on the field
                Arguments.of(new InvalidLeadCommandException("reason", "A reason is required to close a lead."),
                        400, ErrorCode.VALIDATION_FAILED, List.of("reason"), null),
                Arguments.of(new InvalidLeadCommandException("duplicateOfLeadId", "The lead to merge into is not "
                                + "valid."),
                        400, ErrorCode.VALIDATION_FAILED, List.of("duplicateOfLeadId"), null),
                Arguments.of(new MethodArgumentTypeMismatchException("NOPE", OwnerLeadStatus.class, "status",
                                parameter(list, 1), null),
                        400, ErrorCode.VALIDATION_FAILED, List.of("status"), null),
                Arguments.of(new HandlerMethodValidationException(methodValidation(list, 3, 0)),
                        400, ErrorCode.VALIDATION_FAILED, List.of("size"), null),
                Arguments.of(new HandlerMethodValidationException(methodValidation(list, 0, "x")),
                        400, ErrorCode.VALIDATION_FAILED, List.of("scope"), null),
                Arguments.of(pageViolation, 400, ErrorCode.VALIDATION_FAILED, List.of("page"), null),
                // 409 INVALID_STATE_TRANSITION with the allowed states
                Arguments.of(new InvalidLeadTransitionException(OwnerLeadStatus.NEW, OwnerLeadStatus.ONBOARDED,
                                List.of(OwnerLeadStatus.ASSIGNED, OwnerLeadStatus.REJECTED)),
                        409, ErrorCode.INVALID_STATE_TRANSITION, List.of("toStatus"), List.of("ASSIGNED", "REJECTED")),
                Arguments.of(new InvalidLeadTransitionException(OwnerLeadStatus.REJECTED, OwnerLeadStatus.CONTACTED,
                                List.of()),
                        409, ErrorCode.INVALID_STATE_TRANSITION, List.of("toStatus"), List.of()),
                Arguments.of(new InvalidLeadTransitionException(OwnerLeadStatus.REJECTED, null, List.of()),
                        409, ErrorCode.INVALID_STATE_TRANSITION, List.of("status"), List.of()),
                // 409 CONCURRENT_MODIFICATION on expectedVersion
                Arguments.of(new ConcurrentLeadModificationException(),
                        409, ErrorCode.CONCURRENT_MODIFICATION, List.of("expectedVersion"), null),
                // the note request has no expectedVersion: its 409 names no field
                Arguments.of(ConcurrentLeadModificationException.withoutField(),
                        409, ErrorCode.CONCURRENT_MODIFICATION, List.of(), null),
                Arguments.of(new ObjectOptimisticLockingFailureException("OwnerLeadEntity", LEAD_ID),
                        409, ErrorCode.CONCURRENT_MODIFICATION, List.of("expectedVersion"), null),
                Arguments.of(new OptimisticLockException("Row was updated or deleted by another transaction"),
                        409, ErrorCode.CONCURRENT_MODIFICATION, List.of("expectedVersion"), null),
                // 422 INELIGIBLE_ASSIGNEE on the field that named the person
                Arguments.of(new IneligibleAssigneeException("assigneeUserId"),
                        422, ErrorCode.INELIGIBLE_ASSIGNEE, List.of("assigneeUserId"), null),
                Arguments.of(new IneligibleAssigneeException("userId"),
                        422, ErrorCode.INELIGIBLE_ASSIGNEE, List.of("userId"), null));
    }

    @ParameterizedTest
    @MethodSource("mapping")
    void handle_eachStaffException_returnsStatusCodeFieldsAndAllowedValuesOfSpec6(
            Exception exception, int status, String code, List<String> fields, List<String> allowedValues)
            throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(exception);

        assertEquals(status, response.getStatusCode().value());
        ErrorResponse<ValidationError> body = response.getBody();
        assertNotNull(body);
        assertEquals(code, body.code());
        assertEquals(fields, body.errors().stream().map(ValidationError::field).toList());
        if (allowedValues == null) {
            body.errors().forEach(error -> assertNull(error.allowedValues(), error.field()));
        } else {
            assertEquals(allowedValues, body.errors().get(0).allowedValues());
        }
        assertEquals(CORRELATION_ID, body.correlationId());
        assertFalse(body.message().isBlank());
        assertFalse(body.toString().contains(LEAD_ID), body.toString());
    }

    /**
     * acquisition-owner-lead-queue Q9-4: both failures come from the real query service and visibility check (with
     * in-memory repositories): an unknown id, and a lead that exists but is held by another SALE.
     */
    @Test
    void handle_notFoundMissingAndInvisible_haveIdenticalBodiesExceptCorrelationId() throws Exception {
        InMemoryOwnerLeadRepository leads = new InMemoryOwnerLeadRepository();
        OwnerLeadQueryService queries = new OwnerLeadQueryService(leads, new InMemoryOwnerLeadActivityRepository(),
                new InMemoryOwnerLeadAccessLogRepository(), new InMemoryDuplicateCandidateRepository(leads),
                new InMemoryConsentRecordRepository(), new LeadVisibility(leads),
                new AtomicLong(5_000L)::incrementAndGet);
        OwnerLead heldByOther = OwnerLeadFixture.newLead(OwnerLeadStatus.ASSIGNED, OTHER_SALE, "20194",
                OwnerLeadFixture.LONG_AGO);
        leads.save(heldByOther);
        LeadViewer sale = new LeadViewer(SALE, LeadAccess.OWN);

        Exception missingError = assertThrows(OwnerLeadNotFoundException.class,
                () -> queries.get(sale, UNKNOWN_LEAD_ID));
        Exception invisibleError = assertThrows(OwnerLeadNotFoundException.class,
                () -> queries.get(sale, heldByOther.getId().getValue()));

        ErrorResponse<ValidationError> missing = handle(missingError).getBody();
        MDC.put(CorrelationId.MDC_KEY, OTHER_CORRELATION_ID);
        ResponseEntity<ErrorResponse<ValidationError>> invisibleResponse = handle(invisibleError);
        ErrorResponse<ValidationError> invisible = invisibleResponse.getBody();

        assertEquals(404, invisibleResponse.getStatusCode().value());
        assertNotNull(missing);
        assertNotNull(invisible);
        assertNotEquals(missing.correlationId(), invisible.correlationId());
        assertEquals(new ErrorResponse<>(missing.code(), missing.message(), missing.errors(), null),
                new ErrorResponse<>(invisible.code(), invisible.message(), invisible.errors(), null));
        assertEquals(List.of(), invisible.errors());
        assertFalse(invisible.toString().contains(heldByOther.getId().getValue()), invisible.toString());
    }

    @Test
    void handle_invalidTransition_usesFixedMessagesThatNameNoRequestedStatus() throws Exception {
        ErrorResponse<ValidationError> change = handle(new InvalidLeadTransitionException(OwnerLeadStatus.NEW,
                OwnerLeadStatus.ONBOARDED, List.of(OwnerLeadStatus.ASSIGNED))).getBody();
        ErrorResponse<ValidationError> closed = handle(new InvalidLeadTransitionException(OwnerLeadStatus.REJECTED,
                null, List.of())).getBody();

        assertNotNull(change);
        assertNotNull(closed);
        assertEquals("This status change is not allowed from the current status.", change.errors().get(0).message());
        assertEquals("The lead is closed and cannot be changed.", closed.errors().get(0).message());
        assertFalse(change.message().contains("ONBOARDED"), change.message());
        assertFalse(change.errors().get(0).message().contains("ONBOARDED"), change.errors().get(0).message());
        assertEquals(List.of("ASSIGNED"), change.errors().get(0).allowedValues());
    }

    @Test
    void handle_objectOptimisticLockWrappedAsCause_isConcurrentModification() throws Exception {
        IllegalStateException wrapper = new IllegalStateException("save failed",
                new ObjectOptimisticLockingFailureException("OwnerLeadEntity", LEAD_ID));

        ResponseEntity<ErrorResponse<ValidationError>> response = handle(wrapper);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(ErrorCode.CONCURRENT_MODIFICATION, response.getBody().code());
        assertEquals(List.of("expectedVersion"),
                response.getBody().errors().stream().map(ValidationError::field).toList());
        assertFalse(response.getBody().toString().contains(LEAD_ID), response.getBody().toString());
    }

    @Test
    void handle_optimisticLockWrappedAsCause_isConcurrentModification() throws Exception {
        TransactionSystemException commitFailure = new TransactionSystemException("Could not commit JPA transaction",
                new RuntimeException("rollback", new OptimisticLockException("stale")));

        ResponseEntity<ErrorResponse<ValidationError>> response = handle(commitFailure);

        assertEquals(409, response.getStatusCode().value());
        assertEquals(ErrorCode.CONCURRENT_MODIFICATION, response.getBody().code());
        assertEquals(List.of("expectedVersion"),
                response.getBody().errors().stream().map(ValidationError::field).toList());
    }

    @Test
    void handle_accessDenied_stillRethrown() {
        // The scope check of the list endpoint throws it; Spring Security keeps its {error,message,path} body
        AccessDeniedException denied = new AccessDeniedException("scope");

        assertSame(denied, assertThrows(AccessDeniedException.class, () -> handle(denied)));
    }

    /** Calls the handler method Spring MVC would choose for the exception. */
    @SuppressWarnings("unchecked")
    private ResponseEntity<ErrorResponse<ValidationError>> handle(Exception exception) throws Exception {
        Method method = resolver.resolveMethod(exception);
        assertNotNull(method, "no handler method for " + exception.getClass().getName());
        try {
            return (ResponseEntity<ErrorResponse<ValidationError>>) method.invoke(handler, exception);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static Method listMethod() {
        return Arrays.stream(AcquisitionExceptionHandlerStaffTest.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("list"))
                .findFirst()
                .orElseThrow();
    }

    private static MethodParameter parameter(Method method, int index) {
        return new MethodParameter(method, index);
    }

    /** The result Spring's method validation gives for one bad query parameter. */
    private static MethodValidationResult methodValidation(Method method, int index, Object value) {
        return MethodValidationResult.create(new Object(), method,
                List.of(new ParameterValidationResult(parameter(method, index), value,
                        List.of(new DefaultMessageSourceResolvable(new String[]{"Min"}, "must be valid")),
                        null, null, null, (error, type) -> null)));
    }
}
