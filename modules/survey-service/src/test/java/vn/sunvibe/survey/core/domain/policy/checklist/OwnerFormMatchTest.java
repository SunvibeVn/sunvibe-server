package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.RentMoney;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An "owner's form" value equals what the owner submitted (survey-checklist BR-21, spec A-18). */
class OwnerFormMatchTest {

    private static final SubmittedForm FORM = new SubmittedForm(LocalDate.parse("2026-10-01"), "12 Lê Duẩn", "48",
            "20194", new BigDecimal("16.054456"), new BigDecimal("108.202167"), 15_000_000L,
            LocalDate.parse("2026-11-01"));

    /** spec A-18: both sides as intake normalises them (NFC, trimmed, whitespace runs collapsed). */
    @Test
    void address_spacingAndAccentEncoding_match() {
        String typed = Normalizer.normalize(" 12  Lê  Duẩn ", Normalizer.Form.NFD);

        assertTrue(OwnerFormMatch.matchesAddress(location(typed, "20194", "16.054456"), FORM));
    }

    @Test
    void address_otherWardTextOrPin_doesNotMatch() {
        assertFalse(OwnerFormMatch.matchesAddress(location("12 Lê Duẩn", "20195", "16.054456"), FORM));
        assertFalse(OwnerFormMatch.matchesAddress(location("12 le duẩn", "20194", "16.054456"), FORM));
        assertFalse(OwnerFormMatch.matchesAddress(location("12 Lê Duẩn", "20194", "16.054457"), FORM));
        assertFalse(OwnerFormMatch.matchesAddress(new ObservedLocation("12 Lê Duẩn", "48", "20194", null, null),
                FORM));
    }

    /** The pin a client sends with more decimals is the checked pin, rounded to 6 like the submission. */
    @Test
    void address_pinRoundedToSixDecimals_matches() {
        assertTrue(OwnerFormMatch.matchesAddress(location("12 Lê Duẩn", "20194", "16.0544560"), FORM));
        assertTrue(OwnerFormMatch.matchesAddress(location("12 Lê Duẩn", "20194", "16.0544563"), FORM));
    }

    @Test
    void address_submissionWithoutPin_matchesOnlyWithoutPin() {
        SubmittedForm noPin = new SubmittedForm(FORM.submittedOn(), FORM.addressDetail(), "48", "20194", null, null,
                null, null);

        assertTrue(OwnerFormMatch.matchesAddress(new ObservedLocation("12 Lê Duẩn", "48", "20194", null, null), noPin));
        assertFalse(OwnerFormMatch.matchesAddress(location("12 Lê Duẩn", "20194", "16.054456"), noPin));
    }

    @Test
    void rentAndAvailableFrom_equalTheSubmission() {
        assertTrue(OwnerFormMatch.matchesRent(RentMoney.monthly(15_000_000), FORM));
        assertFalse(OwnerFormMatch.matchesRent(RentMoney.monthly(15_000_001), FORM));
        assertTrue(OwnerFormMatch.matchesAvailableFrom(LocalDate.parse("2026-11-01"), FORM));
        assertFalse(OwnerFormMatch.matchesAvailableFrom(LocalDate.parse("2026-11-02"), FORM));
        SubmittedForm none = new SubmittedForm(FORM.submittedOn(), FORM.addressDetail(), "48", "20194", null, null,
                null, null);
        assertFalse(OwnerFormMatch.matchesRent(RentMoney.monthly(15_000_000), none));
        assertFalse(OwnerFormMatch.matchesAvailableFrom(LocalDate.parse("2026-11-01"), none));
    }

    private static ObservedLocation location(String address, String ward, String latitude) {
        return new ObservedLocation(address, "48", ward, new BigDecimal(latitude), new BigDecimal("108.202167"));
    }
}
