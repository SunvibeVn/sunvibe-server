package vn.sunvibe.property.core.testsupport;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vn.sunvibe.property.core.domain.exception.PropertyAlreadyExistsException;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps saved properties in a list. Like the real adapter, a save hands the saved version back to the property (0 for
 * a new one, one more for a stored one), refuses a stale version with {@link ObjectOptimisticLockingFailureException}
 * and a taken property code with {@link PropertyAlreadyExistsException}. Unlike the database it bumps the version on
 * every update, changed or not.
 */
public class InMemoryPropertyDomainRepository implements PropertyDomainRepository {

    private final List<Property> saved = new ArrayList<>();
    private final Map<PropertyId, Long> storedVersions = new HashMap<>();

    public List<Property> saved() {
        return saved;
    }

    @Override
    public void save(Property property) {
        Long stored = storedVersions.get(property.getId());
        if (stored == null) {
            if (existsByCode(property.getPropertyCode())) {
                throw new PropertyAlreadyExistsException("Property code already exists.");
            }
            saved.add(property);
            storedVersions.put(property.getId(), 0L);
            property.markPersisted(0L);
            return;
        }
        if (!stored.equals(property.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Property.class, property.getId().getValue());
        }
        saved.removeIf(p -> p.getId().equals(property.getId()));
        saved.add(property);
        storedVersions.put(property.getId(), stored + 1);
        property.markPersisted(stored + 1);
    }

    @Override
    public boolean existsByCode(PropertyCode code) {
        return saved.stream().anyMatch(p -> p.getPropertyCode().equals(code));
    }

    @Override
    public Optional<Property> findById(PropertyId id) {
        return saved.stream().filter(p -> p.getId().equals(id)).findFirst();
    }
}
