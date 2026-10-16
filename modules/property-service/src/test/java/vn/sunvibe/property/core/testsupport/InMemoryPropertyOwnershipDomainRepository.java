package vn.sunvibe.property.core.testsupport;

import vn.sunvibe.property.core.domain.model.ownership.PropertyOwnership;
import vn.sunvibe.property.core.domain.repository.PropertyOwnershipDomainRepository;

import java.util.ArrayList;
import java.util.List;

/** Keeps saved ownerships in a list. The repository only declares save in W8. */
public class InMemoryPropertyOwnershipDomainRepository implements PropertyOwnershipDomainRepository {

    private final List<PropertyOwnership> saved = new ArrayList<>();

    public List<PropertyOwnership> saved() {
        return saved;
    }

    @Override
    public void save(PropertyOwnership ownership) {
        saved.add(ownership);
    }
}
