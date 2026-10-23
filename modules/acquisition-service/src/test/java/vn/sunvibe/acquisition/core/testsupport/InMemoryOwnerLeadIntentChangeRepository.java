package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntentChange;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadIntentChangeDomainRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hand-written fake of the requirement change history; keeps changes in the order they were saved. */
public class InMemoryOwnerLeadIntentChangeRepository implements OwnerLeadIntentChangeDomainRepository {

    private final List<OwnerLeadIntentChange> changes = new ArrayList<>();

    public List<OwnerLeadIntentChange> all() {
        return new ArrayList<>(changes);
    }

    @Override
    public void save(OwnerLeadIntentChange change) {
        changes.add(change);
    }

    @Override
    public List<OwnerLeadIntentChange> findByLeadIdOrderByOccurredAt(OwnerLeadId leadId) {
        return changes.stream()
                .filter(change -> change.getLeadId().equals(leadId))
                .sorted(Comparator.comparing(OwnerLeadIntentChange::getOccurredAt)
                        .thenComparing(OwnerLeadIntentChange::getId))
                .toList();
    }
}
