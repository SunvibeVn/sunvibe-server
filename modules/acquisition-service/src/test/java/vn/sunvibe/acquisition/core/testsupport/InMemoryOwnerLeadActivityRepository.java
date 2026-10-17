package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hand-written fake of the lead history; keeps activities in the order they were saved. */
public class InMemoryOwnerLeadActivityRepository implements OwnerLeadActivityDomainRepository {

    private final List<OwnerLeadActivity> activities = new ArrayList<>();

    public List<OwnerLeadActivity> all() {
        return new ArrayList<>(activities);
    }

    @Override
    public void save(OwnerLeadActivity activity) {
        activities.add(activity);
    }

    @Override
    public List<OwnerLeadActivity> findByLeadIdOrderByOccurredAt(OwnerLeadId leadId) {
        return activities.stream()
                .filter(activity -> activity.getLeadId().equals(leadId))
                .sorted(Comparator.comparing(OwnerLeadActivity::getOccurredAt).thenComparing(OwnerLeadActivity::getId))
                .toList();
    }
}
