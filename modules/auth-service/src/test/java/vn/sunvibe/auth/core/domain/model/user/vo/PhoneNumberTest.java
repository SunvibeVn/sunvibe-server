package vn.sunvibe.auth.core.domain.model.user.vo;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneNumberTest {

    @Test
    void create_variantsOfOneNumber_areEqual() {
        PhoneNumber national = PhoneNumber.create("0901234567");

        assertEquals(national, PhoneNumber.create("+84 901 234 567"));
        assertEquals(national, PhoneNumber.create("84901234567"));
    }

    /** auth-email-access 4.3: no `00`. The parser would drop the second 0 and take the number of somebody else. */
    @Test
    void create_doubleZero_refused() {
        for (String raw : java.util.List.of("00901234567", "000901234567", "0 0901234567", "840901234567", "+840901234567")) {
            assertThrows(AuthValidationException.class, () -> PhoneNumber.create(raw), raw);
        }
    }

    @Test
    void create_nullValue_refused() {
        assertThrows(AuthValidationException.class, () -> PhoneNumber.create(null));
    }

    @Test
    void toString_namesNoDigits() {
        assertFalse(PhoneNumber.create("0901234567").toString().contains("901234"));
    }

    @Test
    void create_refusedValue_messageQuotesNothing() {
        AuthValidationException refused = assertThrows(AuthValidationException.class,
                () -> PhoneNumber.create("0123456789"));

        assertFalse(refused.getMessage().contains("0123456789"));
    }
}
