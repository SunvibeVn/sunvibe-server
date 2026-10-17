package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phone normalization (acquisition-owner-intake BR-2): the same Vietnamese number always has one stored form.
 */
class PhoneNumberTest {

    @ParameterizedTest
    @ValueSource(strings = {"0909 123 456", "+84 909.123.456", "(0909)-123-456", "0909123456", "+84909123456"})
    void parse_validFormats_normalizesToPlus84(String raw) {
        assertEquals("+84909123456", PhoneNumber.parse(raw).getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0123", "00909123456", "84909123456", "0909123456789", "abc", "", "   ", "0009123456",
            "+840909123456", "09091234a6"})
    void parse_invalidInput_throwsWithFieldPhoneAndNoValueInMessage(String raw) {
        InvalidIntakeValueException ex = assertThrows(InvalidIntakeValueException.class, () -> PhoneNumber.parse(raw));

        assertEquals("phone", ex.getField());
        assertFalse(!raw.isBlank() && ex.getMessage().contains(raw.strip()), "message must not echo the input");
    }

    @Test
    void parse_null_throwsWithFieldPhone() {
        InvalidIntakeValueException ex = assertThrows(InvalidIntakeValueException.class, () -> PhoneNumber.parse(null));

        assertEquals("phone", ex.getField());
    }

    @Test
    void equals_sameNumberInDifferentFormats_areEqual() {
        assertEquals(PhoneNumber.parse("0909 123 456"), PhoneNumber.parse("+84909123456"));
        assertEquals(PhoneNumber.parse("0909 123 456").hashCode(), PhoneNumber.parse("+84909123456").hashCode());
    }
}
