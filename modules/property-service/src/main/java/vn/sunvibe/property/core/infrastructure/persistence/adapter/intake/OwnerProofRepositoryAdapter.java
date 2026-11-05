package vn.sunvibe.property.core.infrastructure.persistence.adapter.intake;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.intake.OwnerProof;
import vn.sunvibe.property.core.domain.repository.OwnerProofDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.OwnerProofEntity;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.IntakeEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.intake.OwnerProofJpaRepository;

import java.util.List;
import java.util.Optional;

/** Repository Implementation - owner proofs. As for intake rows, no entity stays managed. */
@Service
@RequiredArgsConstructor
public class OwnerProofRepositoryAdapter implements OwnerProofDomainRepository {

    /** One statement, so parallel wrong entries all count (spec S-8); a phrase past the limit is ended by its count. */
    private static final String COUNT_WRONG_PHRASE = """
            update inventory_owner_proofs
               set phrase_failed_attempts = phrase_failed_attempts + 1
             where id = ? and phrase_hash is not null and phrase_failed_attempts < ?
            returning phrase_failed_attempts
            """;

    private final OwnerProofJpaRepository jpaRepository;
    private final IntakeEntityMapper mapper;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void save(OwnerProof proof) {
        entityManager.detach(jpaRepository.saveAndFlush(mapper.toEntity(proof)));
    }

    @Override
    public Optional<OwnerProof> findById(String id) {
        return jpaRepository.findById(id).map(this::read);
    }

    @Override
    public Optional<OwnerProof> lockById(String id) {
        return jpaRepository.lockForUpdate(id).flatMap(jpaRepository::findById).map(this::read);
    }

    @Override
    public Optional<OwnerProof> findInForce(String intakeId) {
        return jpaRepository.findByIntakeIdAndWithdrawnAtIsNullAndVoidedAtIsNull(intakeId).map(this::read);
    }

    @Override
    public Optional<OwnerProof> findByRequestId(String requestId) {
        return jpaRepository.findByRequestId(requestId).map(this::read);
    }

    @Override
    public List<OwnerProof> findByLeadId(String leadId) {
        return jpaRepository.findByLeadIdOrderByConfirmedAtAscIdAsc(leadId).stream().map(this::read).toList();
    }

    @Override
    public List<OwnerProof> findByUserId(String userId) {
        return jpaRepository.findByUserIdOrderByConfirmedAtAscIdAsc(userId).stream().map(this::read).toList();
    }

    @Override
    public List<OwnerProof> findByRequestCreator(String creatorUserId) {
        return jpaRepository.findByRequestCreator(creatorUserId).stream().map(this::read).toList();
    }

    @Override
    public int countWrongPhrase(String id, int limit) {
        List<Integer> attempts = jdbc.queryForList(COUNT_WRONG_PHRASE, Integer.class, id, limit);
        return attempts.isEmpty() ? 0 : attempts.getFirst();
    }

    private OwnerProof read(OwnerProofEntity entity) {
        entityManager.detach(entity);
        return mapper.toDomain(entity);
    }
}
