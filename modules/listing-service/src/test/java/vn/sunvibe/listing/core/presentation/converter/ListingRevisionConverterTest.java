package vn.sunvibe.listing.core.presentation.converter;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.command.RevisionFeaturesInput;
import vn.sunvibe.listing.core.application.command.RevisionSaveCommand;
import vn.sunvibe.listing.core.application.query.ListingRevisionView;
import vn.sunvibe.listing.core.domain.model.listing.ChargeUnit;
import vn.sunvibe.listing.core.domain.model.listing.DepositBasis;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;
import vn.sunvibe.listing.core.domain.model.listing.RevisionPart;
import vn.sunvibe.listing.core.domain.model.listing.RevisionRule;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.RevisionViolation;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.presentation.dto.request.ListingFeaturesInput;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionContentRequest;
import vn.sunvibe.listing.core.presentation.dto.response.ListingDepositResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFeaturesResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFeeResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRentResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionDetailResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionProblemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingTermsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingUnitSnapshotResponse;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The revision requests to commands and the views to answers (listing-publish E06-S2 spec section 4, E06S2-D4,
 * E06S2-D6, plan E06S2-P3): a revision number is 1 to 9 decimal digits without a leading zero, else none; the texts
 * reach the command in their stored form; the rent in ADR-006's shape, the deposit and the fees in E05's flat forms.
 */
class ListingRevisionConverterTest {

    private final ListingRequestConverter requests = new ListingRequestConverter();
    private final ListingResponseConverter responses = new ListingResponseConverter();

    @Test
    void revisionNoOrNull_wellFormed_number() {
        assertEquals(1, ListingRequestConverter.revisionNoOrNull("1"));
        assertEquals(42, ListingRequestConverter.revisionNoOrNull("42"));
        assertEquals(999_999_999, ListingRequestConverter.revisionNoOrNull("999999999"));
    }

    @Test
    void revisionNoOrNull_malformed_null() {
        for (String text : Arrays.asList("0", "01", "abc", "1234567890", "-1", "+1", " 1", "1 ", "١", "", null)) {
            assertNull(ListingRequestConverter.revisionNoOrNull(text), String.valueOf(text));
        }
    }

    @Test
    void toCommand_content_normalizedTexts() {
        ListingFeaturesInput features = new ListingFeaturesInput("FULL", true, false, true, null, "SHARED", false, true,
                false, true, false, "ALLOWED", true);
        ListingRevisionContentRequest request = new ListingRevisionContentRequest(3L, "  Phòng mới  ",
                "Dòng một\r\nDòng hai\r", features);

        RevisionSaveCommand command = requests.toCommand("01JLISTING0000000000000001", 2, request);

        assertEquals("01JLISTING0000000000000001", command.listingId());
        assertEquals(2, command.revisionNoOrNull());
        assertEquals(3L, command.dataVersion());
        assertEquals("Phòng mới", command.titleOrNull());
        assertEquals("Dòng một\nDòng hai", command.descriptionOrNull());
        assertEquals(new RevisionFeaturesInput("FULL", true, false, true, null, "SHARED", false, true, false, true, false,
                "ALLOWED", true), command.features());
        assertNull(requests.toCommand("01JLISTING0000000000000001", 2,
                new ListingRevisionContentRequest(3L, "   ", null, features)).titleOrNull());
    }

    @Test
    void toResponse_terms_rentMonthDepositAndFeesFlat() {
        RevisionTerms terms = new RevisionTerms(TermsSource.PENDING_START, LocalDate.parse("2026-12-01"),
                RevisionFixtures.NOW, 9_500_000L, new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 5_000_000L), 12, 3,
                List.of(new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                        new RevisionTerms.Fee("Internet", FeeBasis.INCLUDED, null, null)));
        RevisionTerms months = RevisionFixtures.inEffectTerms();

        ListingTermsResponse answer = responses.toTerms(terms);

        assertEquals(new ListingRentResponse(9_500_000L, "VND", "MONTH"), answer.monthlyRent());
        assertEquals(new ListingDepositResponse("AMOUNT", null, 5_000_000L, "VND"), answer.deposit());
        assertEquals(12, answer.minimumTermMonths());
        assertEquals(3, answer.paymentPeriodMonths());
        assertEquals(List.of(new ListingFeeResponse("Điện", "AMOUNT", 3_500L, "VND", "PER_KWH"),
                new ListingFeeResponse("Internet", "INCLUDED", null, null, null)), answer.mandatoryFees());
        assertEquals(new ListingDepositResponse("MONTHS", 2, null, null), responses.toTerms(months).deposit());
    }

    @Test
    void toResponse_revisionDetail_everyField() {
        ListingRevisionView view = new ListingRevisionView("01JLISTING0000000000000001", 2, RevisionStatus.PENDING_REVIEW,
                RevisionFixtures.CREATED, RevisionFixtures.NOW, RevisionFixtures.completeContent(),
                RevisionFixtures.inEffectTerms(), RevisionFixtures.unitSnapshot(), RevisionFixtures.NOW, 7L, List.of());
        ListingRevisionView draft = new ListingRevisionView("01JLISTING0000000000000001", 3, RevisionStatus.DRAFT,
                RevisionFixtures.CREATED, RevisionFixtures.NOW, RevisionFixtures.completeContent(), null, null, null, 7L,
                List.of(new RevisionViolation(RevisionPart.TERMS, RevisionRule.REQUIRED),
                        new RevisionViolation(RevisionPart.TITLE, RevisionRule.PHONE_NUMBER)));

        ListingRevisionDetailResponse answer = responses.toRevisionDetail(view);
        ListingRevisionDetailResponse draftAnswer = responses.toRevisionDetail(draft);

        assertAll(
                () -> assertEquals("01JLISTING0000000000000001", answer.listingId()),
                () -> assertEquals(2, answer.revisionNo()),
                () -> assertEquals("PENDING_REVIEW", answer.status()),
                () -> assertEquals(RevisionFixtures.CREATED, answer.createdAt()),
                () -> assertEquals(RevisionFixtures.NOW, answer.updatedAt()),
                () -> assertEquals(RevisionFixtures.TITLE, answer.title()),
                () -> assertEquals(RevisionFixtures.DESCRIPTION, answer.description()),
                () -> assertEquals(new ListingFeaturesResponse("FULL", true, true, true, "PRIVATE", "SHARED", true, true,
                        true, true, true, "NOT_ALLOWED", true), answer.features()),
                () -> assertEquals("IN_EFFECT", answer.termsSnapshot().source()),
                () -> assertNull(answer.termsSnapshot().pendingStartDate()),
                () -> assertEquals(RevisionFixtures.NOW, answer.termsSnapshot().capturedAt()),
                () -> assertEquals(responses.toTerms(RevisionFixtures.inEffectTerms()), answer.termsSnapshot().terms()),
                () -> assertEquals(new ListingUnitSnapshotResponse("APARTMENT", "48", "Đà Nẵng", "20194",
                        "Phường Hải Châu", 2, 1, 32.5), answer.unitSnapshot()),
                () -> assertEquals(RevisionFixtures.NOW, answer.submittedAt()),
                () -> assertEquals(7L, answer.dataVersion()),
                () -> assertEquals(List.of(), answer.problems()),
                () -> assertNull(draftAnswer.termsSnapshot()),
                () -> assertNull(draftAnswer.unitSnapshot()),
                () -> assertNull(draftAnswer.submittedAt()),
                () -> assertEquals(List.of(new ListingRevisionProblemResponse("TERMS", "REQUIRED"),
                        new ListingRevisionProblemResponse("TITLE", "PHONE_NUMBER")), draftAnswer.problems()));
    }
}
