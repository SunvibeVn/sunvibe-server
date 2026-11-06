package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationEmailChallenge;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationEmailChallengeRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.support.AuthConstraintViolations;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.RegistrationEmailChallengeEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.RegistrationEmailChallengeJpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA implementation of {@link RegistrationEmailChallengeRepository}.
 */
@Service
@RequiredArgsConstructor
public class RegistrationEmailChallengeRepositoryAdapter implements RegistrationEmailChallengeRepository {

    private static final String ACTIVE_INDEX = "uq_registration_email_challenges_active";

    private final RegistrationEmailChallengeJpaRepository jpaRepository;

    @Override
    public boolean insert(RegistrationEmailChallenge challenge) {
        try {
            jpaRepository.saveAndFlush(toEntity(challenge));
            return true;
        } catch (DataIntegrityViolationException e) {
            if (ACTIVE_INDEX.equals(AuthConstraintViolations.constraintName(e).orElse(null))) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void save(RegistrationEmailChallenge challenge) {
        jpaRepository.save(toEntity(challenge));
    }

    @Override
    public Optional<RegistrationEmailChallenge> findLatest(String emailKey) {
        return jpaRepository.findFirstByEmailKeyOrderByCreatedAtDescIdDesc(emailKey).map(this::toDomain);
    }

    @Override
    public Optional<RegistrationEmailChallenge> findLatestForUpdate(String emailKey) {
        return jpaRepository.findLatestForUpdate(emailKey, Limit.of(1)).stream().findFirst().map(this::toDomain);
    }

    @Override
    public Optional<RegistrationEmailChallenge> findByTokenHashForUpdate(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public void burnActiveCodes(String emailKey, Instant now) {
        jpaRepository.burnActiveCodes(emailKey, now);
    }

    @Override
    public long countIssuedSince(String emailKey, Instant since) {
        return jpaRepository.countByEmailKeyAndCreatedAtAfter(emailKey, since);
    }

    @Override
    public int deleteExpiredBatch(Instant cutoff, int limit) {
        return jpaRepository.deleteExpiredBatch(cutoff, limit);
    }

    private RegistrationEmailChallengeEntity toEntity(RegistrationEmailChallenge challenge) {
        RegistrationEmailChallengeEntity entity = new RegistrationEmailChallengeEntity();
        entity.setId(challenge.getId());
        entity.setEmailKey(challenge.getEmailKey());
        entity.setCodeHash(challenge.getCodeHash());
        entity.setCreatedAt(challenge.getCreatedAt());
        entity.setExpiresAt(challenge.getExpiresAt());
        entity.setFailedAttempts(challenge.getFailedAttempts());
        entity.setConsumedAt(challenge.getConsumedAt());
        entity.setVerifiedAt(challenge.getVerifiedAt());
        entity.setTokenHash(challenge.getTokenHash());
        entity.setTokenExpiresAt(challenge.getTokenExpiresAt());
        entity.setRegisteredAt(challenge.getRegisteredAt());
        return entity;
    }

    private RegistrationEmailChallenge toDomain(RegistrationEmailChallengeEntity e) {
        return RegistrationEmailChallenge.load(e.getId(), e.getEmailKey(), e.getCodeHash(), e.getCreatedAt(),
                e.getExpiresAt(), e.getFailedAttempts(), e.getConsumedAt(), e.getVerifiedAt(), e.getTokenHash(),
                e.getTokenExpiresAt(), e.getRegisteredAt());
    }
}
