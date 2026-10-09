package vn.sunvibe.property.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.repository.PropertyJpaRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.PropertyEntityMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository Implementation - PropertyRepositoryAdapter
 * <p>
 * This is the implementation of the domain PropertyDomainRepository.
 * It maps between domain models (Property) and JPA entities (PropertyEntity).
 * <p>
 * Note: This is the only place in the infrastructure layer where entities are visible.
 */
@Service
@RequiredArgsConstructor
public class PropertyRepositoryAdapter implements PropertyDomainRepository {

    private final PropertyJpaRepository jpaRepository;
    private final PropertyEntityMapper mapper;

    @Override
    public void save(Property property) {
        if (property == null) {
            throw new IllegalArgumentException("Property cannot be null");
        }
        var entity = mapper.toEntity(property);
        jpaRepository.save(entity);
    }

    @Override
    public void saveAll(List<Property> properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        var entities = properties.stream()
                .map(mapper::toEntity)
                .collect(Collectors.toList());
        jpaRepository.saveAll(entities);
    }

    @Override
    public Optional<Property> findById(PropertyId id) {
        if (id == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        return jpaRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Property> findByCode(PropertyCode code) {
        if (code == null) {
            throw new IllegalArgumentException("PropertyCode cannot be null");
        }
        return jpaRepository.findByPropertyCode(code.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public List<Property> findByOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("OwnerId cannot be null or blank");
        }
        return jpaRepository.findByOwnerId(ownerId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Property> findByStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be null or blank");
        }
        return jpaRepository.findByStatus(status).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Property> findAvailableByDistrict(String district) {
        if (district == null || district.isBlank()) {
            throw new IllegalArgumentException("District cannot be null or blank");
        }
        return jpaRepository.findByDistrictAndStatus(district, "AVAILABLE").stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Property> findByPriceRange(long minPrice, long maxPrice) {
        if (minPrice < 0 || maxPrice < 0 || minPrice > maxPrice) {
            throw new IllegalArgumentException("Invalid price range");
        }
        return jpaRepository.findByPriceRange(
                        BigDecimal.valueOf(minPrice),
                        BigDecimal.valueOf(maxPrice)
                ).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Property> findByBedrooms(Integer bedrooms) {
        if (bedrooms == null || bedrooms <= 0) {
            throw new IllegalArgumentException("Bedrooms must be > 0");
        }
        return jpaRepository.findByBedrooms(bedrooms).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(PropertyId id) {
        if (id == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    public boolean existsById(PropertyId id) {
        if (id == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        return jpaRepository.existsById(id.getValue());
    }

    @Override
    public boolean existsByCode(PropertyCode code) {
        if (code == null) {
            throw new IllegalArgumentException("PropertyCode cannot be null");
        }
        return jpaRepository.existsByPropertyCode(code.getValue());
    }

    @Override
    public List<Property> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public long countByOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("OwnerId cannot be null or blank");
        }
        return jpaRepository.countByOwnerId(ownerId);
    }

    @Override
    public long countByStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be null or blank");
        }
        return jpaRepository.countByStatus(status);
    }
}
