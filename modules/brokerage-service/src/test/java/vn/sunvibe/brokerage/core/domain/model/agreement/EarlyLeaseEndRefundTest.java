package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType.NO_REFUND;
import static vn.sunvibe.brokerage.core.domain.model.agreement.EarlyLeaseEndType.PARTIAL_REFUND;

/**
 * What is refunded when a lease ends early (brokerage-agreement BR-7; E05-S4 spec S4-D3): no refund, or a refund of 1 to
 * 100 percent when the lease ends within 1 to 12 months.
 */
class EarlyLeaseEndRefundTest {

    @Test
    void partialRefund_bounds() {
        assertDoesNotThrow(() -> new EarlyLeaseEndRefund(PARTIAL_REFUND, 1, 1));
        EarlyLeaseEndRefund most = new EarlyLeaseEndRefund(PARTIAL_REFUND, 100, 12);
        assertEquals(100, most.refundPercentOrNull());
        assertEquals(12, most.withinMonthsOrNull());

        assertEquals("refundPercent", refused(() -> new EarlyLeaseEndRefund(PARTIAL_REFUND, null, 3)));
        assertEquals("refundPercent", refused(() -> new EarlyLeaseEndRefund(PARTIAL_REFUND, 0, 3)));
        assertEquals("refundPercent", refused(() -> new EarlyLeaseEndRefund(PARTIAL_REFUND, 101, 3)));
        assertEquals("withinMonths", refused(() -> new EarlyLeaseEndRefund(PARTIAL_REFUND, 50, null)));
        assertEquals("withinMonths", refused(() -> new EarlyLeaseEndRefund(PARTIAL_REFUND, 50, 0)));
        assertEquals("withinMonths", refused(() -> new EarlyLeaseEndRefund(PARTIAL_REFUND, 50, 13)));
        assertEquals("type", refused(() -> new EarlyLeaseEndRefund(null, null, null)));
    }

    @Test
    void noRefund_holdsNothing() {
        assertDoesNotThrow(() -> new EarlyLeaseEndRefund(NO_REFUND, null, null));

        assertEquals("refundPercent", refused(() -> new EarlyLeaseEndRefund(NO_REFUND, 50, null)));
        assertEquals("withinMonths", refused(() -> new EarlyLeaseEndRefund(NO_REFUND, null, 3)));
    }

    /** brokerage-agreement BR-17: never the percentage or the months. */
    @Test
    void toString_holdsNoValue() {
        String text = new EarlyLeaseEndRefund(PARTIAL_REFUND, 47, 11).toString();

        assertFalse(text.contains("47"), text);
        assertFalse(text.contains("11"), text);
    }

    private static String refused(Executable call) {
        return assertThrows(AgreementContentException.class, call).field();
    }
}
