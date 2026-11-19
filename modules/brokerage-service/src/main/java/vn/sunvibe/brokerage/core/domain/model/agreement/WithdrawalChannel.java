package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * How the owner's withdrawal reached Sunvibe (brokerage-agreement BR-14 "ngày và nguồn"; E05-S8 spec S8-D5): a closed
 * list with OTHER, the optional note holding any detail. Stored and exposed.
 */
public enum WithdrawalChannel {
    PHONE_CALL,
    IN_PERSON,
    MESSAGE,
    EMAIL,
    OTHER
}
