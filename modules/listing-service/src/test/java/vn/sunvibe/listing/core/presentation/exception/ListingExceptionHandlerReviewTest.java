package vn.sunvibe.listing.core.presentation.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.exception.ReviewerNotIndependentException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The error bodies of the review (listing-publish E06-S4 spec sections 4.3 to 4.6, E06S4-D5, D7, D14; ADR-010): the
 * independence refusal with one entry per reason in contract order, the copy's refusal and the terms mismatch, each a
 * stable code and a fixed message. Beside {@code ListingExceptionHandlerTest} and
 * {@code ListingExceptionHandlerRevisionTest}, which stay untouched.
 */
class ListingExceptionHandlerReviewTest {

    private final ListingExceptionHandler handler = new ListingExceptionHandler();

    @Test
    void notIndependent_403EntriesInOrder() {
        ResponseEntity<ErrorResponse<ValidationError>> answer = handler.handleNotIndependent(
                new ReviewerNotIndependentException(List.of(ReviewIneligibility.AUTHOR,
                        ReviewIneligibility.ASSIGNED_SALE)));

        assertEquals(HttpStatus.FORBIDDEN, answer.getStatusCode());
        assertEquals("REVIEWER_NOT_INDEPENDENT", answer.getBody().code());
        assertEquals("The caller may not decide this revision.", answer.getBody().message());
        assertEquals(List.of(
                ValidationError.of("reasons.AUTHOR", ListingExceptionHandler.REASON_MESSAGE),
                ValidationError.of("reasons.ASSIGNED_SALE", ListingExceptionHandler.REASON_MESSAGE)),
                answer.getBody().errors());

        ResponseEntity<ErrorResponse<ValidationError>> assigneeOnly = handler.handleNotIndependent(
                new ReviewerNotIndependentException(List.of(ReviewIneligibility.ASSIGNED_SALE)));
        assertEquals(List.of("reasons.ASSIGNED_SALE"),
                assigneeOnly.getBody().errors().stream().map(ValidationError::field).toList());
    }

    @Test
    void notCopyable_409Message() {
        ResponseEntity<ErrorResponse<ValidationError>> answer = handler.handleRevisionState(
                new RevisionStateException(RevisionStateException.Reason.REVISION_NOT_COPYABLE));

        assertEquals(HttpStatus.CONFLICT, answer.getStatusCode());
        assertEquals("REVISION_NOT_COPYABLE", answer.getBody().code());
        assertEquals("The revision cannot be copied.", answer.getBody().message());
        assertEquals(List.of(), answer.getBody().errors());
    }

    @Test
    void termsMismatch_409() {
        ResponseEntity<ErrorResponse<ValidationError>> answer =
                handler.handleRefused(ListingRefusedException.termsMismatch());

        assertEquals(HttpStatus.CONFLICT, answer.getStatusCode());
        assertEquals("REVISION_TERMS_MISMATCH", answer.getBody().code());
        assertEquals("The revision's terms equal no version of the agreement.", answer.getBody().message());
        assertEquals(List.of(), answer.getBody().errors());
    }
}
