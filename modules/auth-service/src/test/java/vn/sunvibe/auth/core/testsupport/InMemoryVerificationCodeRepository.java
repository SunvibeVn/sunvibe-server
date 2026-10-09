package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.repository.auth.VerificationCodeRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-written fake; stores copies like a database. */
public class InMemoryVerificationCodeRepository implements VerificationCodeRepository {

    private final Map<Long, VerificationCode> codes = new LinkedHashMap<>();

    @Override
    public void save(VerificationCode code) {
        codes.put(code.getId(), copy(code, code.getConsumedAt()));
    }

    @Override
    public Optional<VerificationCode> findLatestForUpdate(String userId, VerificationChannel channel) {
        return codes.values().stream()
                .filter(c -> c.getUserId().equals(userId) && c.getChannel() == channel)
                .max(Comparator.comparing(VerificationCode::getCreatedAt).thenComparing(VerificationCode::getId))
                .map(c -> copy(c, c.getConsumedAt()));
    }

    @Override
    public void invalidateActive(String userId, VerificationChannel channel, Instant now) {
        codes.replaceAll((id, c) -> c.getUserId().equals(userId) && c.getChannel() == channel && c.getConsumedAt() == null
                ? copy(c, now) : c);
    }

    @Override
    public long countIssuedSince(String userId, VerificationChannel channel, Instant since) {
        return codes.values().stream()
                .filter(c -> c.getUserId().equals(userId) && c.getChannel() == channel && c.getCreatedAt().isAfter(since))
                .count();
    }

    public List<VerificationCode> all() {
        return List.copyOf(codes.values());
    }

    /** Moves a code back in time (cooldown / daily window tests). */
    public void age(Long id, java.time.Duration by) {
        VerificationCode c = codes.get(id);
        codes.put(id, VerificationCode.load(c.getId(), c.getUserId(), c.getChannel(), c.getDestination(), c.getCodeHash(),
                c.getCreatedAt().minus(by), c.getExpiresAt().minus(by), c.getFailedAttempts(), c.getConsumedAt()));
    }

    private static VerificationCode copy(VerificationCode c, Instant consumedAt) {
        return VerificationCode.load(c.getId(), c.getUserId(), c.getChannel(), c.getDestination(), c.getCodeHash(),
                c.getCreatedAt(), c.getExpiresAt(), c.getFailedAttempts(), consumedAt);
    }
}
