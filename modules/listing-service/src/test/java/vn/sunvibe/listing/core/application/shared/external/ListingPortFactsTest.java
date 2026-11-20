package vn.sunvibe.listing.core.application.shared.external;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.application.shared.external.brokerage.AgreementTermsFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitProfileFacts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The facts listing's ports carry from E05 and from property (listing-publish E06-S2 spec E06S2-D12, E06S2-D13,
 * E06S2-D21): private terms and unpublished unit values never reach a log through their text, and the fee list is a
 * copy no caller can change.
 */
class ListingPortFactsTest {

    private static final AgreementTermsFacts.Fee FEE = new AgreementTermsFacts.Fee("secret-fee", "AMOUNT", 123_456L,
            "PER_KWH");
    private static final AgreementTermsFacts.Terms TERMS = new AgreementTermsFacts.Terms(7_654_321L, "MONTHS", 2, null,
            6, 1, List.of(FEE));

    @Test
    void toString_termsAndProfileFacts_hidden() {
        AgreementTermsFacts facts = new AgreementTermsFacts(false, TERMS, LocalDate.parse("2026-12-01"), TERMS);
        UnitProfileFacts profile = new UnitProfileFacts("01UNIT", "APARTMENT", "48", "Secret Province", "20242",
                "Secret Ward", 3, 2, 45.5);

        assertAll(
                () -> assertEquals("AgreementTermsFacts[hidden]", facts.toString()),
                () -> assertEquals("Terms[hidden]", TERMS.toString()),
                () -> assertEquals("Fee[hidden]", FEE.toString()),
                () -> assertEquals("UnitProfileFacts[hidden]", profile.toString()));
        String all = facts + " " + TERMS + " " + FEE + " " + profile;
        for (String value : List.of("secret", "Secret", "7654321", "123456", "2026", "01UNIT", "45.5")) {
            assertFalse(all.contains(value), value);
        }
    }

    @Test
    void agreementTermsFacts_fees_unmodifiableCopy() {
        List<AgreementTermsFacts.Fee> source = new ArrayList<>(List.of(FEE));
        AgreementTermsFacts.Terms terms = new AgreementTermsFacts.Terms(8_000_000L, "AMOUNT", null, 0L, 6, 1, source);
        source.clear();

        assertEquals(List.of(FEE), terms.fees());
        assertThrows(UnsupportedOperationException.class, () -> terms.fees().add(FEE));
    }
}
