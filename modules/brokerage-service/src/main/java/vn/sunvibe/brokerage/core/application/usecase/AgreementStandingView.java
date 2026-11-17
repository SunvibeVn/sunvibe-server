package vn.sunvibe.brokerage.core.application.usecase;

/**
 * Group (ii) of the listing brief for one agreement (listing section 7, LM-16; E05-S6 spec S6-D10): whether it ended
 * and who holds its basis lead now. No term, fee, date, contract number or owner.
 *
 * @param transactionType               its transaction type code
 * @param ended                         a final state, or the end of the end date of its version that counts has passed
 * @param basisLeadAssigneeIdOrNull     the basis lead's current assignee; null when unassigned or unknown
 */
public record AgreementStandingView(String agreementId, String unitId, String transactionType, boolean ended,
                                    String basisLeadAssigneeIdOrNull) {
}
