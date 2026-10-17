package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritoryChange;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.SaleTerritoryChangeEntity;

/**
 * Mapper to convert between the SaleTerritoryChange Domain Model and SaleTerritoryChangeEntity.
 * Domain → Entity: When saving to database.
 * Entity → Domain: When retrieving from database (rebuilt with SaleTerritoryChange.load).
 */
@Component
public class SaleTerritoryChangeEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public SaleTerritoryChange toDomain(SaleTerritoryChangeEntity entity) {
        if (entity == null) return null;

        return SaleTerritoryChange.load(entity.getId(), entity.getWardCode(), entity.getOldUserId(),
                entity.getNewUserId(), entity.getActorUserId(), entity.getOccurredAt());
    }

    /**
     * Convert Domain → Entity.
     */
    public SaleTerritoryChangeEntity toEntity(SaleTerritoryChange domain) {
        if (domain == null) return null;

        return SaleTerritoryChangeEntity.builder()
                .id(domain.getId())
                .wardCode(domain.getWardCode())
                .oldUserId(domain.getOldUserId())
                .newUserId(domain.getNewUserId())
                .actorUserId(domain.getActorUserId())
                .occurredAt(domain.getOccurredAt())
                .build();
    }
}
