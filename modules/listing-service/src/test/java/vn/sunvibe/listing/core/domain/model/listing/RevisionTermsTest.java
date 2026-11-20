package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A terms snapshot copied from E05 (listing-publish BR-4; E06-S2 spec E06S2-D6): within E05's own bounds, or not built at
 * all.
 */
class RevisionTermsTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:15:00.123456Z");
    private static final LocalDate START = LocalDate.parse("2026-11-30");
    private static final RevisionTerms.Deposit TWO_MONTHS = new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null);
    private static final RevisionTerms.Fee POWER =
            new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH);
    private static final RevisionTerms.Fee NET = new RevisionTerms.Fee("Internet", FeeBasis.INCLUDED, null, null);
    private static final RevisionTerms.Fee WATER =
            new RevisionTerms.Fee("Nước", FeeBasis.PROVIDER_TARIFF, null, null);

    @Test
    void constructor_inEffectWithinBounds_kept() {
        RevisionTerms terms = new RevisionTerms(TermsSource.IN_EFFECT, null, NOW, 8_000_000L, TWO_MONTHS, 6, 1,
                List.of(POWER, NET, WATER));

        assertEquals(TermsSource.IN_EFFECT, terms.source());
        assertNull(terms.pendingStartDateOrNull());
        assertEquals(NOW, terms.capturedAt());
        assertEquals(8_000_000L, terms.monthlyRent());
        assertEquals(TWO_MONTHS, terms.deposit());
        assertEquals(6, terms.minimumTermMonths());
        assertEquals(1, terms.paymentPeriodMonths());
        assertEquals(List.of(POWER, NET, WATER), terms.fees());
        List<RevisionTerms.Fee> twenty = Collections.nCopies(20, NET);
        assertAll(
                () -> assertDoesNotThrow(() -> terms(1L, TWO_MONTHS, 1, 1, List.of())),
                () -> assertDoesNotThrow(() -> terms(999_999_999_999_999L, TWO_MONTHS, 120, 12, twenty)),
                () -> assertDoesNotThrow(() -> new RevisionTerms.Deposit(DepositBasis.MONTHS, 0, null)),
                () -> assertDoesNotThrow(() -> new RevisionTerms.Deposit(DepositBasis.MONTHS, 12, null)),
                () -> assertDoesNotThrow(() -> new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 0L)),
                () -> assertDoesNotThrow(() -> new RevisionTerms.Deposit(DepositBasis.AMOUNT, null,
                        999_999_999_999_999L)),
                () -> assertDoesNotThrow(() -> new RevisionTerms.Fee("a".repeat(100), FeeBasis.AMOUNT, 1L,
                        ChargeUnit.ONE_OFF)));
    }

    @Test
    void constructor_pendingDateOnlyForPendingStart_illegalArgument() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new RevisionTerms(TermsSource.PENDING_START,
                        null, NOW, 8_000_000L, TWO_MONTHS, 6, 1, List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new RevisionTerms(TermsSource.IN_EFFECT,
                        START, NOW, 8_000_000L, TWO_MONTHS, 6, 1, List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new RevisionTerms(null, null, NOW,
                        8_000_000L, TWO_MONTHS, 6, 1, List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new RevisionTerms(TermsSource.IN_EFFECT,
                        null, null, 8_000_000L, TWO_MONTHS, 6, 1, List.of())));
        RevisionTerms pending = new RevisionTerms(TermsSource.PENDING_START, START, NOW, 8_000_000L, TWO_MONTHS, 6, 1,
                List.of());
        assertEquals(START, pending.pendingStartDateOrNull());
    }

    @Test
    void constructor_rentOutOfBounds_illegalArgument() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> terms(0L, TWO_MONTHS, 6, 1, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> terms(1_000_000_000_000_000L, TWO_MONTHS, 6, 1, List.of())));
    }

    @Test
    void constructor_depositShape_illegalArgument() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, 100L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Deposit(DepositBasis.MONTHS, 13, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Deposit(DepositBasis.MONTHS, -1, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Deposit(DepositBasis.MONTHS, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, -1L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Deposit(DepositBasis.AMOUNT, 1, 5_000_000L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 1_000_000_000_000_000L)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RevisionTerms.Deposit(null, 2, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> terms(8_000_000L, null, 6, 1, List.of())));
    }

    @Test
    void constructor_termAndPeriodOutOfBounds_illegalArgument() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> terms(8_000_000L, TWO_MONTHS, 0, 1, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> terms(8_000_000L, TWO_MONTHS, 121, 1, List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> terms(8_000_000L, TWO_MONTHS, 6, 0, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> terms(8_000_000L, TWO_MONTHS, 6, 13, List.of())));
    }

    @Test
    void constructor_feeRules_illegalArgument() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> terms(8_000_000L, TWO_MONTHS, 6, 1, Collections.nCopies(21, NET))),
                () -> assertThrows(IllegalArgumentException.class, () -> terms(8_000_000L, TWO_MONTHS, 6, 1, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("", FeeBasis.INCLUDED, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee(null, FeeBasis.INCLUDED, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("a".repeat(101), FeeBasis.INCLUDED, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("Fee", FeeBasis.AMOUNT, 0L, ChargeUnit.PER_MONTH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("Fee", FeeBasis.AMOUNT, 1_000L, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("Fee", FeeBasis.AMOUNT, null, ChargeUnit.PER_MONTH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("Fee", FeeBasis.AMOUNT, 1_000_000_000_000_000L, ChargeUnit.ONE_OFF)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("Fee", FeeBasis.INCLUDED, 1_000L, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("Fee", FeeBasis.INCLUDED, null, ChargeUnit.PER_MONTH)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("Fee", FeeBasis.PROVIDER_TARIFF, 1_000L, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RevisionTerms.Fee("Fee", null, null, null)));
    }

    @Test
    void fees_copiedUnmodifiable() {
        List<RevisionTerms.Fee> source = new ArrayList<>(List.of(POWER, NET));
        RevisionTerms terms = terms(8_000_000L, TWO_MONTHS, 6, 1, source);
        source.add(WATER);

        assertEquals(List.of(POWER, NET), terms.fees());
        assertThrows(UnsupportedOperationException.class, () -> terms.fees().add(WATER));
    }

    private static RevisionTerms terms(long rent, RevisionTerms.Deposit deposit, int term, int period,
                                       List<RevisionTerms.Fee> fees) {
        return new RevisionTerms(TermsSource.IN_EFFECT, null, NOW, rent, deposit, term, period, fees);
    }
}
