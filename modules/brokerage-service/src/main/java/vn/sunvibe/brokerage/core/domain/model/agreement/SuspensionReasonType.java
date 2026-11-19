package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * Why a manager suspended an agreement in effect (brokerage-agreement BR-15 "khiếu nại, nghi gian lận, tranh chấp, cần
 * kiểm tra"; E05-S8 spec S8-D6). Stored and exposed.
 */
public enum SuspensionReasonType {
    COMPLAINT,
    SUSPECTED_FRAUD,
    DISPUTE,
    NEEDS_CHECK
}
