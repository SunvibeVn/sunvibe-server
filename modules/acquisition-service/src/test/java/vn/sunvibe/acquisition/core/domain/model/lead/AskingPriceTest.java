package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Asking price (acquisition-owner-intake BR-4): positive VND amount whose unit matches the intent.
 */
class AskingPriceTest {

    @ParameterizedTest
    @CsvSource({"SHORT_TERM_RENT,NIGHT", "LONG_TERM_RENT,MONTH", "SALE,TOTAL"})
    void of_unitMatchingIntent_isAccepted(OwnerLeadIntent intent, PriceUnit unit) {
        AskingPrice price = AskingPrice.of(1_500_000L, "VND", unit, intent);

        assertEquals(1_500_000L, price.getAmount());
        assertEquals("VND", price.getCurrency());
        assertEquals(unit, price.getUnit());
    }

    @ParameterizedTest
    @CsvSource({"SHORT_TERM_RENT,MONTH", "SHORT_TERM_RENT,TOTAL", "LONG_TERM_RENT,NIGHT", "LONG_TERM_RENT,TOTAL",
            "SALE,NIGHT", "SALE,MONTH"})
    void of_unitNotMatchingIntent_throwsWithFieldAskingPriceUnit(OwnerLeadIntent intent, PriceUnit unit) {
        InvalidIntakeValueException ex = assertThrows(InvalidIntakeValueException.class,
                () -> AskingPrice.of(1_500_000L, "VND", unit, intent));

        assertEquals("askingPrice.unit", ex.getField());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, 1_000_000_000_000_000L})
    void of_amountOutOfRange_throwsWithFieldAskingPriceAmount(long amount) {
        InvalidIntakeValueException ex = assertThrows(InvalidIntakeValueException.class,
                () -> AskingPrice.of(amount, "VND", PriceUnit.MONTH, OwnerLeadIntent.LONG_TERM_RENT));

        assertEquals("askingPrice.amount", ex.getField());
    }

    @Test
    void of_largestAmount_isAccepted() {
        AskingPrice price = AskingPrice.of(999_999_999_999_999L, "VND", PriceUnit.TOTAL, OwnerLeadIntent.SALE);

        assertEquals(999_999_999_999_999L, price.getAmount());
    }

    @Test
    void of_smallestAmount_isAccepted() {
        assertEquals(1L, AskingPrice.of(1L, "VND", PriceUnit.TOTAL, OwnerLeadIntent.SALE).getAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "vnd", ""})
    void of_currencyNotVnd_throwsWithFieldAskingPriceCurrency(String currency) {
        InvalidIntakeValueException ex = assertThrows(InvalidIntakeValueException.class,
                () -> AskingPrice.of(1_000L, currency, PriceUnit.TOTAL, OwnerLeadIntent.SALE));

        assertEquals("askingPrice.currency", ex.getField());
        assertFalse(ex.getMessage().contains("USD"), "message must not echo the value");
    }

    @Test
    void of_nullCurrency_throwsWithFieldAskingPriceCurrency() {
        InvalidIntakeValueException ex = assertThrows(InvalidIntakeValueException.class,
                () -> AskingPrice.of(1_000L, null, PriceUnit.TOTAL, OwnerLeadIntent.SALE));

        assertEquals("askingPrice.currency", ex.getField());
    }

    @Test
    void requiredPriceUnit_eachIntent_mapsToOneUnit() {
        assertEquals(PriceUnit.NIGHT, OwnerLeadIntent.SHORT_TERM_RENT.requiredPriceUnit());
        assertEquals(PriceUnit.MONTH, OwnerLeadIntent.LONG_TERM_RENT.requiredPriceUnit());
        assertEquals(PriceUnit.TOTAL, OwnerLeadIntent.SALE.requiredPriceUnit());
    }
}
