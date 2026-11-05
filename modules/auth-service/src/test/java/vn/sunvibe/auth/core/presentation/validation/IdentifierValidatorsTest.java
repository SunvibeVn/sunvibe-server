package vn.sunvibe.auth.core.presentation.validation;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.presentation.validation.user.validator.ValidEmailValidator;
import vn.sunvibe.auth.core.presentation.validation.user.validator.ValidPhoneValidator;
import vn.sunvibe.auth.core.testsupport.IdentifierCorpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** auth-email-access S-1: the DTO validators have no rule of their own, so they agree with the value objects. */
class IdentifierValidatorsTest {

    private final ValidEmailValidator emailValidator = new ValidEmailValidator();
    private final ValidPhoneValidator phoneValidator = new ValidPhoneValidator();

    @Test
    void validators_agreeWithValueObjects() {
        for (IdentifierCorpus.Case c : IdentifierCorpus.email()) {
            assertEquals(c.valid(), emailValidator.isValid(c.raw(), null), c.id() + " " + c.note());
        }
        for (IdentifierCorpus.Case c : IdentifierCorpus.phone()) {
            assertEquals(c.valid(), phoneValidator.isValid(c.raw(), null), c.id() + " " + c.note());
        }
    }

    @Test
    void validators_nullPasses_leavingItToNotBlank() {
        assertTrue(emailValidator.isValid(null, null));
        assertTrue(phoneValidator.isValid(null, null));
    }
}
