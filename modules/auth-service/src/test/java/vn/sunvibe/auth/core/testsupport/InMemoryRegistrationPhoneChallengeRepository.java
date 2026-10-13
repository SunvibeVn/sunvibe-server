package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.model.auth.RegistrationPhoneChallenge;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationPhoneChallengeRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-written fake; stores copies like a database. */
public class InMemoryRegistrationPhoneChallengeRepository implements RegistrationPhoneChallengeRepository {

    private final Map<Long, RegistrationPhoneChallenge> challenges = new LinkedHashMap<>();

    @Override
    public void save(RegistrationPhoneChallenge challenge) {
        challenges.put(challenge.getId(), copy(challenge));
    }

    @Override
    public Optional<RegistrationPhoneChallenge> findLatestForUpdate(String phone) {
        return challenges.values().stream()
                .filter(c -> c.getPhone().equals(phone))
                .max(Comparator.comparing(RegistrationPhoneChallenge::getCreatedAt)
                        .thenComparing(RegistrationPhoneChallenge::getId))
                .map(InMemoryRegistrationPhoneChallengeRepository::copy);
    }

    @Override
    public Optional<RegistrationPhoneChallenge> findByTokenHashForUpdate(String tokenHash) {
        return challenges.values().stream()
                .filter(c -> tokenHash.equals(c.getTokenHash()))
                .findFirst()
                .map(InMemoryRegistrationPhoneChallengeRepository::copy);
    }

    @Override
    public void invalidateActive(String phone, Instant now) {
        challenges.replaceAll((id, c) -> {
            if (!c.getPhone().equals(phone)) {
                return c;
            }
            Instant consumedAt = c.getConsumedAt() == null ? now : c.getConsumedAt();
            boolean revokeToken = c.getTokenHash() != null && c.getRegisteredAt() == null
                    && c.getTokenExpiresAt().isAfter(now);
            return RegistrationPhoneChallenge.load(c.getId(), c.getPhone(), c.getCodeHash(), c.getCreatedAt(),
                    c.getExpiresAt(), c.getFailedAttempts(), consumedAt, c.getVerifiedAt(), c.getTokenHash(),
                    revokeToken ? now : c.getTokenExpiresAt(), c.getRegisteredAt());
        });
    }

    @Override
    public long countIssuedSince(String phone, Instant since) {
        return challenges.values().stream()
                .filter(c -> c.getPhone().equals(phone) && c.getCreatedAt().isAfter(since))
                .count();
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        int before = challenges.size();
        challenges.values().removeIf(c -> c.getExpiresAt().isBefore(cutoff)
                && (c.getTokenExpiresAt() == null || c.getTokenExpiresAt().isBefore(cutoff)));
        return before - challenges.size();
    }

    public List<RegistrationPhoneChallenge> all() {
        return List.copyOf(challenges.values());
    }

    /** Moves a challenge back in time (cooldown / daily window tests). */
    public void age(Long id, Duration by) {
        RegistrationPhoneChallenge c = challenges.get(id);
        challenges.put(id, RegistrationPhoneChallenge.load(c.getId(), c.getPhone(), c.getCodeHash(),
                c.getCreatedAt().minus(by), c.getExpiresAt().minus(by), c.getFailedAttempts(), c.getConsumedAt(),
                c.getVerifiedAt(), c.getTokenHash(),
                c.getTokenExpiresAt() == null ? null : c.getTokenExpiresAt().minus(by), c.getRegisteredAt()));
    }

    private static RegistrationPhoneChallenge copy(RegistrationPhoneChallenge c) {
        return RegistrationPhoneChallenge.load(c.getId(), c.getPhone(), c.getCodeHash(), c.getCreatedAt(),
                c.getExpiresAt(), c.getFailedAttempts(), c.getConsumedAt(), c.getVerifiedAt(), c.getTokenHash(),
                c.getTokenExpiresAt(), c.getRegisteredAt());
    }
}
