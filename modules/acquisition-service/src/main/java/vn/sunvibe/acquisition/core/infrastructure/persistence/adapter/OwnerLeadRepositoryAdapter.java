package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.repository.LeadSearchCriteria;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.PageResult;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadEntity;
import vn.sunvibe.acquisition.core.infrastructure.persistence.mapper.manual.OwnerLeadEntityMapper;
import vn.sunvibe.acquisition.core.infrastructure.persistence.repository.OwnerLeadJpaRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Repository Implementation - OwnerLeadRepositoryAdapter
 * <p>
 * Implements the domain OwnerLeadDomainRepository: maps the OwnerLead domain model to OwnerLeadEntity. A lead with a
 * version is saved as an update guarded by that version, so a stale version fails with an optimistic locking error.
 * After a save the aggregate carries the version the database now holds.
 */
@Service
@RequiredArgsConstructor
public class OwnerLeadRepositoryAdapter implements OwnerLeadDomainRepository {

    private final OwnerLeadJpaRepository jpaRepository;
    private final OwnerLeadEntityMapper mapper;

    @Override
    public void save(OwnerLead lead) {
        if (lead == null) {
            throw new IllegalArgumentException("OwnerLead cannot be null");
        }
        // Flush now so the version Hibernate set or bumped is known and can be given back to the aggregate
        OwnerLeadEntity saved = jpaRepository.saveAndFlush(mapper.toEntity(lead));
        lead.markPersisted(saved.getVersion());
    }

    @Override
    public Optional<OwnerLead> findById(OwnerLeadId id) {
        return jpaRepository.findById(id.getValue()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByRequestCode(String requestCode) {
        return jpaRepository.existsByRequestCode(requestCode);
    }

    @Override
    public Optional<OwnerLead> findRecentSameSubmission(String phoneNormalized, OwnerLeadIntent intent,
                                                        String wardCode, String addressKey,
                                                        Instant createdAtOrAfter) {
        return jpaRepository
                .findFirstByPhoneNormalizedAndIntentAndWardCodeAndAddressKeyAndCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
                        phoneNormalized, intent, wardCode, addressKey, createdAtOrAfter)
                .map(mapper::toDomain);
    }

    @Override
    public List<OwnerLeadId> findOpenIdsByPhone(String phoneNormalized) {
        return jpaRepository.findIdsByPhoneNormalizedAndStatusIn(phoneNormalized, openStatuses()).stream()
                .map(OwnerLeadId::of)
                .toList();
    }

    @Override
    public PageResult<OwnerLead> search(LeadSearchCriteria criteria, int page, int size) {
        Specification<OwnerLeadEntity> filters = Specification.unrestricted();
        if (criteria.assignedUserId().isPresent()) {
            String assignedUserId = criteria.assignedUserId().get();
            filters = filters.and((root, query, cb) -> cb.equal(root.get("assignedUserId"), assignedUserId));
        }
        if (!criteria.statuses().isEmpty()) {
            filters = filters.and((root, query, cb) -> root.get("status").in(criteria.statuses()));
        }
        if (criteria.wardCode().isPresent()) {
            String wardCode = criteria.wardCode().get();
            filters = filters.and((root, query, cb) -> cb.equal(root.get("wardCode"), wardCode));
        }
        if (criteria.requestCode().isPresent()) {
            String requestCode = criteria.requestCode().get();
            filters = filters.and((root, query, cb) -> cb.equal(root.get("requestCode"), requestCode));
        }
        // The id breaks the tie between leads created at the same instant, so the pages never overlap
        Page<OwnerLeadEntity> found = jpaRepository.findAll(filters,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PageResult<>(found.getContent().stream().map(mapper::toDomain).toList(), page, size,
                found.getTotalElements());
    }

    private static List<OwnerLeadStatus> openStatuses() {
        return Arrays.stream(OwnerLeadStatus.values())
                .filter(OwnerLeadStatus::isOpen)
                .toList();
    }
}
