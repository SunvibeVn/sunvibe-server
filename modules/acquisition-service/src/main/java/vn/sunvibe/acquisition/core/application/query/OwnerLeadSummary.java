package vn.sunvibe.acquisition.core.application.query;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

/**
 * One lead as the queue lists it (acquisition-owner-lead-queue C-15): contact name and ward, but no phone, email,
 * address or position. {@code possibleDuplicate} is true when the lead has at least one duplicate candidate the
 * viewer can see; {@code version} is the one to send back with the next change.
 */
public record OwnerLeadSummary(String id, String requestCode, OwnerLeadStatus status, OwnerLeadIntent intent,
                               String contactName, String provinceCode, String wardCode, String wardName,
                               OwnerLeadPropertyType propertyType, String assignedUserId, Instant nextActionAt,
                               boolean possibleDuplicate, Instant createdAt, long version) {

    /** The summary of a loaded or saved lead (its version is never null then). */
    public static OwnerLeadSummary of(OwnerLead lead, boolean possibleDuplicate) {
        return new OwnerLeadSummary(lead.getId().getValue(), lead.getRequestCode().getValue(), lead.getStatus(),
                lead.getIntent(), lead.getContactName(), lead.getProvinceCode(), lead.getWardCode(),
                lead.getWardName(), lead.getPropertyType(), lead.getAssignedUserId(), lead.getNextActionAt(),
                possibleDuplicate, lead.getCreatedAt(), lead.getVersion());
    }

    /** The contact name is personal data: never print it. */
    @Override
    public String toString() {
        return "OwnerLeadSummary[" + id + "]";
    }
}
