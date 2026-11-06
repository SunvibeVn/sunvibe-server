package vn.sunvibe.property.core.application.usecase;

import vn.sunvibe.property.core.domain.model.property.Property;
import vn.sunvibe.property.core.domain.model.property.vo.PropertyId;

public interface PropertyFindByIdUseCase {
    Property execute(PropertyId propertyId);
}
