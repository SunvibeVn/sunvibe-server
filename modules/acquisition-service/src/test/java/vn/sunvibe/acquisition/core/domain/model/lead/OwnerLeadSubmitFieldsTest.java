package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Submitted lead fields are personal data (acquisition module brief BR-2), and a stored asking price always uses the
 * unit its intent needs (acquisition-owner-intake BR-4).
 */
class OwnerLeadSubmitFieldsTest {

    private static OwnerLead.SubmitFields fields(OwnerLeadIntent intent, Optional<AskingPrice> price) {
        return new OwnerLead.SubmitFields(
                "Nguyen Van A", PhoneNumber.parse("0909 123 456"), "a@example.vn", intent,
                "48", "Da Nang", "20242", "Hai Chau", "12 Bach Dang", OwnerLead.addressKey("12 Bach Dang"),
                GeoPoint.of(new BigDecimal("16.0544"), new BigDecimal("108.2022"), false),
                OwnerLeadPropertyType.APARTMENT, price, LocalDate.parse("2026-11-01"), "Near the river", "Evening");
    }

    @Test
    void toString_anyFields_printsNoPersonalData() {
        String text = fields(OwnerLeadIntent.LONG_TERM_RENT, Optional.empty()).toString();

        for (String secret : new String[]{"Nguyen", "0909", "+84909123456", "a@example.vn", "Bach Dang",
                "bach dang", "16.0544", "108.2022", "Near the river", "Evening"}) {
            assertFalse(text.contains(secret), "toString must not print " + secret);
        }
    }

    @Test
    void submit_priceUnitNotMatchingIntent_isRefused() {
        Optional<AskingPrice> monthly =
                Optional.of(AskingPrice.of(12_000_000L, "VND", PriceUnit.MONTH, OwnerLeadIntent.LONG_TERM_RENT));

        assertThrows(IllegalArgumentException.class, () -> OwnerLead.submit(fields(OwnerLeadIntent.SALE, monthly),
                ConsentRecordId.generate(), Optional.empty(), "{}", RequestCode.of("SV-7K3M-Q9TX")));
    }
}
