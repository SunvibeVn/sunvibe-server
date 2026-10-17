package vn.sunvibe.acquisition.core.application.query;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

/**
 * Another lead that may come from the same owner, shown on a lead detail: only its id, code, status and creation
 * time, never its contact data.
 */
public record DuplicateCandidateView(String id, String requestCode, OwnerLeadStatus status, Instant createdAt) {

    public static DuplicateCandidateView of(OwnerLead lead) {
        return new DuplicateCandidateView(lead.getId().getValue(), lead.getRequestCode().getValue(),
                lead.getStatus(), lead.getCreatedAt());
    }
}
