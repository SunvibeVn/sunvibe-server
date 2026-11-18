package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

/**
 * What is refunded of the brokerage fee when the lease ends early (brokerage-agreement BR-7; E05-S4 spec S4-D3): no
 * refund, or a refund of 1 to 100 percent of the fee when the lease ends within 1 to 12 months. The first failing rule
 * refuses it with the field path inside the part.
 */
public record EarlyLeaseEndRefund(EarlyLeaseEndType type, Integer refundPercentOrNull, Integer withinMonthsOrNull) {

    private static final int MAX_REFUND_PERCENT = 100;
    private static final int MAX_WITHIN_MONTHS = 12;

    public EarlyLeaseEndRefund {
        if (type == null) {
            throw new AgreementContentException("type");
        }
        if (type == EarlyLeaseEndType.PARTIAL_REFUND) {
            if (refundPercentOrNull == null || refundPercentOrNull < 1 || refundPercentOrNull > MAX_REFUND_PERCENT) {
                throw new AgreementContentException("refundPercent");
            }
            if (withinMonthsOrNull == null || withinMonthsOrNull < 1 || withinMonthsOrNull > MAX_WITHIN_MONTHS) {
                throw new AgreementContentException("withinMonths");
            }
        } else {
            if (refundPercentOrNull != null) {
                throw new AgreementContentException("refundPercent");
            }
            if (withinMonthsOrNull != null) {
                throw new AgreementContentException("withinMonths");
            }
        }
    }

    /** brokerage-agreement BR-17: the fee policy is private, so the type only. */
    @Override
    public String toString() {
        return "EarlyLeaseEndRefund[" + type + "]";
    }
}
