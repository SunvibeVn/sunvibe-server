package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingAnswerFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AdvertisingReviewFacts;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.policy.listing.ListingDisplayPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.testsupport.InMemoryListingRepository;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;
import vn.sunvibe.listing.core.testsupport.StubAdvertisingRightPort;
import vn.sunvibe.listing.core.testsupport.StubUnitInventoryPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CODE;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.CREATED;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;

/**
 * Where the display facts come from (listing-publish E06-S6 spec E06S6-D4, D8 lock order; PL-11, PL-18 halves): for a
 * command, E05's answer under its share lock first, then E05's terms, then property's availability, then the freshness
 * summary; for a read, E05's review answer without a lock. E05's groups are mapped by code and an unknown one fails, as
 * an availability property sends that listing does not know; a unit property does not know is not available.
 */
class DisplayConditionsAssemblerTest {

    private static final String LISTING = "01LISTING00000000000000001";
    private static final String AGREEMENT = "01AGREEMENT000000000000001";
    private static final String UNIT = "01UNIT00000000000000000001";
    private static final ListingRow ROW = new ListingRow(LISTING, CODE, UNIT, TransactionType.LONG_TERM_RENT, AGREEMENT,
            ListingState.UNPUBLISHED, "S1", CREATED, 7L);

    private final List<String> log = new ArrayList<>();
    private final InMemoryListingRepository listings = new InMemoryListingRepository() {
        @Override
        public FreshnessSummary findFreshnessSummary(String listingId) {
            log.add("findFreshnessSummary");
            return super.findFreshnessSummary(listingId);
        }
    };
    private final StubAdvertisingRightPort rights = new StubAdvertisingRightPort() {
        @Override
        public Optional<AdvertisingAnswerFacts> answerUnderShareLock(String unitId, String agreementId) {
            log.add("answerUnderShareLock");
            return super.answerUnderShareLock(unitId, agreementId);
        }

        @Override
        public Optional<AgreementTermsFacts> terms(String unitId, String agreementId) {
            log.add("terms");
            return super.terms(unitId, agreementId);
        }

        @Override
        public Optional<AdvertisingReviewFacts> review(String unitId, String agreementId) {
            log.add("review");
            return super.review(unitId, agreementId);
        }
    };
    private final StubUnitInventoryPort units = new StubUnitInventoryPort() {
        @Override
        public List<UnitAvailabilityFacts> availability(Collection<String> unitIds) {
            log.add("availability");
            return super.availability(unitIds);
        }
    };

    private DisplayConditionsAssembler assembler() {
        return new DisplayConditionsAssembler(rights, units, listings);
    }

    @Test
    void underShareLock_readsShareLockThenTermsThenUnitThenFreshness() {
        holdingWorld();

        DisplayConditions conditions = assembler().underShareLock(ROW, ListingState.UNPUBLISHED, NOW);

        assertEquals(List.of("answerUnderShareLock", "terms", "availability", "findFreshnessSummary"), log);
        assertAll(
                () -> assertEquals(ListingState.UNPUBLISHED, conditions.state()),
                () -> assertFalse(conditions.agreementEnded()),
                () -> assertTrue(conditions.advertisingAllowed()),
                () -> assertEquals(List.of(), conditions.agreementGroups()),
                () -> assertEquals(UnitAvailability.AVAILABLE, conditions.unitAvailabilityOrNull()),
                () -> assertFalse(conditions.availabilityDiffers()),
                () -> assertEquals(confirmedToday(), conditions.freshness()),
                () -> assertEquals(List.of(), ListingDisplayPolicy.putLiveReasons(conditions,
                        RevisionFixtures.inEffectTerms(), NOW)));
    }

    @Test
    void withoutLock_readsReviewAndUnit_noShareLock() {
        holdingWorld();
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, false, List.of("SUSPENDED", "OWNER"), null,
                true, true, snapshotTerms(), null, null));

        DisplayConditions conditions = assembler().withoutLock(ROW, ListingState.PUBLISHED, NOW);

        assertEquals(List.of("review", "availability", "findFreshnessSummary"), log);
        assertAll(
                () -> assertEquals(ListingState.PUBLISHED, conditions.state()),
                () -> assertFalse(conditions.advertisingAllowed()),
                () -> assertEquals(List.of(AgreementReasonGroup.SUSPENDED, AgreementReasonGroup.OWNER),
                        conditions.agreementGroups()),
                () -> assertEquals(List.of(DisplayReason.ADVERTISING_RIGHT), ListingDisplayPolicy.putLiveReasons(
                        conditions, RevisionFixtures.inEffectTerms(), NOW)));
    }

    @Test
    void underShareLock_givenSummary_readsNoSummary() {
        holdingWorld();
        FreshnessSummary next = new FreshnessSummary(FreshnessPolicy.today(NOW).minusDays(1), true);

        DisplayConditions conditions = assembler().underShareLock(ROW, ListingState.UNPUBLISHED, next, NOW);

        assertEquals(List.of("answerUnderShareLock", "terms", "availability"), log);
        assertEquals(next, conditions.freshness());
    }

    @Test
    void underShareLock_noE05Answer_noAgreementGroup() {
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));

        DisplayConditions conditions = assembler().underShareLock(ROW, ListingState.UNPUBLISHED, NOW);

        assertAll(
                () -> assertFalse(conditions.agreementEnded()),
                () -> assertFalse(conditions.advertisingAllowed()),
                () -> assertEquals(List.of(AgreementReasonGroup.NO_AGREEMENT), conditions.agreementGroups()),
                () -> assertNull(conditions.termsInEffectOrNull()),
                () -> assertTrue(ListingDisplayPolicy.putLiveReasons(conditions, RevisionFixtures.inEffectTerms(), NOW)
                        .contains(DisplayReason.ADVERTISING_RIGHT)));
    }

    @Test
    void underShareLock_unknownE05Group_illegalState() {
        holdingWorld();
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, false, List.of("SOMETHING_NEW")));

        assertThrows(IllegalStateException.class,
                () -> assembler().underShareLock(ROW, ListingState.UNPUBLISHED, NOW));
    }

    @Test
    void underShareLock_unitWithoutAvailability_illegalState() {
        holdingWorld();
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", null, false));

        assertThrows(IllegalStateException.class,
                () -> assembler().underShareLock(ROW, ListingState.UNPUBLISHED, NOW));
    }

    @Test
    void underShareLock_unknownAvailability_illegalState() {
        holdingWorld();
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "RESERVED", false));

        assertThrows(IllegalStateException.class,
                () -> assembler().underShareLock(ROW, ListingState.UNPUBLISHED, NOW));
    }

    @Test
    void underShareLock_unitUnknownToProperty_unitNotAvailable() {
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, true, List.of()));
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, snapshotTerms(), null, null));
        listings.putFreshness(LISTING, confirmedToday(), null);

        DisplayConditions conditions = assembler().underShareLock(ROW, ListingState.UNPUBLISHED, NOW);

        assertNull(conditions.unitAvailabilityOrNull());
        assertEquals(List.of(DisplayReason.UNIT_NOT_AVAILABLE), ListingDisplayPolicy.putLiveReasons(conditions,
                RevisionFixtures.inEffectTerms(), NOW));
    }

    @Test
    void withoutLock_unknownE05Group_illegalState() {
        holdingWorld();
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, false, List.of("SOMETHING_NEW"), null, true,
                true, snapshotTerms(), null, null));

        assertThrows(IllegalStateException.class, () -> assembler().withoutLock(ROW, ListingState.UNPUBLISHED, NOW));
    }

    @Test
    void withoutLock_unknownAvailability_illegalState() {
        holdingWorld();
        rights.review(new AdvertisingReviewFacts(UNIT, AGREEMENT, false, true, List.of(), null, true, true,
                snapshotTerms(), null, null));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "RESERVED", false));

        assertThrows(IllegalStateException.class, () -> assembler().withoutLock(ROW, ListingState.UNPUBLISHED, NOW));
    }

    /** E05 allows with the snapshot's terms in effect, the unit available, a valid confirmation today. */
    private void holdingWorld() {
        rights.answer(new AdvertisingAnswerFacts(UNIT, AGREEMENT, false, true, List.of()));
        rights.terms(UNIT, AGREEMENT, new AgreementTermsFacts(false, snapshotTerms(), null, null));
        units.availability(new UnitAvailabilityFacts(UNIT, "P101", "AVAILABLE", false));
        listings.putFreshness(LISTING, confirmedToday(), null);
    }

    private static FreshnessSummary confirmedToday() {
        return new FreshnessSummary(FreshnessPolicy.today(NOW), true);
    }

    /** E05's terms equal to RevisionFixtures.inEffectTerms(), as E05 sends them. */
    static AgreementTermsFacts.Terms snapshotTerms() {
        return new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, 6, 1, List.of(
                new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
                new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null),
                new AgreementTermsFacts.Fee("Nước", "PROVIDER_TARIFF", null, null)));
    }
}
