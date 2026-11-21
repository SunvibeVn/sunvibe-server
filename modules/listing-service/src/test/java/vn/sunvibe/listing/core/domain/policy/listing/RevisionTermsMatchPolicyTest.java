package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.sunvibe.listing.core.domain.model.listing.ChargeUnit;
import vn.sunvibe.listing.core.domain.model.listing.DepositBasis;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsMatch;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The terms check of a review (listing-publish BR-4, US-4 AC-5, E-5; E06-S4 spec E06S4-D7; RV-7 policy half): a snapshot
 * equals an agreement version by rent, deposit, minimum term, payment period and every fee in order, never by its source,
 * pending start date or capture time.
 */
class RevisionTermsMatchPolicyTest {

    private static final Instant CAPTURED = Instant.parse("2026-11-20T03:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-22T05:00:00Z");

    @Test
    void match_equalInEffect_inEffect() {
        assertEquals(TermsMatch.IN_EFFECT, RevisionTermsMatchPolicy.match(snapshot(), version(), other()));
    }

    @Test
    void match_equalBoth_inEffect() {
        assertEquals(TermsMatch.IN_EFFECT, RevisionTermsMatchPolicy.match(snapshot(), version(), version()));
    }

    @Test
    void match_equalPendingOnly_pendingStart() {
        assertEquals(TermsMatch.PENDING_START, RevisionTermsMatchPolicy.match(snapshot(), other(), version()));
        assertEquals(TermsMatch.PENDING_START, RevisionTermsMatchPolicy.match(snapshot(), null, version()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneFieldDiffers")
    void match_oneFieldDiffers_none(String field, RevisionTerms differing) {
        assertEquals(TermsMatch.NONE, RevisionTermsMatchPolicy.match(snapshot(), differing, differing), field);
    }

    @Test
    void match_sourceDateAndCaptureDiffer_stillInEffect() {
        RevisionTerms takenFromWaiting = new RevisionTerms(TermsSource.PENDING_START, LocalDate.parse("2026-12-01"),
                CAPTURED, 8_000_000L, new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, fees());
        RevisionTerms inEffectNow = new RevisionTerms(TermsSource.IN_EFFECT, null, LATER, 8_000_000L,
                new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, fees());

        assertEquals(TermsMatch.IN_EFFECT, RevisionTermsMatchPolicy.match(takenFromWaiting, inEffectNow, null));
    }

    @Test
    void match_noSnapshot_noSnapshot() {
        assertEquals(TermsMatch.NO_SNAPSHOT, RevisionTermsMatchPolicy.match(null, version(), version()));
        assertEquals(TermsMatch.NO_SNAPSHOT, RevisionTermsMatchPolicy.match(null, null, null));
    }

    @Test
    void match_noAgreementTerms_none() {
        assertEquals(TermsMatch.NONE, RevisionTermsMatchPolicy.match(snapshot(), null, null));
    }

    static Stream<Arguments> oneFieldDiffers() {
        RevisionTerms.Deposit deposit = new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null);
        List<RevisionTerms.Fee> reordered = new ArrayList<>(fees());
        java.util.Collections.reverse(reordered);
        List<RevisionTerms.Fee> oneMore = new ArrayList<>(fees());
        oneMore.add(new RevisionTerms.Fee("Rác", FeeBasis.AMOUNT, 20_000L, ChargeUnit.PER_MONTH));
        return Stream.of(
                Arguments.of("rent", terms(8_000_001L, deposit, 6, 1, fees())),
                Arguments.of("deposit basis", terms(8_000_000L,
                        new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 2L), 6, 1, fees())),
                Arguments.of("deposit months", terms(8_000_000L,
                        new RevisionTerms.Deposit(DepositBasis.MONTHS, 3, null), 6, 1, fees())),
                Arguments.of("minimum term", terms(8_000_000L, deposit, 7, 1, fees())),
                Arguments.of("payment period", terms(8_000_000L, deposit, 6, 2, fees())),
                Arguments.of("fee name", terms(8_000_000L, deposit, 6, 1, List.of(
                        new RevisionTerms.Fee("Điện sinh hoạt", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                        fees().get(1), fees().get(2)))),
                Arguments.of("fee basis", terms(8_000_000L, deposit, 6, 1, List.of(fees().get(0),
                        new RevisionTerms.Fee("Internet", FeeBasis.PROVIDER_TARIFF, null, null), fees().get(2)))),
                Arguments.of("fee amount", terms(8_000_000L, deposit, 6, 1, List.of(
                        new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_800L, ChargeUnit.PER_KWH),
                        fees().get(1), fees().get(2)))),
                Arguments.of("fee charge unit", terms(8_000_000L, deposit, 6, 1, List.of(
                        new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_MONTH),
                        fees().get(1), fees().get(2)))),
                Arguments.of("fees' order", terms(8_000_000L, deposit, 6, 1, reordered)),
                Arguments.of("one fee more", terms(8_000_000L, deposit, 6, 1, oneMore)));
    }

    /** The revision's snapshot of the version in effect. */
    private static RevisionTerms snapshot() {
        return terms(8_000_000L, new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, fees());
    }

    /** E05's version, as listing converts it, taken later than the snapshot. */
    private static RevisionTerms version() {
        return new RevisionTerms(TermsSource.IN_EFFECT, null, LATER, 8_000_000L,
                new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, fees());
    }

    /** A version that differs from the snapshot in its rent. */
    private static RevisionTerms other() {
        return terms(9_000_000L, new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, fees());
    }

    private static RevisionTerms terms(long rent, RevisionTerms.Deposit deposit, int minimumTerm, int paymentPeriod,
                                       List<RevisionTerms.Fee> fees) {
        return new RevisionTerms(TermsSource.IN_EFFECT, null, CAPTURED, rent, deposit, minimumTerm, paymentPeriod,
                fees);
    }

    private static List<RevisionTerms.Fee> fees() {
        return List.of(new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                new RevisionTerms.Fee("Internet", FeeBasis.INCLUDED, null, null),
                new RevisionTerms.Fee("Nước", FeeBasis.PROVIDER_TARIFF, null, null));
    }
}
