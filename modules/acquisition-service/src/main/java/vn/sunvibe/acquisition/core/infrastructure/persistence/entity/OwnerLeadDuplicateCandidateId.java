package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;

/**
 * Composite primary key of owner_lead_duplicate_candidates: the lead and the lead it may repeat.
 */
@Embeddable
public record OwnerLeadDuplicateCandidateId(
        @Column(name = "lead_id", nullable = false, length = 26) String leadId,
        @Column(name = "candidate_lead_id", nullable = false, length = 26) String candidateLeadId
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
