package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Public request code (acquisition-owner-intake BR-11): SV-XXXX-XXXX without look-alike characters.
 */
class RequestCodeTest {

    private static final Pattern FORMAT = Pattern.compile("^SV-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$");

    @Test
    void generate_thousandCodes_matchFormatAlphabetAndVary() {
        SecureRandom random = new SecureRandom();
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            String code = RequestCode.generate(random).getValue();
            assertTrue(FORMAT.matcher(code).matches(), "bad format: " + code);
            assertFalse(code.substring(3).matches(".*[01OI].*"), "look-alike character in " + code);
            codes.add(code);
        }

        assertTrue(codes.size() > 1, "codes must not all be equal");
    }

    @Test
    void of_validCode_keepsValue() {
        assertEquals("SV-7K3M-Q9TX", RequestCode.of("SV-7K3M-Q9TX").getValue());
    }

    @Test
    void of_codeWithExcludedCharacters_throws() {
        InvalidIntakeValueException ex = assertThrows(InvalidIntakeValueException.class,
                () -> RequestCode.of("SV-0000-0000"));

        assertEquals("requestCode", ex.getField());
    }

    @Test
    void of_wrongShapeOrNull_throws() {
        assertThrows(InvalidIntakeValueException.class, () -> RequestCode.of("sv-7k3m-q9tx"));
        assertThrows(InvalidIntakeValueException.class, () -> RequestCode.of("SV-7K3M-Q9T"));
        assertThrows(InvalidIntakeValueException.class, () -> RequestCode.of(null));
    }

    @Test
    void equals_sameValue_areEqual() {
        assertEquals(RequestCode.of("SV-7K3M-Q9TX"), RequestCode.of("SV-7K3M-Q9TX"));
    }
}
