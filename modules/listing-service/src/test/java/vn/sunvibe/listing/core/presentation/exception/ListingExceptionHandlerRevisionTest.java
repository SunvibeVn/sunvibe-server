package vn.sunvibe.listing.core.presentation.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.exception.RevisionNotSubmittableException;
import vn.sunvibe.listing.core.domain.exception.RevisionStateException;
import vn.sunvibe.listing.core.domain.model.listing.RevisionPart;
import vn.sunvibe.listing.core.domain.model.listing.RevisionRule;
import vn.sunvibe.listing.core.domain.model.listing.RevisionViolation;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The error bodies of the revision commands (listing-publish E06-S2 spec section 4, plan E06S2-P8; ADR-010): a stable
 * code and a fixed message per refusal, the 422's entries as {@code <part>.<rule>} with one fixed message per rule,
 * never the text that broke a rule. The new cases beside {@code ListingExceptionHandlerTest}, which stays untouched
 * (plan E06S2-P1).
 */
class ListingExceptionHandlerRevisionTest {

    private final ListingExceptionHandler handler = new ListingExceptionHandler();

    @Test
    void revisionState_eachReason_409ItsCode() {
        Map<RevisionStateException.Reason, String> messages = Map.of(
                RevisionStateException.Reason.REVISION_NOT_DRAFT, "The revision is not a draft.",
                RevisionStateException.Reason.REVISION_NOT_PENDING, "The revision is not waiting for review.",
                RevisionStateException.Reason.OPEN_REVISION_EXISTS, "The listing already has an open revision.");
        for (Map.Entry<RevisionStateException.Reason, String> expected : messages.entrySet()) {
            ResponseEntity<ErrorResponse<ValidationError>> answer =
                    handler.handleRevisionState(new RevisionStateException(expected.getKey()));

            assertEquals(HttpStatus.CONFLICT, answer.getStatusCode());
            assertEquals(expected.getKey().name(), answer.getBody().code());
            assertEquals(expected.getValue(), answer.getBody().message());
            assertEquals(List.of(), answer.getBody().errors());
        }
    }

    @Test
    void notSubmittable_violations_422EntriesInOrderNoText() {
        List<RevisionViolation> violations = List.of(
                new RevisionViolation(RevisionPart.TITLE, RevisionRule.TOO_SHORT),
                new RevisionViolation(RevisionPart.DESCRIPTION, RevisionRule.REQUIRED),
                new RevisionViolation(RevisionPart.TERMS, RevisionRule.REQUIRED),
                new RevisionViolation(RevisionPart.TITLE, RevisionRule.PHONE_NUMBER),
                new RevisionViolation(RevisionPart.TITLE, RevisionRule.EMAIL),
                new RevisionViolation(RevisionPart.DESCRIPTION, RevisionRule.LINK),
                new RevisionViolation(RevisionPart.DESCRIPTION, RevisionRule.SOCIAL_HANDLE),
                new RevisionViolation(RevisionPart.DESCRIPTION, RevisionRule.FORBIDDEN_WORD),
                new RevisionViolation(RevisionPart.DESCRIPTION, RevisionRule.TOO_LONG));

        ResponseEntity<ErrorResponse<ValidationError>> answer =
                handler.handleNotSubmittable(new RevisionNotSubmittableException(violations));

        assertEquals(422, answer.getStatusCode().value());
        assertEquals("REVISION_NOT_SUBMITTABLE", answer.getBody().code());
        assertEquals("The revision cannot be submitted.", answer.getBody().message());
        assertEquals(List.of(
                ValidationError.of("title.TOO_SHORT", "Too short."),
                ValidationError.of("description.REQUIRED", "Required."),
                ValidationError.of("terms.REQUIRED", "Required."),
                ValidationError.of("title.PHONE_NUMBER", "Holds what reads as a phone number."),
                ValidationError.of("title.EMAIL", "Holds an e-mail address."),
                ValidationError.of("description.LINK", "Holds a link."),
                ValidationError.of("description.SOCIAL_HANDLE", "Holds a social or chat account."),
                ValidationError.of("description.FORBIDDEN_WORD", "Holds a word a listing may not use."),
                ValidationError.of("description.TOO_LONG", "Too long.")), answer.getBody().errors());
    }

    /**
     * listing-publish E06-S7 spec 4.3, E06S7-D10 (E-20): a fee-name violation's entry is {@code feeName.<position>.<RULE>}
     * with the rule's fixed message; the other entries are unchanged.
     */
    @Test
    void notSubmittable_feeNameViolation_entryNamesPositionAndRule() {
        List<RevisionViolation> violations = List.of(
                new RevisionViolation(RevisionPart.DESCRIPTION, RevisionRule.LINK),
                new RevisionViolation(RevisionPart.FEE_NAME, RevisionRule.PHONE_NUMBER, 2),
                new RevisionViolation(RevisionPart.FEE_NAME, RevisionRule.FORBIDDEN_WORD, 3));

        ResponseEntity<ErrorResponse<ValidationError>> answer =
                handler.handleNotSubmittable(new RevisionNotSubmittableException(violations));

        assertEquals(422, answer.getStatusCode().value());
        assertEquals(List.of(
                ValidationError.of("description.LINK", "Holds a link."),
                ValidationError.of("feeName.2.PHONE_NUMBER", "Holds what reads as a phone number."),
                ValidationError.of("feeName.3.FORBIDDEN_WORD", "Holds a word a listing may not use.")),
                answer.getBody().errors());
    }

    @Test
    void listingChanged_409ConcurrentModification() {
        ResponseEntity<ErrorResponse<ValidationError>> answer = handler.handleListingChanged(new ListingChangedException());

        assertEquals(HttpStatus.CONFLICT, answer.getStatusCode());
        assertEquals("CONCURRENT_MODIFICATION", answer.getBody().code());
        assertEquals(ListingExceptionHandler.CONFLICT_MESSAGE, answer.getBody().message());
        assertEquals(List.of(), answer.getBody().errors());
    }

    @Test
    void refused_archivedAndTermsSourceAbsent_409() {
        ResponseEntity<ErrorResponse<ValidationError>> archived = handler.handleRefused(ListingRefusedException.archived());
        ResponseEntity<ErrorResponse<ValidationError>> absent =
                handler.handleRefused(ListingRefusedException.termsSourceAbsent());

        assertEquals(HttpStatus.CONFLICT, archived.getStatusCode());
        assertEquals("LISTING_ARCHIVED", archived.getBody().code());
        assertEquals("The listing is archived.", archived.getBody().message());
        assertEquals(List.of(), archived.getBody().errors());
        assertEquals(HttpStatus.CONFLICT, absent.getStatusCode());
        assertEquals("TERMS_SOURCE_ABSENT", absent.getBody().code());
        assertEquals("These terms are not available.", absent.getBody().message());
        assertEquals(List.of(), absent.getBody().errors());
    }
}
