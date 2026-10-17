package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.repository.ConsentRecordDomainRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-written fake of the consent repository, keyed by consent id. */
public class InMemoryConsentRecordRepository implements ConsentRecordDomainRepository {

    private final Map<ConsentRecordId, ConsentRecord> records = new LinkedHashMap<>();

    public List<ConsentRecord> all() {
        return new ArrayList<>(records.values());
    }

    @Override
    public void save(ConsentRecord record) {
        records.put(record.getId(), record);
    }

    @Override
    public Optional<ConsentRecord> findById(ConsentRecordId id) {
        return Optional.ofNullable(records.get(id));
    }
}
