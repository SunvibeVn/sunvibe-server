package vn.sunvibe.brokerage.core.application.usecase;

/**
 * The unfinished agreement of a unit and a transaction type (brokerage-agreement BR-1: at most one; listing section 7,
 * LM-16; E05-S6 spec S6-D10).
 *
 * @param transactionType its transaction type code
 */
public record UnfinishedAgreementView(String unitId, String agreementId, String transactionType) {
}
