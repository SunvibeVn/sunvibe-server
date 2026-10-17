package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;
import vn.sunvibe.acquisition.core.domain.repository.IdempotencyKeyDomainRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Hand-written fake of the idempotency keys, keyed by the key text. */
public class InMemoryIdempotencyKeyRepository implements IdempotencyKeyDomainRepository {

    private final Map<String, IdempotencyRecord> records = new LinkedHashMap<>();

    public int size() {
        return records.size();
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        return Optional.ofNullable(records.get(key));
    }

    @Override
    public void save(IdempotencyRecord record) {
        records.put(record.key(), record);
    }

    @Override
    public void deleteByKey(String key) {
        records.remove(key);
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        int before = records.size();
        records.values().removeIf(record -> record.createdAt().isBefore(cutoff));
        return before - records.size();
    }
}
