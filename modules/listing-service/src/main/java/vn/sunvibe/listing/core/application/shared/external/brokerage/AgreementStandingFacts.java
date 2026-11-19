package vn.sunvibe.listing.core.application.shared.external.brokerage;

/**
 * Group (ii) of the listing brief for one agreement, as E05 publishes it (listing section 7, LM-16; listing-publish
 * E06-S1 spec E06S1-D9): plain values only.
 *
 * @param transactionType           the agreement's transaction type code, as E05 sends it
 * @param ended                     the agreement reached a final state or the end of its end date
 * @param basisLeadAssigneeIdOrNull the current assignee of the agreement's basis lead; null when unassigned or unknown
 */
public record AgreementStandingFacts(String agreementId, String unitId, String transactionType, boolean ended,
                                     String basisLeadAssigneeIdOrNull) {
}
