package vn.sunvibe.property.core.testsupport;

import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Keeps saved properties in a list. Only the operations the create flow uses are supported. */
public class InMemoryPropertyDomainRepository implements PropertyDomainRepository {

    private final List<Property> saved = new ArrayList<>();

    public List<Property> saved() {
        return saved;
    }

    @Override
    public void save(Property property) {
        saved.add(property);
    }

    @Override
    public boolean existsByCode(PropertyCode code) {
        return saved.stream().anyMatch(p -> p.getPropertyCode().equals(code));
    }

    @Override
    public Optional<Property> findById(PropertyId id) {
        return saved.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    @Override
    public void saveAll(List<Property> properties) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Property> findByCode(PropertyCode code) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Property> findByOwnerId(String ownerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Property> findByStatus(String status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Property> findAvailableByDistrict(String district) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Property> findByPriceRange(long minPrice, long maxPrice) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Property> findByBedrooms(Integer bedrooms) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteById(PropertyId id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsById(PropertyId id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Property> findAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long count() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long countByOwnerId(String ownerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long countByStatus(String status) {
        throw new UnsupportedOperationException();
    }
}
