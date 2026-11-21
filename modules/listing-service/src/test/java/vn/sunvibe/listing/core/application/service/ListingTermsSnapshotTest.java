package vn.sunvibe.listing.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.domain.model.listing.ChargeUnit;
import vn.sunvibe.listing.core.domain.model.listing.DepositBasis;
import vn.sunvibe.listing.core.domain.model.listing.FeeBasis;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.TermsSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The one home of E05's terms mapping (listing-publish E06-S2 spec E06S2-D6, plan E06S2-P4): codes mapped by explicit
 * switches, bounds applied by {@link RevisionTerms}; an unknown code or a value outside the bounds fails closed with a
 * message that names no value.
 */
class ListingTermsSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:15:00.123456Z");

    @Test
    void snapshotOf_inEffectTerms_everyField() {
        AgreementTermsFacts.Terms terms = new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, 6, 1, List.of(
                new AgreementTermsFacts.Fee("Điện", "AMOUNT", 3_500L, "PER_KWH"),
                new AgreementTermsFacts.Fee("Internet", "INCLUDED", null, null),
                new AgreementTermsFacts.Fee("Nước", "PROVIDER_TARIFF", null, null),
                new AgreementTermsFacts.Fee("Giữ xe", "AMOUNT", 100_000L, "PER_VEHICLE_PER_MONTH")));

        assertEquals(new RevisionTerms(TermsSource.IN_EFFECT, null, NOW, 8_000_000L,
                        new RevisionTerms.Deposit(DepositBasis.MONTHS, 2, null), 6, 1, List.of(
                        new RevisionTerms.Fee("Điện", FeeBasis.AMOUNT, 3_500L, ChargeUnit.PER_KWH),
                        new RevisionTerms.Fee("Internet", FeeBasis.INCLUDED, null, null),
                        new RevisionTerms.Fee("Nước", FeeBasis.PROVIDER_TARIFF, null, null),
                        new RevisionTerms.Fee("Giữ xe", FeeBasis.AMOUNT, 100_000L,
                                ChargeUnit.PER_VEHICLE_PER_MONTH))),
                ListingCommandService.snapshotOf(terms, TermsSource.IN_EFFECT, null, NOW));
        AgreementTermsFacts.Terms amountDeposit = new AgreementTermsFacts.Terms(9_500_000L, "AMOUNT", null, 0L, 12, 3,
                List.of());
        assertEquals(new RevisionTerms(TermsSource.PENDING_START, LocalDate.parse("2026-12-01"), NOW, 9_500_000L,
                        new RevisionTerms.Deposit(DepositBasis.AMOUNT, null, 0L), 12, 3, List.of()),
                ListingCommandService.snapshotOf(amountDeposit, TermsSource.PENDING_START,
                        LocalDate.parse("2026-12-01"), NOW));
    }

    @Test
    void snapshotOf_unknownCode_illegalState() {
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> ListingCommandService.snapshotOf(
                        terms("WEEKS", 2, List.of()), TermsSource.IN_EFFECT, null, NOW)),
                () -> assertThrows(IllegalStateException.class, () -> ListingCommandService.snapshotOf(
                        terms("MONTHS", 2, List.of(new AgreementTermsFacts.Fee("Fee", "FREE", null, null))),
                        TermsSource.IN_EFFECT, null, NOW)),
                () -> assertThrows(IllegalStateException.class, () -> ListingCommandService.snapshotOf(
                        terms("MONTHS", 2, List.of(new AgreementTermsFacts.Fee("Fee", "AMOUNT", 1_000L, "PER_DAY"))),
                        TermsSource.IN_EFFECT, null, NOW)),
                () -> assertThrows(IllegalStateException.class, () -> ListingCommandService.snapshotOf(
                        terms(null, null, List.of()), TermsSource.IN_EFFECT, null, NOW)));
    }

    @Test
    void snapshotOf_outOfBounds_illegalStateNamingNoValue() {
        AgreementTermsFacts.Terms tooHigh = new AgreementTermsFacts.Terms(1_000_000_000_000_000L, "MONTHS", 2, null, 6, 1,
                List.of(new AgreementTermsFacts.Fee("secret-fee", "AMOUNT", 7_654_321L, "PER_MONTH")));
        AgreementTermsFacts.Terms noTerm = new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, null, 1, List.of());
        AgreementTermsFacts.Terms longName = new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 2, null, 6, 1,
                List.of(new AgreementTermsFacts.Fee("secret-".repeat(20), "INCLUDED", null, null)));

        for (AgreementTermsFacts.Terms broken : List.of(tooHigh, noTerm, longName,
                new AgreementTermsFacts.Terms(0L, "MONTHS", 2, null, 6, 1, List.of()),
                new AgreementTermsFacts.Terms(8_000_000L, "MONTHS", 13, null, 6, 1, List.of()))) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> ListingCommandService.snapshotOf(broken, TermsSource.IN_EFFECT, null, NOW));
            String text = failure.getMessage() + " " + (failure.getCause() == null ? "" : failure.getCause().getMessage());
            for (String value : List.of("1000000000000000", "7654321", "secret", "8000000")) {
                assertFalse(text.contains(value), value);
            }
        }
        assertThrows(IllegalStateException.class, () -> ListingCommandService.snapshotOf(
                terms("MONTHS", 2, List.of()), TermsSource.PENDING_START, null, NOW));
    }

    private static AgreementTermsFacts.Terms terms(String depositBasis, Integer months,
                                                   List<AgreementTermsFacts.Fee> fees) {
        return new AgreementTermsFacts.Terms(8_000_000L, depositBasis, months, null, 6, 1, fees);
    }
}
