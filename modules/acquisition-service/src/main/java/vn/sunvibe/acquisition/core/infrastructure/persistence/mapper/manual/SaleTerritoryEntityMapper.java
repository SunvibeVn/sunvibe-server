package vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.domain.model.territory.SaleTerritory;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.SaleTerritoryEntity;

/**
 * Mapper from SaleTerritoryEntity to the SaleTerritory Domain Model (rebuilt with SaleTerritory.load). There is no
 * Domain → Entity direction: the adapter writes a territory with one upsert statement.
 */
@Component
public class SaleTerritoryEntityMapper {

    public SaleTerritory toDomain(SaleTerritoryEntity entity) {
        if (entity == null) return null;

        return SaleTerritory.load(entity.getWardCode(), entity.getUserId(), entity.getAssignedBy(),
                entity.getAssignedAt());
    }
}
