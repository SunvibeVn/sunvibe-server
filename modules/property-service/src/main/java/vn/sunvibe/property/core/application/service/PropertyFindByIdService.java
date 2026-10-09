package vn.sunvibe.property.core.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.application.usecase.PropertyFindByIdUseCase;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;

@Service
@RequiredArgsConstructor
public class PropertyFindByIdService implements PropertyFindByIdUseCase {

    private final PropertyDomainRepository propertyDomainRepository;

    @Override
    public Property execute(PropertyId propertyId) {
        return propertyDomainRepository.findById(propertyId).get();
    }
}
