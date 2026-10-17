package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecordId;
import vn.sunvibe.acquisition.core.domain.repository.ConsentRecordDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.ConsentRecordEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.repository.ConsentRecordJpaRepository;

import java.util.Optional;

/**
 * Repository Implementation - ConsentRecordRepositoryAdapter
 * <p>
 * Implements the domain ConsentRecordDomainRepository: maps the ConsentRecord domain model to ConsentRecordEntity.
 */
@Service
@RequiredArgsConstructor
public class ConsentRecordRepositoryAdapter implements ConsentRecordDomainRepository {

    private final ConsentRecordJpaRepository jpaRepository;
    private final ConsentRecordEntityMapper mapper;

    @Override
    public void save(ConsentRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("ConsentRecord cannot be null");
        }
        jpaRepository.save(mapper.toEntity(record));
    }

    @Override
    public Optional<ConsentRecord> findById(ConsentRecordId id) {
        return jpaRepository.findById(id.getValue()).map(mapper::toDomain);
    }
}
