package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.acquisition.core.domain.model.idempotency.IdempotencyRecord;
import vn.sunvibe.acquisition.core.domain.repository.IdempotencyKeyDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.IdempotencyKeyEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.repository.OwnerIntakeIdempotencyKeyJpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository Implementation - IdempotencyKeyRepositoryAdapter
 * <p>
 * Implements the domain IdempotencyKeyDomainRepository: maps the IdempotencyRecord to
 * OwnerIntakeIdempotencyKeyEntity.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyDomainRepository {

    private final OwnerIntakeIdempotencyKeyJpaRepository jpaRepository;
    private final IdempotencyKeyEntityMapper mapper;

    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        return jpaRepository.findById(key).map(mapper::toDomain);
    }

    @Override
    public void save(IdempotencyRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("IdempotencyRecord cannot be null");
        }
        jpaRepository.save(mapper.toEntity(record));
    }

    @Override
    public void deleteByKey(String key) {
        jpaRepository.deleteById(key);
    }

    // IdempotencyKeyCleanupService owns the transaction and this joins it; it stays so the bulk delete also works
    // when the adapter is called on its own (a JPQL delete fails without a transaction)
    @Transactional
    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return jpaRepository.deleteCreatedBefore(cutoff);
    }
}
