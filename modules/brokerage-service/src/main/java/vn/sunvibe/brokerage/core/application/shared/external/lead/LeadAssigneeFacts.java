package vn.sunvibe.brokerage.core.application.shared.external.lead;

/**
 * The current assignee of one lead, read at one moment (E05-S6 spec S6-D10, S6-D17).
 *
 * @param assignedUserIdOrNull the assignee now; null on an unassigned lead
 */
public record LeadAssigneeFacts(String leadId, String assignedUserIdOrNull) {
}
