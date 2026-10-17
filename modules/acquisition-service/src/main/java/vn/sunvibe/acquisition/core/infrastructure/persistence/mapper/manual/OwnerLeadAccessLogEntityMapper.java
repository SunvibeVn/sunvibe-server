package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.log.OwnerLeadAccessLog;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadAccessLogEntity;

/**
 * Mapper to convert between the OwnerLeadAccessLog Domain Model and OwnerLeadAccessLogEntity.
 * Domain → Entity: When saving to database.
 * Entity → Domain: When retrieving from database (rebuilt with OwnerLeadAccessLog.load).
 */
@Component
public class OwnerLeadAccessLogEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public OwnerLeadAccessLog toDomain(OwnerLeadAccessLogEntity entity) {
        if (entity == null) return null;

        return OwnerLeadAccessLog.load(entity.getId(), OwnerLeadId.of(entity.getLeadId()), entity.getUserId(),
                entity.getAccessedAt());
    }

    /**
     * Convert Domain → Entity.
     */
    public OwnerLeadAccessLogEntity toEntity(OwnerLeadAccessLog domain) {
        if (domain == null) return null;

        return OwnerLeadAccessLogEntity.builder()
                .id(domain.getId())
                .leadId(domain.getLeadId().getValue())
                .userId(domain.getUserId())
                .accessedAt(domain.getAccessedAt())
                .build();
    }
}
