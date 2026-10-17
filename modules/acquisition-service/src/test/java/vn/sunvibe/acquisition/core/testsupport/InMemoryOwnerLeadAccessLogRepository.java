package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.log.OwnerLeadAccessLog;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadAccessLogDomainRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hand-written fake of the lead access log; keeps entries in the order they were saved. */
public class InMemoryOwnerLeadAccessLogRepository implements OwnerLeadAccessLogDomainRepository {

    private final List<OwnerLeadAccessLog> logs = new ArrayList<>();

    public List<OwnerLeadAccessLog> all() {
        return new ArrayList<>(logs);
    }

    @Override
    public void save(OwnerLeadAccessLog log) {
        logs.add(log);
    }

    @Override
    public List<OwnerLeadAccessLog> findByLeadId(OwnerLeadId id) {
        return logs.stream()
                .filter(log -> log.getLeadId().equals(id))
                .sorted(Comparator.comparing(OwnerLeadAccessLog::getAccessedAt).thenComparing(OwnerLeadAccessLog::getId))
                .toList();
    }
}
