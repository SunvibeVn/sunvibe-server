package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The brokerage fee policy of a version (brokerage-agreement BR-7; E05-S4 spec S4-D3): the owner pays when a lease is
 * signed; the formula, the due days (0 to 30), the renewal, the early end and the protection days (0 to 180), each
 * nullable in a draft; a kind outside its place is refused.
 */
class BrokerageFeeTest {

    private static final FeeFormula PERCENT = FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT,
            FeeFormulaTest.tiers(1, 50, 12, 100), null, null);
    private static final FeeFormula NOT_CHARGED = FeeFormula.of(FeeFormulaType.NOT_CHARGED, null, null, null);
    private static final FeeFormula FREE = FeeFormula.of(FeeFormulaType.FREE, null, null, "First unit");
    private static final EarlyLeaseEndRefund PARTIAL = new EarlyLeaseEndRefund(EarlyLeaseEndType.PARTIAL_REFUND, 50, 3);

    @Test
    void none_everyPartNull() {
        assertNull(BrokerageFee.NONE.formulaOrNull());
        assertNull(BrokerageFee.NONE.dueDaysOrNull());
        assertNull(BrokerageFee.NONE.renewalOrNull());
        assertNull(BrokerageFee.NONE.earlyLeaseEndOrNull());
        assertNull(BrokerageFee.NONE.protectionDaysOrNull());
        assertEquals(BrokerageFee.NONE, new BrokerageFee(null, null, null, null, null));
    }

    @Test
    void places_feeTypeAndRenewalType_refusedOutsideTheirPlace() {
        assertDoesNotThrow(() -> new BrokerageFee(PERCENT, 7, NOT_CHARGED, PARTIAL, 90));
        assertDoesNotThrow(() -> new BrokerageFee(FREE, 7, PERCENT, PARTIAL, 90));

        assertEquals("formula.type", refused(() -> new BrokerageFee(NOT_CHARGED, 7, NOT_CHARGED, PARTIAL, 90)));
        assertEquals("renewal.type", refused(() -> new BrokerageFee(PERCENT, 7, FREE, PARTIAL, 90)));
    }

    @Test
    void dueAndProtection_bounds_zeroValid() {
        for (int days : List.of(0, 30)) {
            assertEquals(days, new BrokerageFee(null, days, null, null, null).dueDaysOrNull());
        }
        for (int days : List.of(0, 180)) {
            assertEquals(days, new BrokerageFee(null, null, null, null, days).protectionDaysOrNull());
        }

        for (int days : List.of(-1, 31)) {
            assertEquals("dueDays", refused(() -> new BrokerageFee(null, days, null, null, null)));
        }
        for (int days : List.of(-1, 181)) {
            assertEquals("protectionDays", refused(() -> new BrokerageFee(null, null, null, null, days)));
        }
    }

    /** brokerage-agreement BR-7 and owner G2 D-23: the owner pays, when the lease is signed; neither is entered. */
    @Test
    void payerAndMoment_fixed() {
        assertEquals(FeePayer.OWNER, BrokerageFee.NONE.payer());
        assertEquals(FeeChargePoint.LEASE_SIGNED, BrokerageFee.NONE.chargedWhen());
        assertEquals(FeePayer.OWNER, new BrokerageFee(PERCENT, 7, NOT_CHARGED, PARTIAL, 90).payer());
        assertEquals(FeeChargePoint.LEASE_SIGNED, new BrokerageFee(PERCENT, 7, NOT_CHARGED, PARTIAL, 90).chargedWhen());
    }

    /** brokerage-agreement BR-17 (SD-14): no amount, percentage, reason or number of days. */
    @Test
    void toString_holdsNoValue() {
        FeeFormula fixed = FeeFormula.of(FeeFormulaType.FIXED_AMOUNT, null, 2_345_678L, null);
        FeeFormula percent = FeeFormula.of(FeeFormulaType.PERCENT_OF_RENT, FeeFormulaTest.tiers(1, 77), null, null);
        List<String> texts = List.of(new BrokerageFee(fixed, 23, percent, PARTIAL, 166).toString(),
                new BrokerageFee(FeeFormula.of(FeeFormulaType.FREE, null, null, "Secret reason"), null, null, null, null)
                        .toString());

        for (String text : texts) {
            for (String value : List.of("2345678", "77", "23", "166", "Secret", "50")) {
                assertFalse(text.contains(value), value + " in " + text);
            }
        }
    }

    private static String refused(Executable call) {
        return assertThrows(AgreementContentException.class, call).field();
    }
}
