package vn.sunvibe.listing.core.presentation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.exception.ReviewerNotIndependentException;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.ListingCodeTakenException;
import vn.sunvibe.listing.core.domain.exception.RevisionNotSubmittableException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.RevisionPart;
import vn.sunvibe.listing.core.domain.model.listing.RevisionRule;
import vn.sunvibe.listing.core.domain.model.listing.RevisionViolation;
import vn.sunvibe.listing.core.presentation.controller.ListingController;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ADR-010 error bodies of the listing endpoints (listing-publish E06-S1 spec section 4, E06S1-P3). No message quotes a
 * value of the request or of a row; an entry names a field or a reason group only, and nothing names a lead or a
 * listing. The class runs before the global handler, and within it the most specific exception type wins over the
 * catch-all.
 */
@RestControllerAdvice(basePackageClasses = ListingController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ListingExceptionHandler {

    /**
     * listing BR-5: the message of the one "not found" body, which the public path's filter writes too, so a hidden
     * listing and a refused code answer alike (listing-publish E06-S7 spec E06S7-D8).
     */
    public static final String NOT_FOUND_MESSAGE = "The resource was not found.";
    static final String FIELD_MESSAGE = "This value is not accepted.";
    static final String GROUP_MESSAGE = "This group stops the listing.";
    static final String CONFLICT_MESSAGE = "Something changed at the same time. Try again.";
    static final String NOT_SUBMITTABLE = "REVISION_NOT_SUBMITTABLE";
    static final String NOT_INDEPENDENT = "REVIEWER_NOT_INDEPENDENT";
    static final String REASON_MESSAGE = "This reason stops the decision.";
    static final String DISPLAY_REASON_MESSAGE = "This reason stops the put live.";
    private static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);

    /**
     * A refused listing command: the reason's status, code and message, and one entry per reason group in order; a
     * refused put live (E06-S6 spec E06S6-D19) has one entry per display reason in its fixed order, E05's groups right
     * after {@code reasons.ADVERTISING_RIGHT}. Every other refusal's body is as before.
     */
    @ExceptionHandler(ListingRefusedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleRefused(ListingRefusedException e) {
        ListingRefusedException.Reason reason = e.reason();
        List<ValidationError> groups = e.groups().stream()
                .map(group -> ValidationError.of("reasonGroups." + group.name(), GROUP_MESSAGE))
                .toList();
        List<ValidationError> entries = groups;
        if (!e.displayReasons().isEmpty()) {
            entries = new ArrayList<>();
            for (DisplayReason displayReason : e.displayReasons()) {
                entries.add(ValidationError.of("reasons." + displayReason.name(), DISPLAY_REASON_MESSAGE));
                if (displayReason == DisplayReason.ADVERTISING_RIGHT) {
                    entries.addAll(groups);
                }
            }
        }
        return ResponseEntity.status(reason.status()).body(ErrorResponse.of(reason.name(), reason.message(), entries));
    }

    /** listing BR-5: unknown, malformed and out of scope answer one body, naming nothing. */
    @ExceptionHandler(ListingNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotFound(ListingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE, List.of()));
    }

    /** listing-detail BR-15: a concurrent insert of the same code (one in 10^12) is a conflict the caller may retry. */
    @ExceptionHandler(ListingCodeTakenException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleCodeTaken(ListingCodeTakenException e) {
        return conflict();
    }

    /**
     * listing section 3.3, listing-publish BR-6 (E06-S2 spec section 4, plan E06S2-P8): a revision's status refuses the
     * command; the reason's name is the code, its message fixed, no entry.
     */
    @ExceptionHandler(RevisionStateException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleRevisionState(RevisionStateException e) {
        String message = switch (e.reason()) {
            case REVISION_NOT_DRAFT -> "The revision is not a draft.";
            case REVISION_NOT_PENDING -> "The revision is not waiting for review.";
            case OPEN_REVISION_EXISTS -> "The listing already has an open revision.";
            case REVISION_NOT_COPYABLE -> "The revision cannot be copied.";
            case REVISION_NOT_APPROVED -> "The revision is not approved.";
        };
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(e.reason().name(), message, List.of()));
    }

    /**
     * listing-publish US-2 AC-4, E-4 (E06-S2 spec E06S2-D7, plan E06S2-P8): 422 with one entry per violation, in order,
     * its field {@code <part>.<rule>} and a fixed message per rule; never the text that broke a rule. A fee name's entry
     * is {@code feeName.<position>.<rule>} (E06-S7 spec 4.3, E06S7-D10, E-20).
     */
    @ExceptionHandler(RevisionNotSubmittableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotSubmittable(RevisionNotSubmittableException e) {
        List<ValidationError> entries = e.violations().stream()
                .map(violation -> ValidationError.of(fieldOf(violation), ruleMessage(violation.rule())))
                .toList();
        return ResponseEntity.status(UNPROCESSABLE)
                .body(ErrorResponse.of(NOT_SUBMITTABLE, "The revision cannot be submitted.", entries));
    }

    /**
     * listing-publish BR-7, US-4 AC-4 (E06-S4 spec section 4.3, E06S4-D5 step 8): the caller is an author of the revision
     * or its agreement's assigned Sale; one entry per reason, in contract order, naming no user.
     */
    @ExceptionHandler(ReviewerNotIndependentException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotIndependent(ReviewerNotIndependentException e) {
        List<ValidationError> entries = e.reasons().stream()
                .map(reason -> ValidationError.of("reasons." + reason.name(), REASON_MESSAGE))
                .toList();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(NOT_INDEPENDENT,
                "The caller may not decide this revision.", entries));
    }

    /** listing BR-3 (E06-S2 spec E06S2-D19): another command changed the listing since it was read; read again. */
    @ExceptionHandler(ListingChangedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleListingChanged(ListingChangedException e) {
        return conflict();
    }

    /** A lock or serialization failure is a conflict the caller may retry, never a 500. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleConflict(ConcurrencyFailureException e) {
        return conflict();
    }

    /** A request body that breaks its bean constraints: one entry per field, never its value. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidBody(MethodArgumentNotValidException e) {
        Set<String> fields = new LinkedHashSet<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.add(error.getField());
        }
        List<ValidationError> entries = new ArrayList<>();
        fields.forEach(field -> entries.add(ValidationError.of(field, FIELD_MESSAGE)));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, "Validation failed.", entries));
    }

    /** A body that cannot be read; no entry, since its text is the caller's. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, "The request could not be read.", List.of()));
    }

    /**
     * Spring Security exceptions are rethrown so the 401 and 403 keep their own bodies; a Spring MVC error with a client
     * status keeps its status and headers. Anything else, an unknown reason code of E05 included, is a server error
     * whose details stay out of the response; its log line has the exception class and the correlation id only.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(springError.getStatusCode())
                    .headers(springError.getHeaders())
                    .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, "The request cannot be processed.", List.of()));
        }
        log.error("Unexpected error in listing: {} (correlationId={})", e.getClass().getName(), CorrelationId.current());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "Unexpected server error.", List.of()));
    }

    private static String fieldOf(RevisionViolation violation) {
        if (violation.part() == RevisionPart.FEE_NAME) {
            return "feeName." + violation.feePositionOrNull() + "." + violation.rule().name();
        }
        return violation.part().name().toLowerCase(Locale.ROOT) + "." + violation.rule().name();
    }

    /** One fixed message per submission rule (plan E06S2-P8). */
    private static String ruleMessage(RevisionRule rule) {
        return switch (rule) {
            case REQUIRED -> "Required.";
            case TOO_SHORT -> "Too short.";
            case TOO_LONG -> "Too long.";
            case PHONE_NUMBER -> "Holds what reads as a phone number.";
            case EMAIL -> "Holds an e-mail address.";
            case LINK -> "Holds a link.";
            case SOCIAL_HANDLE -> "Holds a social or chat account.";
            case FORBIDDEN_WORD -> "Holds a word a listing may not use.";
        };
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.CONCURRENT_MODIFICATION, CONFLICT_MESSAGE, List.of()));
    }
}
