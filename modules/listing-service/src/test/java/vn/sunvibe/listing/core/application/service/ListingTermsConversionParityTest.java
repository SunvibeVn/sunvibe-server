package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.command.RevisionDecisionCommand;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementStandingFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewOutcome;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TermsMatch;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.policy.listing.ListingDisplayPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.testsupport.InMemoryListingAccessLogRepository;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RecordingEventOutboxPort;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;

/**
 * One conversion of E05's terms in effect (listing-publish E06-S6 spec E06S6-D20; PL-18): the approval's terms check,
 * the review read's terms match and the put live's condition (c) read the same E05 answer as the same value, so they
 * agree on equal terms and all refuse terms that differ by a fee or the deposit basis.
 */
class ListingTermsConversionParityTest {

    private static final String LISTING = "01LISTING00000000000000001";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";
    private static final String SALE = "01SALE00000000000000000001";
    private static final String REVIEWER = "01REVIEWER0000000000000001";
    private static final ListingViewer REVIEWER_VIEWER = new ListingViewer(REVIEWER, Set.of("ROLE_LISTING_REVIEWER"));
    private static final ListingRow ROW = new ListingRow(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT,
            ListingState.UNPUBLISHED, SALE, CREATED, 7L);

    private InMemoryListingRepository listings;
    private final StubAdvertisingRightPort rights = new StubAdvertisingRightPort();
    private final StubUnitInventoryPort units = new StubUnitInventoryPort();
    private final AtomicInteger nextId = new AtomicInteger();

    @Test
    void inEffectSnapshot_equalTerms_approvalReviewAndAssemblerAgree() {
        world(DisplayConditionsAssemblerTest.snapshotTerms(), null);

        assertAll(
                () -> assertEquals(RevisionStatus.APPROVED, approval().status()),
                () -> assertEquals(TermsMatch.IN_EFFECT, reviewMatch()),
                () -> assertFalse(conditionReasons().contains(DisplayReason.TERMS_NOT_IN_EFFECT)));
    }

    @Test
    void inEffectSnapshot_feeOrDepositBasisChanged_refusedEverywhere() {
        for (AgreementTermsFacts.Terms changed : List.of(feeChanged(), depositBasisChanged())) {
            world(changed, null);

            assertAll(
                    () -> assertEquals(ListingRefusedException.Reason.REVISION_TERMS_MISMATCH,
                            assertThrows(ListingRefusedException.class, this::approval).reason()),
                    () -> assertEquals(TermsMatch.NONE, reviewMatch()),
                    () -> assertTrue(conditionReasons().contains(DisplayReason.TERMS_NOT_IN_EFFECT)));

            // the same snapshot equal to the version waiting to start: the review reads it there, condition (c) still not
            world(changed, DisplayConditionsAssemblerTest.snapshotTerms());

            assertAll(
                    () -> assertEquals(TermsMatch.PENDING_START, reviewMatch()),
                    () -> assertTrue(conditionReasons().contains(DisplayReason.TERMS_NOT_IN_EFFECT)));
        }
    }

    private vn.sunvibe.listing.core.application.query.RevisionCommandResult approval() {
        return new ListingReviewCommandService(listings, new InMemoryListingAccessLogRepository(), rights, units,
                () -> "01ID" + String.format("%022d", nextId.incrementAndGet()), () -> 7L,
                new DisplayConditionsAssembler(rights, units, listings), new RecordingEventOutboxPort())
                .decide(new RevisionDecisionCommand(LISTING, 1, 7L, ReviewOutcome.APPROVED, ReviewCheck.VERSION_1,
                        List.of(), null, null), REVIEWER_VIEWER);
    }

    private TermsMatch reviewMatch() {
        return new ListingReviewQueryService(listings, rights, units).review(LISTING, 1, REVIEWER_VIEWER).termsMatch();
    }

    private List<DisplayReason> conditionReasons() {
        DisplayConditions conditions = new DisplayConditionsAssembler(rights, units, listings)
                .underShareLock(ROW, ListingState.UNPUBLISHED, NOW);
        return ListingDisplayPolicy.putLiveReasons(conditions, RevisionFixtures.inEffectTerms(), NOW);
    }

    /** A listing at version 7 with revision 1 Pending review (the in-effect snapshot); E05 answers the terms given. */
    private void world(AgreementTermsFacts.Terms inEffect, AgreementTermsFacts.Terms pendingOrNull) {
        listings = new InMemoryListingRepository();
        listings.put(Listing.load(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT,
                ListingState.UNPUBLISHED, SALE, CREATED, CREATED, 7L, List.of(RevisionFixtures.pending(1))));
        listings.putAuthors("R1", Set.of(SALE));
        LocalDate pendingStart = pendingOrNull == null ? null : LocalDate.of(2026, 12, 1);
        rights.standing(new AgreementStandingFacts(AGREEMENT, UNIT, "LONG_TERM_RENT", false, SALE));
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, inEffect, pendingStart, pendingOrNull));
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, true, List.of()));
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, true, List.of(), null, true, true, inEffect,
                pendingStart, pendingOrNull));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));
    }

    private static AgreementTermsFacts.Terms feeChanged() {
        return new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, 6, 1, List.of(
                new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_600L, "PER_KWH"),
                new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null),
                new AgreementTermsFacts.Fee("Nước", "PROVIDER_TARIFF", null, null)));
    }

    private static AgreementTermsFacts.Terms depositBasisChanged() {
        return new AgreementTermsFacts.Terms(8_000_000L, "AMOUNT", null, 16_000_000L, 6, 1, List.of(
                new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
                new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null),
                new AgreementTermsFacts.Fee("Nước", "PROVIDER_TARIFF", null, null)));
    }
}
