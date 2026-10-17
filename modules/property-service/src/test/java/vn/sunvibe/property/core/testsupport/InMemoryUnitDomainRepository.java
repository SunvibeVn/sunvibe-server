package vn.sunvibe.property.core.testsupport;

import vn.sunvibe.property.core.domain.model.unit.Unit;
import vn.sunvibe.property.core.domain.repository.UnitDomainRepository;

import java.util.ArrayList;
import java.util.List;

/** Keeps saved units in a list. The repository only declares save. */
public class InMemoryUnitDomainRepository implements UnitDomainRepository {

    private final List<Unit> saved = new ArrayList<>();

    public List<Unit> saved() {
        return saved;
    }

    @Override
    public void save(Unit unit) {
        saved.add(unit);
    }
}
