package vn.sunvibe.listing.core.presentation.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The error bodies of the put live (listing-publish E06-S6 spec section 4.1, E06S6-D19; PL-19; ADR-010): a stable code
 * and a fixed message, naming no value. Beside the handler tests of E06-S1, E06-S2 and E06-S4, which stay untouched.
 */
class ListingExceptionHandlerPublicationTest {

    private final ListingExceptionHandler handler = new ListingExceptionHandler();

    @Test
    void revisionNotApproved_409CodeMessageNoEntry() {
        ResponseEntity<ErrorResponse<ValidationError>> answer = handler.handleRevisionState(
                new RevisionStateException(RevisionStateException.Reason.REVISION_NOT_APPROVED));

        assertEquals(HttpStatus.CONFLICT, answer.getStatusCode());
        assertEquals("REVISION_NOT_APPROVED", answer.getBody().code());
        assertEquals("The revision is not approved.", answer.getBody().message());
        assertEquals(List.of(), answer.getBody().errors());
    }

    @Test
    void putLiveRefused_reasonsAndGroupsInOrder_fixedMessages() {
        ResponseEntity<ErrorResponse<ValidationError>> answer = handler.handleRefused(
                ListingRefusedException.putLiveRefused(List.of(DisplayReason.LISTING_STATE,
                        DisplayReason.ADVERTISING_RIGHT, DisplayReason.FRESHNESS),
                        List.of(AgreementReasonGroup.SUSPENDED, AgreementReasonGroup.ENDED)));

        assertEquals(HttpStatus.CONFLICT, answer.getStatusCode());
        assertEquals("PUT_LIVE_REFUSED", answer.getBody().code());
        assertEquals("The revision cannot go live now.", answer.getBody().message());
        assertEquals(List.of(
                ValidationError.of("reasons.LISTING_STATE", "This reason stops the put live."),
                ValidationError.of("reasons.ADVERTISING_RIGHT", "This reason stops the put live."),
                ValidationError.of("reasonGroups.SUSPENDED", ListingExceptionHandler.GROUP_MESSAGE),
                ValidationError.of("reasonGroups.ENDED", ListingExceptionHandler.GROUP_MESSAGE),
                ValidationError.of("reasons.FRESHNESS", "This reason stops the put live.")),
                answer.getBody().errors());
    }
}
