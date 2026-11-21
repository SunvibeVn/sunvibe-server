package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * Why an agreement was terminated (brokerage-agreement BR-14; E05-S8 spec S8-D5): the owner withdrew, the unit is let,
 * or Sunvibe decided to end it. Stored and exposed in this declaration order.
 */
public enum TerminationType {
    /** The owner withdrew ("chủ rút"): recorded with the date the owner said it and the channel it came through. */
    OWNER_WITHDREW,
    /** The unit is let ("căn đã cho thuê"): recorded with the date reported and whether Sunvibe introduced the tenant. */
    UNIT_LET,
    /** Sunvibe ended the agreement ("Sunvibe chấm dứt"): a manager only, with the reason in words. */
    SUNVIBE_TERMINATED
}
