package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.repository.auth.VerificationCodeRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.VerificationCodeEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.auth.VerificationCodeJpaRepository;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VerificationCodeRepositoryAdapter implements VerificationCodeRepository {

    private final VerificationCodeJpaRepository jpaRepository;

    @Override
    public void save(VerificationCode code) {
        VerificationCodeEntity entity = new VerificationCodeEntity();
        entity.setId(code.getId());
        entity.setUserId(code.getUserId());
        entity.setChannel(code.getChannel());
        entity.setDestination(code.getDestination());
        entity.setCodeHash(code.getCodeHash());
        entity.setCreatedAt(code.getCreatedAt());
        entity.setExpiresAt(code.getExpiresAt());
        entity.setFailedAttempts(code.getFailedAttempts());
        entity.setConsumedAt(code.getConsumedAt());
        jpaRepository.save(entity);
    }

    @Override
    public Optional<VerificationCode> findLatestForUpdate(String userId, VerificationChannel channel) {
        return jpaRepository.findLatestForUpdate(userId, channel, Limit.of(1)).stream().findFirst()
                .map(e -> VerificationCode.load(e.getId(), e.getUserId(), e.getChannel(), e.getDestination(),
                        e.getCodeHash(), e.getCreatedAt(), e.getExpiresAt(), e.getFailedAttempts(), e.getConsumedAt()));
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
