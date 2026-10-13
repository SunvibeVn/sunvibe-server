package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationPhoneChallenge;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationPhoneChallengeRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.RegistrationPhoneChallengeEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.RegistrationPhoneChallengeJpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA implementation of {@link RegistrationPhoneChallengeRepository}.
 */
@Service
@RequiredArgsConstructor
public class RegistrationPhoneChallengeRepositoryAdapter implements RegistrationPhoneChallengeRepository {

    private final RegistrationPhoneChallengeJpaRepository jpaRepository;

    @Override
    public void save(RegistrationPhoneChallenge challenge) {
        RegistrationPhoneChallengeEntity entity = new RegistrationPhoneChallengeEntity();
        entity.setId(challenge.getId());
        entity.setPhone(challenge.getPhone());
        entity.setCodeHash(challenge.getCodeHash());
        entity.setCreatedAt(challenge.getCreatedAt());
        entity.setExpiresAt(challenge.getExpiresAt());
        entity.setFailedAttempts(challenge.getFailedAttempts());
        entity.setConsumedAt(challenge.getConsumedAt());
        entity.setVerifiedAt(challenge.getVerifiedAt());
        entity.setTokenHash(challenge.getTokenHash());
        entity.setTokenExpiresAt(challenge.getTokenExpiresAt());
        entity.setRegisteredAt(challenge.getRegisteredAt());
        jpaRepository.save(entity);
    }

    @Override
    public Optional<RegistrationPhoneChallenge> findLatestForUpdate(String phone) {
        return jpaRepository.findLatestForUpdate(phone, Limit.of(1)).stream().findFirst().map(this::toDomain);
    }

    @Override
    public Optional<RegistrationPhoneChallenge> findByTokenHashForUpdate(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public void invalidateActive(String phone, Instant now) {
        jpaRepository.invalidateActiveCodes(phone, now);
        jpaRepository.revokeActiveTokens(phone, now);
    }

    @Override
    public long countIssuedSince(String phone, Instant since) {
        return jpaRepository.countByPhoneAndCreatedAtAfter(phone, since);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return jpaRepository.deleteExpiredBefore(cutoff);
    }

    private RegistrationPhoneChallenge toDomain(RegistrationPhoneChallengeEntity e) {
        return RegistrationPhoneChallenge.load(e.getId(), e.getPhone(), e.getCodeHash(), e.getCreatedAt(),
                e.getExpiresAt(), e.getFailedAttempts(), e.getConsumedAt(), e.getVerifiedAt(), e.getTokenHash(),
                e.getTokenExpiresAt(), e.getRegisteredAt());
    }
}
