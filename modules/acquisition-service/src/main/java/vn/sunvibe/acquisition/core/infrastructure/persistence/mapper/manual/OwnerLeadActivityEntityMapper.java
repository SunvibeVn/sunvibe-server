package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadActivityEntity;

/**
 * Mapper to convert between the OwnerLeadActivity Domain Model and OwnerLeadActivityEntity.
 * Domain → Entity: When saving to database.
 * Entity → Domain: When retrieving from database (rebuilt with OwnerLeadActivity.load).
 */
@Component
public class OwnerLeadActivityEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public OwnerLeadActivity toDomain(OwnerLeadActivityEntity entity) {
        if (entity == null) return null;

        return OwnerLeadActivity.load(
                entity.getId(),
                OwnerLeadId.of(entity.getLeadId()),
                entity.getType(),
                entity.getActorUserId(),
                entity.getFromStatus(),
                entity.getToStatus(),
                entity.getFromAssigneeId(),
                entity.getToAssigneeId(),
                entity.getNote(),
                entity.getNextActionAt(),
                entity.getOccurredAt(),
                entity.getReferenceId(),
                entity.getReferenceNo()
        );
    }

    /**
     * Convert Domain → Entity.
     */
    public OwnerLeadActivityEntity toEntity(OwnerLeadActivity domain) {
        if (domain == null) return null;

        return OwnerLeadActivityEntity.builder()
                .id(domain.getId())
                .leadId(domain.getLeadId().getValue())
                .type(domain.getType())
                .actorUserId(domain.getActorUserId())
                .fromStatus(domain.getFromStatus())
                .toStatus(domain.getToStatus())
                .fromAssigneeId(domain.getFromAssigneeId())
                .toAssigneeId(domain.getToAssigneeId())
                .note(domain.getNote())
                .nextActionAt(domain.getNextActionAt())
                .occurredAt(domain.getOccurredAt())
                .referenceId(domain.getReferenceId())
                .referenceNo(domain.getReferenceNo())
                .build();
    }
}
