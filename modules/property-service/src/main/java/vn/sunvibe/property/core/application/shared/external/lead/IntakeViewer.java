package vn.sunvibe.property.core.application.shared.external.lead;

/**
 * The staff member an intake command or read runs for.
 *
 * @param seesAllLeads a manager role sees every lead; a Sale only the leads assigned to them
 */
public record IntakeViewer(String userId, boolean seesAllLeads) {
}
