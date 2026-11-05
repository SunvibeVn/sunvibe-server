package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationPurpose;
import vn.sunvibe.auth.core.domain.repository.auth.VerificationCodeRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.VerificationCodeEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.VerificationCodeJpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA implementation of {@link VerificationCodeRepository}.
 */
@Service
@RequiredArgsConstructor
public class VerificationCodeRepositoryAdapter implements VerificationCodeRepository {

    private final VerificationCodeJpaRepository jpaRepository;

    @Override
    public void save(VerificationCode code) {
        jpaRepository.save(toEntity(code));
    }

    @Override
    public void insert(VerificationCode code) {
        jpaRepository.saveAndFlush(toEntity(code));
    }

    @Override
    public Optional<VerificationCode> findLatestForUpdate(String userId, VerificationChannel channel) {
        return jpaRepository.findLatestForUpdate(userId, channel, Limit.of(1)).stream().findFirst().map(this::toDomain);
    }

    @Override
    public Optional<VerificationCode> findLatestBind(String userId) {
        return jpaRepository.findLatestByPurpose(userId, VerificationPurpose.EMAIL_BIND, Limit.of(1)).stream().findFirst()
                .map(this::toDomain);
    }

    @Override
    public Optional<VerificationCode> findLatestBindForUpdate(String userId) {
        return jpaRepository.findLatestByPurposeForUpdate(userId, VerificationPurpose.EMAIL_BIND, Limit.of(1)).stream()
                .findFirst().map(this::toDomain);
    }

    @Override
    public void burnOtherBindCodes(String userId, Long keptId, Instant now) {
        jpaRepository.burnOthers(userId, VerificationPurpose.EMAIL_BIND, keptId, now);
    }

    @Override
    public long countBindIssuedSince(String userId, Instant since) {
        return jpaRepository.countByUserIdAndPurposeAndCreatedAtAfter(userId, VerificationPurpose.EMAIL_BIND, since);
    }

    @Override
    public long countBindIssuedToDestinationSince(String destination, Instant since) {
        return jpaRepository.countByDestinationAndPurposeAndCreatedAtAfter(destination, VerificationPurpose.EMAIL_BIND, since);
    }

    @Override
    public int deleteExpiredBatch(Instant cutoff, int limit) {
        return jpaRepository.deleteExpiredBatch(cutoff, limit);
    }

    private VerificationCodeEntity toEntity(VerificationCode code) {
        VerificationCodeEntity entity = new VerificationCodeEntity();
        entity.setId(code.getId());
        entity.setUserId(code.getUserId());
        entity.setChannel(code.getChannel());
        entity.setPurpose(code.getPurpose());
        entity.setDestination(code.getDestination());
        entity.setCodeHash(code.getCodeHash());
        entity.setCreatedAt(code.getCreatedAt());
        entity.setExpiresAt(code.getExpiresAt());
        entity.setFailedAttempts(code.getFailedAttempts());
        entity.setConsumedAt(code.getConsumedAt());
        entity.setCredentialVersion(code.getCredentialVersion());
        return entity;
    }

    private VerificationCode toDomain(VerificationCodeEntity e) {
        return VerificationCode.load(e.getId(), e.getUserId(), e.getChannel(), e.getPurpose(), e.getDestination(),
                e.getCodeHash(), e.getCreatedAt(), e.getExpiresAt(), e.getFailedAttempts(), e.getConsumedAt(),
                e.getCredentialVersion());
    }

    @Override
    public void invalidateActive(String userId, VerificationChannel channel, Instant now) {
        jpaRepository.invalidateActive(userId, channel, now);
    }

    @Override
    public long countIssuedSince(String userId, VerificationChannel channel, Instant since) {
        return jpaRepository.countByUserIdAndChannelAndCreatedAtAfter(userId, channel, since);
    }
}
