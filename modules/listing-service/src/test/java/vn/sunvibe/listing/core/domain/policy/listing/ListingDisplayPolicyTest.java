package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup;
import vn.sunvibe.listing.core.domain.model.listing.ChargeUnit;
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.DisplayReason;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;
import vn.sunvibe.listing.core.testsupport.RevisionFixtures;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.listing.core.testsupport.RevisionFixtures.NOW;

/**
 * The display conditions of listing BR-4 and listing-publish BR-11 (E06-S6 spec E06S6-D3; PL-2 rule half): five reasons
 * in a fixed order, each at most once, an empty answer when every condition holds; the live revision also needs a
 * Published listing.
 */
class ListingDisplayPolicyTest {

    private static final LocalDate TODAY = FreshnessPolicy.today(NOW);

    @Test
    void putLiveReasons_everyConditionHolds_empty() {
        assertEquals(List.of(), ListingDisplayPolicy.putLiveReasons(holding(ListingState.UNPUBLISHED),
                RevisionFixtures.inEffectTerms(), NOW));
        assertEquals(List.of(), ListingDisplayPolicy.putLiveReasons(holding(ListingState.PUBLISHED),
                RevisionFixtures.inEffectTerms(), NOW));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failingConditions")
    void putLiveReasons_eachFailingCondition_itsReasonAlone(String name, DisplayConditions conditions,
                                                            RevisionTerms snapshotOrNull, DisplayReason expected) {
        assertEquals(List.of(expected), ListingDisplayPolicy.putLiveReasons(conditions, snapshotOrNull, NOW));
    }

    static Stream<Arguments> failingConditions() {
        RevisionTerms terms = RevisionFixtures.inEffectTerms();
        return Stream.of(
                Arguments.of("paused", holding(ListingState.PAUSED), terms, DisplayReason.LISTING_STATE),
                Arguments.of("urgently unpublished", holding(ListingState.URGENTLY_UNPUBLISHED), terms,
                        DisplayReason.LISTING_STATE),
                Arguments.of("agreement ended", conditions(ListingState.UNPUBLISHED, true, true, List.of(), terms,
                        UnitAvailability.AVAILABLE, false, confirmedToday()), terms, DisplayReason.ADVERTISING_RIGHT),
                Arguments.of("not allowed", conditions(ListingState.UNPUBLISHED, false, false,
                        List.of(AgreementReasonGroup.SUSPENDED), terms, UnitAvailability.AVAILABLE, false,
                        confirmedToday()), terms, DisplayReason.ADVERTISING_RIGHT),
                Arguments.of("no terms in effect", conditions(ListingState.UNPUBLISHED, false, true, List.of(), null,
                        UnitAvailability.AVAILABLE, false, confirmedToday()), terms, DisplayReason.TERMS_NOT_IN_EFFECT),
                Arguments.of("one fee changed", holding(ListingState.UNPUBLISHED), oneFeeChanged(),
                        DisplayReason.TERMS_NOT_IN_EFFECT),
                Arguments.of("no snapshot", holding(ListingState.UNPUBLISHED), null,
                        DisplayReason.TERMS_NOT_IN_EFFECT),
                Arguments.of("unit unknown to property", conditions(ListingState.UNPUBLISHED, false, true, List.of(),
                        terms, null, false, confirmedToday()), terms, DisplayReason.UNIT_NOT_AVAILABLE),
                Arguments.of("occupied", unit(UnitAvailability.OCCUPIED, false), terms,
                        DisplayReason.UNIT_NOT_AVAILABLE),
                Arguments.of("maintenance", unit(UnitAvailability.MAINTENANCE, false), terms,
                        DisplayReason.UNIT_NOT_AVAILABLE),
                Arguments.of("blocked", unit(UnitAvailability.BLOCKED, false), terms, DisplayReason.UNIT_NOT_AVAILABLE),
                Arguments.of("available but differs", unit(UnitAvailability.AVAILABLE, true), terms,
                        DisplayReason.UNIT_NOT_AVAILABLE),
                Arguments.of("never confirmed", freshness(FreshnessSummary.none()), terms, DisplayReason.FRESHNESS),
                Arguments.of("latest invalid", freshness(new FreshnessSummary(TODAY, false)), terms,
                        DisplayReason.FRESHNESS));
    }

    @Test
    void putLiveReasons_allFail_fixedOrderEachOnce() {
        DisplayConditions conditions = conditions(ListingState.PAUSED, true, false,
                List.of(AgreementReasonGroup.SUSPENDED, AgreementReasonGroup.ENDED), null, UnitAvailability.OCCUPIED,
                true, FreshnessSummary.none());

        assertEquals(List.of(DisplayReason.LISTING_STATE, DisplayReason.ADVERTISING_RIGHT,
                        DisplayReason.TERMS_NOT_IN_EFFECT, DisplayReason.UNIT_NOT_AVAILABLE, DisplayReason.FRESHNESS),
                ListingDisplayPolicy.putLiveReasons(conditions, RevisionFixtures.inEffectTerms(), NOW));
    }

    @Test
    void putLiveReasons_noAnswer_advertisingRightNoAgreement() {
        DisplayConditions conditions = conditions(ListingState.UNPUBLISHED, false, false,
                List.of(AgreementReasonGroup.NO_AGREEMENT), RevisionFixtures.inEffectTerms(),
                UnitAvailability.AVAILABLE, false, confirmedToday());

        assertEquals(List.of(DisplayReason.ADVERTISING_RIGHT),
                ListingDisplayPolicy.putLiveReasons(conditions, RevisionFixtures.inEffectTerms(), NOW));
        assertEquals(List.of(AgreementReasonGroup.NO_AGREEMENT), conditions.agreementGroups());
    }

    @Test
    void putLiveReasons_pendingStartSnapshot_termsNotInEffect() {
        RevisionTerms inEffect = RevisionFixtures.inEffectTerms();
        RevisionTerms pendingSnapshot = new RevisionTerms(TermsSource.PENDING_START, TODAY.plusDays(10), NOW,
                9_000_000L, inEffect.deposit(), inEffect.minimumTermMonths(), inEffect.paymentPeriodMonths(),
                inEffect.fees());

        assertEquals(List.of(DisplayReason.TERMS_NOT_IN_EFFECT),
                ListingDisplayPolicy.putLiveReasons(holding(ListingState.UNPUBLISHED), pendingSnapshot, NOW));
    }

    @Test
    void putLiveReasons_freshnessDeadline_refusedFromItsInstant() {
        LocalDate confirmedOn = LocalDate.of(2026, 11, 5);
        Instant deadline = confirmedOn.plusDays(15).atStartOfDay(FreshnessPolicy.ZONE).toInstant();
        DisplayConditions conditions = freshness(new FreshnessSummary(confirmedOn, true));

        assertEquals(List.of(DisplayReason.FRESHNESS),
                ListingDisplayPolicy.putLiveReasons(conditions, RevisionFixtures.inEffectTerms(), deadline));
        assertEquals(List.of(), ListingDisplayPolicy.putLiveReasons(conditions, RevisionFixtures.inEffectTerms(),
                deadline.minus(1, ChronoUnit.MICROS)));
    }

    @Test
    void liveReasons_unpublishedOrPaused_listingState() {
        for (ListingState state : List.of(ListingState.UNPUBLISHED, ListingState.PAUSED,
                ListingState.URGENTLY_UNPUBLISHED)) {
            assertEquals(List.of(DisplayReason.LISTING_STATE), ListingDisplayPolicy.liveReasons(holding(state),
                    RevisionFixtures.inEffectTerms(), NOW), state.name());
        }
        assertEquals(List.of(), ListingDisplayPolicy.liveReasons(holding(ListingState.PUBLISHED),
                RevisionFixtures.inEffectTerms(), NOW));
    }

    private static DisplayConditions holding(ListingState state) {
        return conditions(state, false, true, List.of(), RevisionFixtures.inEffectTerms(), UnitAvailability.AVAILABLE,
                false, confirmedToday());
    }

    private static DisplayConditions unit(UnitAvailability availability, boolean differs) {
        return conditions(ListingState.UNPUBLISHED, false, true, List.of(), RevisionFixtures.inEffectTerms(),
                availability, differs, confirmedToday());
    }

    private static DisplayConditions freshness(FreshnessSummary summary) {
        return conditions(ListingState.UNPUBLISHED, false, true, List.of(), RevisionFixtures.inEffectTerms(),
                UnitAvailability.AVAILABLE, false, summary);
    }

    private static DisplayConditions conditions(ListingState state, boolean ended, boolean allowed,
                                                List<AgreementReasonGroup> groups, RevisionTerms termsInEffectOrNull,
                                                UnitAvailability availabilityOrNull, boolean differs,
                                                FreshnessSummary freshness) {
        return new DisplayConditions(state, ended, allowed, groups, termsInEffectOrNull, availabilityOrNull, differs,
                freshness);
    }

    private static FreshnessSummary confirmedToday() {
        return new FreshnessSummary(TODAY, true);
    }

    private static RevisionTerms oneFeeChanged() {
        RevisionTerms terms = RevisionFixtures.inEffectTerms();
        return new RevisionTerms(terms.source(), terms.pendingStartDateOrNull(), terms.capturedAt(),
                terms.monthlyRent(), terms.deposit(), terms.minimumTermMonths(), terms.paymentPeriodMonths(),
                List.of(new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_600L, ChargeUnit.PER_KWH),
                        terms.fees().get(1), terms.fees().get(2)));
    }
}
