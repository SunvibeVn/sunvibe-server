package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadDuplicateCandidateEntity;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadDuplicateCandidateId;

import java.time.Instant;

/**
 * Mapper from a lead and one candidate to OwnerLeadDuplicateCandidateEntity. The link has no domain model of its own
 * yet, so there is only the Domain → Entity direction.
 */
@Component
public class DuplicateCandidateEntityMapper {

    public OwnerLeadDuplicateCandidateEntity toEntity(OwnerLeadId leadId, OwnerLeadId candidateId,
                                                      Instant detectedAt) {
        return OwnerLeadDuplicateCandidateEntity.builder()
                .id(new OwnerLeadDuplicateCandidateId(leadId.getValue(), candidateId.getValue()))
                .detectedAt(detectedAt)
                .build();
    }
}
