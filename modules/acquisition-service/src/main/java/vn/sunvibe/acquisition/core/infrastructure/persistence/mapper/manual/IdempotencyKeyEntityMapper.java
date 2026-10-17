package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerIntakeIdempotencyKeyEntity;

/**
 * Mapper to convert between the IdempotencyRecord Domain Model and OwnerIntakeIdempotencyKeyEntity.
 */
@Component
public class IdempotencyKeyEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public IdempotencyRecord toDomain(OwnerIntakeIdempotencyKeyEntity entity) {
        if (entity == null) return null;

        return new IdempotencyRecord(entity.getKey(), entity.getRequestHash(), OwnerLeadId.of(entity.getLeadId()),
                entity.getCreatedAt());
    }

    /**
     * Convert Domain → Entity.
     */
    public OwnerIntakeIdempotencyKeyEntity toEntity(IdempotencyRecord domain) {
        if (domain == null) return null;

        return OwnerIntakeIdempotencyKeyEntity.builder()
                .key(domain.key())
                .requestHash(domain.requestHash())
                .leadId(domain.leadId().getValue())
                .createdAt(domain.createdAt())
                .build();
    }
}
