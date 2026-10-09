package vn.sunvibe.auth.core.domain.model.user.vo;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;

import static org.junit.jupiter.api.Assertions.*;

class IdCardNumberTest {

    @Test
    void create_acceptsNineDigitCmnd() {
        IdCardNumber idCardNumber = IdCardNumber.create("123456789");
        assertEquals("123456789", idCardNumber.getValue());
    }

    @Test
    void create_acceptsTwelveDigitCccd() {
        IdCardNumber idCardNumber = IdCardNumber.create("123456789012");
        assertEquals("123456789012", idCardNumber.getValue());
    }

    @Test
    void create_rejectsNonDigitCharacters() {
        assertThrows(AuthValidationException.class, () -> IdCardNumber.create("12345678A"));
    }

    @Test
    void create_rejectsWrongLength() {
        assertThrows(AuthValidationException.class, () -> IdCardNumber.create("12345"));
    }

    @Test
    void create_rejectsBlank() {
        assertThrows(AuthValidationException.class, () -> IdCardNumber.create(""));
    }

    @Test
    void create_rejectsNull() {
        assertThrows(AuthValidationException.class, () -> IdCardNumber.create(null));
    }

    @Test
    void from_doesNotValidate() {
        IdCardNumber idCardNumber = IdCardNumber.from("not-a-valid-number");
        assertEquals("not-a-valid-number", idCardNumber.getValue());
    }

    @Test
    void equalsAndHashCode_sameValueAreEqual() {
        assertEquals(IdCardNumber.create("123456789"), IdCardNumber.create("123456789"));
    }
}
