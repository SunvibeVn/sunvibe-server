package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The confirmed terms of a version and its deposit (brokerage-agreement BR-8; E05-S3 spec S3-D8): every part may stay
 * empty in a draft, and a value outside its rule is refused with the field it is about.
 */
class VersionTermsTest {

    private static final long MAX = 999_999_999_999_999L;

    @Test
    void versionTerms_boundaries_acceptedAndRefusedWithTheirField() {
        assertDoesNotThrow(() -> terms(1L, null, null, null));
        assertDoesNotThrow(() -> terms(MAX, null, null, null));
        assertDoesNotThrow(() -> terms(null, 1, null, null));
        assertDoesNotThrow(() -> terms(null, 120, null, null));
        assertDoesNotThrow(() -> terms(null, null, 1, null));
        assertDoesNotThrow(() -> terms(null, null, 12, null));
        assertDoesNotThrow(() -> terms(null, null, null, List.of()));
        assertDoesNotThrow(() -> terms(null, null, null, fees(20)));

        assertEquals("monthlyRent.amount", refused(() -> terms(0L, null, null, null)));
        assertEquals("monthlyRent.amount", refused(() -> terms(MAX + 1, null, null, null)));
        assertEquals("minimumTermMonths", refused(() -> terms(null, 0, null, null)));
        assertEquals("minimumTermMonths", refused(() -> terms(null, 121, null, null)));
        assertEquals("paymentPeriodMonths", refused(() -> terms(null, null, 0, null)));
        assertEquals("paymentPeriodMonths", refused(() -> terms(null, null, 13, null)));
        assertEquals("mandatoryFees", refused(() -> terms(null, null, null, fees(21))));
        assertEquals("mandatoryFees", refused(() -> terms(null, null, null, Arrays.asList(fee("Water"), null))));
    }

    @Test
    void versionTerms_feeList_copiedAndUnmodifiable() {
        List<MandatoryFee> source = new ArrayList<>(List.of(fee("Water")));
        VersionTerms terms = terms(null, null, null, source);

        source.add(fee("Internet"));

        assertEquals(List.of(fee("Water")), terms.mandatoryFeesOrNull());
        assertThrows(UnsupportedOperationException.class, () -> terms.mandatoryFeesOrNull().add(fee("Parking")));
        assertNull(terms(null, null, null, null).mandatoryFeesOrNull());
        assertEquals(List.of(), terms(null, null, null, Collections.emptyList()).mandatoryFeesOrNull());
        assertNotEquals(terms(null, null, null, null), terms(null, null, null, List.of()));
    }

    @Test
    void versionTerms_none_everyPartNull() {
        assertNull(VersionTerms.NONE.monthlyRentOrNull());
        assertNull(VersionTerms.NONE.depositOrNull());
        assertNull(VersionTerms.NONE.minimumTermMonthsOrNull());
        assertNull(VersionTerms.NONE.paymentPeriodMonthsOrNull());
        assertNull(VersionTerms.NONE.mandatoryFeesOrNull());
        assertEquals(MAX, VersionTerms.MAX_AMOUNT);
        assertEquals(20, VersionTerms.MAX_FEES);
    }

    @Test
    void termsDeposit_monthsAndAmount_rulesWithTheirField() {
        assertDoesNotThrow(() -> new TermsDeposit(DepositBasis.MONTHS, 0, null));
        assertDoesNotThrow(() -> new TermsDeposit(DepositBasis.MONTHS, 12, null));
        assertDoesNotThrow(() -> new TermsDeposit(DepositBasis.AMOUNT, null, 0L));
        assertDoesNotThrow(() -> new TermsDeposit(DepositBasis.AMOUNT, null, MAX));

        assertEquals("deposit.months", refused(() -> new TermsDeposit(DepositBasis.MONTHS, 13, null)));
        assertEquals("deposit.months", refused(() -> new TermsDeposit(DepositBasis.MONTHS, -1, null)));
        assertEquals("deposit.months", refused(() -> new TermsDeposit(DepositBasis.MONTHS, null, null)));
        assertEquals("deposit.amount", refused(() -> new TermsDeposit(DepositBasis.MONTHS, 2, 1_000L)));
        assertEquals("deposit.amount", refused(() -> new TermsDeposit(DepositBasis.AMOUNT, null, -1L)));
        assertEquals("deposit.amount", refused(() -> new TermsDeposit(DepositBasis.AMOUNT, null, MAX + 1)));
        assertEquals("deposit.amount", refused(() -> new TermsDeposit(DepositBasis.AMOUNT, null, null)));
        assertEquals("deposit.months", refused(() -> new TermsDeposit(DepositBasis.AMOUNT, 2, 1_000L)));
        assertEquals("deposit.basis", refused(() -> new TermsDeposit(null, 2, null)));
    }

    @Test
    void versionTerms_toString_holdsNoAmountOrName() {
        TermsDeposit deposit = new TermsDeposit(DepositBasis.AMOUNT, null, 3_000_000L);
        VersionTerms terms = new VersionTerms(8_000_000L, deposit, 12, 1,
                List.of(new MandatoryFee("Cleaning", FeeBasis.AMOUNT, 100_000L, ChargeUnit.PER_MONTH)));

        for (String text : List.of(terms.toString(), deposit.toString())) {
            for (String value : List.of("8000000", "3000000", "100000", "Cleaning")) {
                assertFalse(text.contains(value), text);
            }
        }
    }

    @Test
    void contentException_within_prefixesTheField() {
        AgreementContentException refused = new AgreementContentException("amount");

        AgreementContentException within = refused.within("terms.mandatoryFees[2].");

        assertEquals("terms.mandatoryFees[2].amount", within.field());
        assertEquals("amount", refused.field());
        assertEquals(refused.getMessage(), within.getMessage());
        assertFalse(within.getMessage().contains("amount"), within.getMessage());
    }

    private static VersionTerms terms(Long rent, Integer minimum, Integer payment, List<MandatoryFee> fees) {
        return new VersionTerms(rent, null, minimum, payment, fees);
    }

    private static List<MandatoryFee> fees(int count) {
        List<MandatoryFee> fees = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fees.add(fee("Fee " + i));
        }
        return fees;
    }

    private static MandatoryFee fee(String name) {
        return new MandatoryFee(name, FeeBasis.INCLUDED, null, null);
    }

    private static String refused(Executable build) {
        return assertThrows(AgreementContentException.class, build).field();
    }
}
