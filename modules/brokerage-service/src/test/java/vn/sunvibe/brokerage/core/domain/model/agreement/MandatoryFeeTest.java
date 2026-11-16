package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * One mandatory fee of the confirmed terms (brokerage-agreement BR-8, AC-3, E-13; E05-S3 spec S3-D8): an amount with
 * its charge unit, or included in the rent, or at the provider's rate; a trimmed name of 1 to 100 characters.
 */
class MandatoryFeeTest {

    private static final long MAX = 999_999_999_999_999L;

    @Test
    void mandatoryFee_basisRules_amountAndUnitOnlyForAmount() {
        assertDoesNotThrow(() -> new MandatoryFee("Water", FeeBasis.AMOUNT, 1L, ChargeUnit.PER_M3));
        assertDoesNotThrow(() -> new MandatoryFee("Water", FeeBasis.AMOUNT, MAX, ChargeUnit.ONE_OFF));
        assertDoesNotThrow(() -> new MandatoryFee("Water", FeeBasis.INCLUDED, null, null));
        assertDoesNotThrow(() -> new MandatoryFee("Water", FeeBasis.PROVIDER_TARIFF, null, null));

        assertEquals("amount", refused(() -> new MandatoryFee("Water", FeeBasis.AMOUNT, 0L, ChargeUnit.PER_M3)));
        assertEquals("amount", refused(() -> new MandatoryFee("Water", FeeBasis.AMOUNT, MAX + 1, ChargeUnit.PER_M3)));
        assertEquals("amount", refused(() -> new MandatoryFee("Water", FeeBasis.AMOUNT, null, ChargeUnit.PER_M3)));
        assertEquals("chargeUnit", refused(() -> new MandatoryFee("Water", FeeBasis.AMOUNT, 5_000L, null)));
        assertEquals("amount", refused(() -> new MandatoryFee("Water", FeeBasis.INCLUDED, 5_000L, null)));
        assertEquals("chargeUnit", refused(() -> new MandatoryFee("Water", FeeBasis.PROVIDER_TARIFF, null,
                ChargeUnit.PER_M3)));
        assertEquals("basis", refused(() -> new MandatoryFee("Water", null, null, null)));
    }

    @Test
    void mandatoryFee_name_trimmedBoundedNoControlCharacter() {
        String hundred = "a".repeat(100);

        assertEquals("Water", new MandatoryFee("  Water  ", FeeBasis.INCLUDED, null, null).name());
        assertEquals(hundred, new MandatoryFee(hundred, FeeBasis.INCLUDED, null, null).name());
        assertEquals(hundred, new MandatoryFee("  " + hundred + "  ", FeeBasis.INCLUDED, null, null).name());
        assertEquals("Phí vệ sinh", new MandatoryFee("Phí vệ sinh", FeeBasis.INCLUDED, null, null).name());

        for (String name : List.of("a".repeat(101), "", "   ", "Wa\u0000ter", "Wa\nter", "Water\n", "Wa\u001Fter")) {
            assertEquals("name", refused(() -> new MandatoryFee(name, FeeBasis.INCLUDED, null, null)), name);
        }
        assertEquals("name", refused(() -> new MandatoryFee(null, FeeBasis.INCLUDED, null, null)));
    }

    @Test
    void mandatoryFee_toString_holdsNoNameOrAmount() {
        String text = new MandatoryFee("Cleaning", FeeBasis.AMOUNT, 100_000L, ChargeUnit.PER_MONTH).toString();

        assertFalse(text.contains("Cleaning"), text);
        assertFalse(text.contains("100000"), text);
    }

    private static String refused(Executable build) {
        return assertThrows(AgreementContentException.class, build).field();
    }
}
