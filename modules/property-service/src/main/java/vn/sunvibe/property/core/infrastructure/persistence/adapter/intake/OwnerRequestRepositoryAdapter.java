package vn.sunvibe.property.core.infrastructure.persistence.adapter.intake;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.intake.OwnerRequest;
import vn.sunvibe.property.core.domain.repository.OwnerRequestDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.intake.OwnerRequestEntity;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.IntakeEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.intake.OwnerRequestJpaRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Repository Implementation - owner requests. As for intake rows, no entity stays managed. */
@Service
@RequiredArgsConstructor
public class OwnerRequestRepositoryAdapter implements OwnerRequestDomainRepository {

    /** One statement, so parallel wrong codes all count (spec S-5); the row at the limit voids and drops its snapshot. */
    private static final String COUNT_WRONG_CODE = """
            update inventory_owner_requests
               set failed_attempts = failed_attempts + 1,
                   voided_at = case when failed_attempts + 1 >= ? then ? else voided_at end,
                   display_snapshot = case when failed_attempts + 1 >= ? then null else display_snapshot end
             where id = ? and consumed_at is null and voided_at is null and declined_at is null
            returning failed_attempts
            """;

    private final OwnerRequestJpaRepository jpaRepository;
    private final IntakeEntityMapper mapper;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void save(OwnerRequest request) {
        entityManager.detach(jpaRepository.saveAndFlush(mapper.toEntity(request)));
    }

    @Override
    public Optional<OwnerRequest> findById(String id) {
        return jpaRepository.findById(id).map(this::read);
    }

    @Override
    public Optional<OwnerRequest> lockById(String id) {
        return jpaRepository.lockForUpdate(id).flatMap(jpaRepository::findById).map(this::read);
    }

    @Override
    public List<OwnerRequest> findByIntakeId(String intakeId) {
        return jpaRepository.findByIntakeIdOrderByCreatedAtAscIdAsc(intakeId).stream().map(this::read).toList();
    }

    @Override
    public List<OwnerRequest> findByTargetUser(String userId) {
        return jpaRepository.findByTargetUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(this::read).toList();
    }

    @Override
    public long countCreatedSince(String intakeId, Instant since) {
        return jpaRepository.countByIntakeIdAndCreatedAtGreaterThanEqual(intakeId, since);
    }

    @Override
    public int countWrongCode(String id, int limit, Instant now) {
        List<Integer> attempts = jdbc.queryForList(COUNT_WRONG_CODE, Integer.class, limit, Timestamp.from(now), limit, id);
        return attempts.isEmpty() ? 0 : attempts.getFirst();
    }

    private OwnerRequest read(OwnerRequestEntity entity) {
        entityManager.detach(entity);
        return mapper.toDomain(entity);
    }
}
