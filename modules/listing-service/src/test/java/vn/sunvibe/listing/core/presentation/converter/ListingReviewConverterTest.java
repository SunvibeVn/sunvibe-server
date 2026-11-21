package vn.sunvibe.listing.core.presentation.converter;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.command.RevisionCopyCommand;
import vn.sunvibe.listing.core.application.command.RevisionDecisionCommand;
import vn.sunvibe.listing.core.application.command.UnitLocationOpenCommand;
import vn.sunvibe.listing.core.application.query.ReviewQueueView;
import vn.sunvibe.listing.core.application.query.RevisionDecisionView;
import vn.sunvibe.listing.core.application.query.RevisionReviewView;
import vn.sunvibe.listing.core.application.query.UnitLocationResult;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.ChangeReason;
import vn.sunvibe.listing.core.domain.model.listing.RejectionGroup;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsMatch;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionCommandRequest;
import vn.sunvibe.listing.core.presentation.dto.request.ListingRevisionDecisionRequest;
import vn.sunvibe.listing.core.presentation.dto.response.ListingPendingTermsResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewAdvertisingResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewFreshnessResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewQueueItemResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewQueueResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingReviewUnitResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionDecisionResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingRevisionReviewResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingUnitLocationResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingUnitSnapshotResponse;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The review's request and response converters (listing-publish E06-S4 spec section 4, E06S4-D6, D10, D11, D15): the
 * request's codes as domain values, the views' values as codes, the existing schemas reused for the unit snapshot and
 * the terms, every nullable part null where the view has none; the location prints nothing.
 */
class ListingReviewConverterTest {

    private static final String LISTING = "01LISTING00000000000000001";
    private static final Instant SUBMITTED = Instant.parse("2026-11-20T01:00:00.000001Z");
    private static final LocalDate CONFIRMED_ON = LocalDate.parse("2026-11-01");

    private final ListingRequestConverter requests = new ListingRequestConverter();
    private final ListingResponseConverter responses = new ListingResponseConverter();

    @Test
    void toDecisionCommand_codesToEnums() {
        RevisionDecisionCommand approved = requests.toDecisionCommand(LISTING, 2, new ListingRevisionDecisionRequest(5L,
                "APPROVED", List.of("NO_FRAUD_SIGNS", "CONTENT_MATCHES_UNIT", "TEXT_FOLLOWS_RULES",
                "TERMS_MATCH_AGREEMENT", "AGREEMENT_END_AND_RIGHT"), null, null, null));
        RevisionDecisionCommand returned = requests.toDecisionCommand(LISTING, null, new ListingRevisionDecisionRequest(
                5L, "NEEDS_CHANGES", null, List.of("TERMS_MISMATCH", "OTHER"), null, " Ghi chú "));
        RevisionDecisionCommand rejected = requests.toDecisionCommand(LISTING, 2, new ListingRevisionDecisionRequest(5L,
                "REJECTED", null, null, "SERIOUS_VIOLATION", "Ghi chú"));

        assertEquals(new RevisionDecisionCommand(LISTING, 2, 5L, ReviewOutcome.APPROVED, List.of(
                ReviewCheck.NO_FRAUD_SIGNS, ReviewCheck.CONTENT_MATCHES_UNIT, ReviewCheck.TEXT_FOLLOWS_RULES,
                ReviewCheck.TERMS_MATCH_AGREEMENT, ReviewCheck.AGREEMENT_END_AND_RIGHT), List.of(), null, null), approved);
        assertEquals(new RevisionDecisionCommand(LISTING, null, 5L, ReviewOutcome.NEEDS_CHANGES, List.of(),
                List.of(ChangeReason.TERMS_MISMATCH, ChangeReason.OTHER), null, " Ghi chú "), returned);
        assertEquals(new RevisionDecisionCommand(LISTING, 2, 5L, ReviewOutcome.REJECTED, List.of(), List.of(),
                RejectionGroup.SERIOUS_VIOLATION, "Ghi chú"), rejected);
        assertEquals(new UnitLocationOpenCommand(LISTING, 2), requests.toLocationCommand(LISTING, 2));
        assertEquals(new RevisionCopyCommand(LISTING, 2, 5L),
                requests.toCopyCommand(LISTING, 2, new ListingRevisionCommandRequest(5L)));
    }

    @Test
    void toReviewQueue_unitSnapshotAndReasons() {
        ReviewQueueView view = new ReviewQueueView(List.of(
                new ReviewQueueView.Item(LISTING, "TD-7K3M-Q9TX", 2, SUBMITTED, "Phòng trọ", RevisionFixtures.unitSnapshot(),
                        false, List.of(ReviewIneligibility.AUTHOR, ReviewIneligibility.ASSIGNED_SALE)),
                new ReviewQueueView.Item("01LISTING00000000000000002", "TD-AAAA-BBBB", 1, SUBMITTED.plusSeconds(1),
                        "Căn hộ", RevisionFixtures.unitSnapshot(), true, List.of())), true);

        ListingReviewQueueResponse response = responses.toReviewQueue(view);

        assertEquals(new ListingReviewQueueResponse(List.of(
                new ListingReviewQueueItemResponse(LISTING, "TD-7K3M-Q9TX", 2, SUBMITTED, "Phòng trọ", unitSnapshot(),
                        false, List.of("AUTHOR", "ASSIGNED_SALE")),
                new ListingReviewQueueItemResponse("01LISTING00000000000000002", "TD-AAAA-BBBB", 1,
                        SUBMITTED.plusSeconds(1), "Căn hộ", unitSnapshot(), true, List.of())), true), response);
        assertFalse(response.items().getFirst().toString().contains("Phòng trọ"));
    }

    @Test
    void toRevisionReview_nullableAdvertisingAndTerms() {
        RevisionTerms inEffect = RevisionFixtures.inEffectTerms();
        RevisionTerms pending = new RevisionTerms(TermsSource.PENDING_START, LocalDate.parse("2026-12-01"),
                inEffect.capturedAt(), 9_000_000L, inEffect.deposit(), 6, 1, inEffect.fees());
        RevisionReviewView full = review(new RevisionReviewView.Advertising(false, false,
                List.of(AgreementReasonGroup.SUSPENDED, AgreementReasonGroup.OWNER), LocalDate.parse("2027-11-30"), true,
                false, inEffect, LocalDate.parse("2026-12-01"), pending),
                new RevisionReviewView.Unit("P101", UnitAvailability.MAINTENANCE, true),
                new RevisionReviewView.Freshness(CONFIRMED_ON, LocalDate.parse("2026-12-01"), false, true));

        ListingRevisionReviewResponse response = responses.toRevisionReview(full);

        assertAll(
                () -> assertEquals(LISTING, response.listingId()),
                () -> assertEquals("TD-7K3M-Q9TX", response.code()),
                () -> assertEquals(3, response.revisionNo()),
                () -> assertEquals("PENDING_REVIEW", response.status()),
                () -> assertEquals(11L, response.dataVersion()),
                () -> assertFalse(response.viewerMayReview()),
                () -> assertEquals(List.of("NOT_A_REVIEWER", "LISTING_ARCHIVED"), response.notEligibleReasons()),
                () -> assertEquals(new ListingReviewAdvertisingResponse(false, false, List.of("SUSPENDED", "OWNER"),
                        LocalDate.parse("2027-11-30"), true, false, responses.toTerms(inEffect),
                        new ListingPendingTermsResponse(LocalDate.parse("2026-12-01"), responses.toTerms(pending))),
                        response.advertising()),
                () -> assertEquals("PENDING_START", response.termsMatch()),
                () -> assertEquals(new ListingReviewUnitResponse("P101", "MAINTENANCE", true), response.unit()),
                () -> assertEquals(new ListingReviewFreshnessResponse(CONFIRMED_ON, LocalDate.parse("2026-12-01"), false,
                        true), response.freshness()),
                () -> assertEquals(List.of("CONTENT_MATCHES_UNIT", "TEXT_FOLLOWS_RULES", "TERMS_MATCH_AGREEMENT",
                        "NO_FRAUD_SIGNS", "AGREEMENT_END_AND_RIGHT"), response.checklist()));

        ListingRevisionReviewResponse noAnswer = responses.toRevisionReview(review(null,
                new RevisionReviewView.Unit(null, null, false), new RevisionReviewView.Freshness(null, null, false, null)));
        assertNull(noAnswer.advertising());
        assertEquals(new ListingReviewUnitResponse(null, null, false), noAnswer.unit());
        assertEquals(new ListingReviewFreshnessResponse(null, null, false, null), noAnswer.freshness());

        ListingRevisionReviewResponse noTerms = responses.toRevisionReview(review(new RevisionReviewView.Advertising(
                true, false, List.of(), null, true, true, null, null, null),
                new RevisionReviewView.Unit("P101", UnitAvailability.AVAILABLE, false),
                new RevisionReviewView.Freshness(null, null, false, null)));
        assertNull(noTerms.advertising().termsInEffect());
        assertNull(noTerms.advertising().pending());
        assertNull(noTerms.advertising().endDate());
    }

    @Test
    void toRevisionDecision_valuesAsCodes() {
        Instant decidedAt = Instant.parse("2026-11-21T04:30:00.000001Z");

        assertEquals(new ListingRevisionDecisionResponse("NEEDS_CHANGES", decidedAt, null,
                List.of("MISSING_INFORMATION", "TERMS_MISMATCH"), null, "Thiếu ảnh."),
                responses.toRevisionDecision(new RevisionDecisionView(ReviewOutcome.NEEDS_CHANGES, decidedAt, null,
                        List.of(ChangeReason.MISSING_INFORMATION, ChangeReason.TERMS_MISMATCH), null, "Thiếu ảnh.")));
        assertEquals(new ListingRevisionDecisionResponse("APPROVED", decidedAt, 1, List.of(), null, null),
                responses.toRevisionDecision(new RevisionDecisionView(ReviewOutcome.APPROVED, decidedAt, 1, List.of(),
                        null, null)));
        assertEquals(new ListingRevisionDecisionResponse("REJECTED", decidedAt, null, List.of(), "WRONG_UNIT", "Sai."),
                responses.toRevisionDecision(new RevisionDecisionView(ReviewOutcome.REJECTED, decidedAt, null,
                        List.of(), RejectionGroup.WRONG_UNIT, "Sai.")));
    }

    @Test
    void toUnitLocation_toStringHidden() {
        ListingUnitLocationResponse response = responses.toUnitLocation(new UnitLocationResult("P101",
                "P101 · Tầng 1", "12 Trần Phú", "Hải Châu"));

        assertEquals(new ListingUnitLocationResponse("P101", "P101 · Tầng 1", "12 Trần Phú", "Hải Châu"), response);
        assertEquals("ListingUnitLocationResponse[hidden]", response.toString());
        assertEquals(new ListingUnitLocationResponse(null, null, null, null),
                responses.toUnitLocation(new UnitLocationResult(null, null, null, null)));
    }

    private static RevisionReviewView review(RevisionReviewView.Advertising advertising, RevisionReviewView.Unit unit,
                                             RevisionReviewView.Freshness freshness) {
        return new RevisionReviewView(LISTING, "TD-7K3M-Q9TX", 3, RevisionStatus.PENDING_REVIEW, 11L, false,
                List.of(ReviewIneligibility.NOT_A_REVIEWER, ReviewIneligibility.LISTING_ARCHIVED), advertising,
                advertising == null ? TermsMatch.NONE : TermsMatch.PENDING_START, unit, freshness, ReviewCheck.VERSION_1);
    }

    private static ListingUnitSnapshotResponse unitSnapshot() {
        return new ListingUnitSnapshotResponse("APARTMENT", "48", "Đà Nẵng", "20194", "Phường Hải Châu", 2, 1, 32.5);
    }
}
