package vn.sunvibe.listing.core.testsupport;

import vn.sunvibe.listing.core.domain.model.listing.ListingAccessLog;
import vn.sunvibe.listing.core.domain.repository.ListingAccessLogDomainRepository;

import java.util.ArrayList;
import java.util.List;

/** In-memory access log for the service tests: keeps each row recorded, in order. */
public class InMemoryListingAccessLogRepository implements ListingAccessLogDomainRepository {

    private final List<ListingAccessLog> recorded = new ArrayList<>();

    @Override
    public void record(ListingAccessLog log) {
        recorded.add(log);
    }

    /** The rows recorded, in order. */
    public List<ListingAccessLog> recorded() {
        return List.copyOf(recorded);
    }
}
