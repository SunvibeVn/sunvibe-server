package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivity;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Hand-written fake of the lead history; keeps activities in the order they were saved. */
public class InMemoryOwnerLeadActivityRepository implements OwnerLeadActivityDomainRepository {

    private final List<OwnerLeadActivity> activities = new ArrayList<>();
    private final List<List<String>> latestLookups = new ArrayList<>();

    public List<OwnerLeadActivity> all() {
        return new ArrayList<>(activities);
    }

    /** The lead ids of each {@link #findLatestOccurredAt} call, in call order. */
    public List<List<String>> latestLookups() {
        return new ArrayList<>(latestLookups);
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

    @Override
    public Map<String, Instant> findLatestOccurredAt(Collection<String> leadIds) {
        latestLookups.add(List.copyOf(leadIds));
        return activities.stream()
                .filter(activity -> leadIds.contains(activity.getLeadId().getValue()))
                .collect(Collectors.toMap(activity -> activity.getLeadId().getValue(), OwnerLeadActivity::getOccurredAt,
                        (left, right) -> left.isAfter(right) ? left : right));
    }
}
