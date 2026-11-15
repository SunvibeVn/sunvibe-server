package vn.sunvibe.brokerage.core.application.shared.external.lead;

/**
 * What brokerage reads of a visible owner lead.
 *
 * @param leadId               the lead
 * @param assignedUserIdOrNull its current assignee; null when it has none
 */
public record BasisLeadFacts(String leadId, String assignedUserIdOrNull) {
}
