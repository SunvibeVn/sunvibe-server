package vn.sunvibe.auth.core.domain.model.user.vo;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @Test
    void email_from_keepsStoredCase() {
        assertEquals("Mixed.Case@Example.COM", Email.from("Mixed.Case@Example.COM").getValue());
    }

    @Test
    void create_nullValue_refused() {
        assertThrows(AuthValidationException.class, () -> Email.create(null));
    }

    @Test
    void create_refusedValue_messageQuotesNothing() {
        AuthValidationException refused = assertThrows(AuthValidationException.class,
                () -> Email.create("secret-address@"));

        assertFalse(refused.getMessage().contains("secret-address"));
    }
}
