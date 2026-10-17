package vn.sunvibe.property.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.property.core.domain.model.area.Province;
import vn.sunvibe.property.core.domain.model.area.Ward;
import vn.sunvibe.property.core.domain.model.area.WardType;
import vn.sunvibe.property.core.infrastructure.persistence.entity.ProvinceEntity;
import vn.sunvibe.property.core.infrastructure.persistence.entity.WardEntity;

/** Entity to domain for the catalog; it is read-only, so there is no domain to entity direction. */
@Component
public class AdministrativeAreaEntityMapper {

    public Province toDomain(ProvinceEntity entity) {
        if (entity == null) return null;
        return Province.load(entity.getCode(), entity.getName());
    }

    public Ward toDomain(WardEntity entity) {
        if (entity == null) return null;
        return Ward.load(entity.getCode(), entity.getProvinceCode(), entity.getName(),
                WardType.valueOf(entity.getType()));
    }
}
