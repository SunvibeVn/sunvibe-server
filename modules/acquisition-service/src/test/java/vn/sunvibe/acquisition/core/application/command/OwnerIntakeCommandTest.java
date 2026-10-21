package vn.sunvibe.acquisition.core.application.command;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The intake command carries personal data (acquisition module brief BR-2), so printing it shows none of it.
 */
class OwnerIntakeCommandTest {

    @Test
    void toString_anyCommand_printsNoPersonalData() {
        OwnerIntakeCommand command = new OwnerIntakeCommand("key-0001-abcdefgh", "Nguyen Thi Lan", "0909 123 456",
                "lan@example.com", OwnerLeadIntent.LONG_TERM_RENT, "48", "20242", "12 Bach Dang",
                new BigDecimal("16.0678"), new BigDecimal("108.2208"), OwnerLeadPropertyType.TWO_BEDROOM,
                15_000_000L, "VND", PriceUnit.MONTH, LocalDate.parse("2026-11-01"), "Furnished", "Evening",
                true, "owner-intake-2026-10-21", "203.0.113.7", "Mozilla/5.0 test", "{\"phone\":\"0909 123 456\"}");

        String text = command.toString();

        for (String secret : new String[]{"Nguyen", "0909", "lan@example.com", "Bach Dang", "16.0678", "108.2208",
                "Furnished", "Evening", "203.0.113.7", "Mozilla", "key-0001"}) {
            assertFalse(text.contains(secret), "toString must not print " + secret);
        }
    }
}
