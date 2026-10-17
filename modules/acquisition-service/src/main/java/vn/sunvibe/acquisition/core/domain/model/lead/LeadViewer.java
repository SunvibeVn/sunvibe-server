package vn.sunvibe.acquisition.core.domain.model.lead;

/**
 * The staff member looking at leads and how much they may see. This is the one place that decides whether a lead is
 * visible (acquisition-owner-lead-queue BR-1): a SALE sees only leads assigned to them, so a lead nobody holds is
 * hidden from them and a lead that was reassigned disappears from the previous holder.
 */
public record LeadViewer(String userId, LeadAccess access) {

    public LeadViewer {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required.");
        }
        if (access == null) {
            throw new IllegalArgumentException("access is required.");
        }
    }

    public boolean canSee(OwnerLead lead) {
        return access == LeadAccess.ALL || userId.equals(lead.getAssignedUserId());
    }
}
