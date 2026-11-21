package vn.sunvibe.listing.core.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.listing.core.domain.model.listing.ListingAccessLog;
import vn.sunvibe.listing.core.domain.repository.ListingAccessLogDomainRepository;
import vn.sunvibe.listing.core.infrastructure.persistence.entity.ListingAccessLogEntity;
import vn.sunvibe.listing.core.infrastructure.persistence.mapper.manual.ListingEntityMapper;

/**
 * Repository Implementation - the access log of listings' private data (listing-publish E06-S4 spec E06S4-D12, D18): one
 * insert by {@code persist}, so no merge SELECT precedes it, flushed in the caller's transaction; the entity detached.
 */
@Service
@RequiredArgsConstructor
public class ListingAccessLogRepositoryAdapter implements ListingAccessLogDomainRepository {

    private final ListingEntityMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void record(ListingAccessLog log) {
        ListingAccessLogEntity row = mapper.toAccessLogEntity(log);
        entityManager.persist(row);
        entityManager.flush();
        entityManager.detach(row);
    }
}
