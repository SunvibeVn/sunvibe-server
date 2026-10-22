package vn.sunvibe.acquisition.core.application.query;

import vn.sunvibe.acquisition.core.application.shared.StaffNames;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;
import java.util.Map;

/**
 * One lead as the queue lists it (acquisition-owner-lead-queue C-15): contact name and ward, but no phone, email,
 * address or position. {@code possibleDuplicate} is true when the lead has at least one duplicate candidate the
 * viewer can see; {@code version} is the one to send back with the next change. {@code assignedUserName} is the
 * assignee's full name, null when nobody holds the lead or the id has no user row
 * (acquisition-owner-lead-queue-ui C-18). {@code lastActivityAt} is the time of the lead's latest history entry, its
 * creation time when it has none (acquisition-owner-lead-queue-ui C-49).
 */
public record OwnerLeadSummary(String id, String requestCode, OwnerLeadStatus status, OwnerLeadIntent intent,
                               String contactName, String provinceCode, String wardCode, String wardName,
                               OwnerLeadPropertyType propertyType, String assignedUserId, String assignedUserName,
                               Instant nextActionAt,
                               boolean possibleDuplicate, Instant createdAt, Instant lastActivityAt,
                               long version) {

    /**
     * The summary of a loaded or saved lead (its version is never null then); {@code names} maps staff ids to full
     * names and may lack the assignee; a null {@code lastActivityAt} (no history) falls back to the creation time.
     */
    public static OwnerLeadSummary of(OwnerLead lead, boolean possibleDuplicate, Map<String, String> names,
                                      Instant lastActivityAt) {
        return new OwnerLeadSummary(lead.getId().getValue(), lead.getRequestCode().getValue(), lead.getStatus(),
                lead.getIntent(), lead.getContactName(), lead.getProvinceCode(), lead.getWardCode(),
                lead.getWardName(), lead.getPropertyType(), lead.getAssignedUserId(),
                StaffNames.nameOf(names, lead.getAssignedUserId()), lead.getNextActionAt(),
                possibleDuplicate, lead.getCreatedAt(),
                lastActivityAt == null ? lead.getCreatedAt() : lastActivityAt, lead.getVersion());
    }

    /** The contact name is personal data: never print it. */
    @Override
    public String toString() {
        return "OwnerLeadSummary[" + id + "]";
    }
}
