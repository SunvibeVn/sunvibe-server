package vn.sunvibe.property.core.application.usecase;

import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;

public interface PropertyFindByIdUseCase {
    Property execute(PropertyId propertyId);
}
