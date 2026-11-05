package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.model.auth.RegistrationEmailChallenge;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationEmailChallengeRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hand-written fake; stores copies like a database, and refuses a second unused code as the partial index does. */
public class InMemoryRegistrationEmailChallengeRepository implements RegistrationEmailChallengeRepository {

    private final Map<Long, RegistrationEmailChallenge> challenges = new LinkedHashMap<>();
    /** Makes the next insert lose to the unique index, as a parallel send would. */
    public boolean indexViolationOnInsert;

    @Override
    public boolean insert(RegistrationEmailChallenge challenge) {
        boolean anotherUnused = challenges.values().stream()
                .anyMatch(c -> c.getEmailKey().equals(challenge.getEmailKey()) && c.getConsumedAt() == null);
        if (indexViolationOnInsert || anotherUnused) {
            return false;
        }
        challenges.put(challenge.getId(), copy(challenge));
        return true;
    }

    @Override
    public void save(RegistrationEmailChallenge challenge) {
        challenges.put(challenge.getId(), copy(challenge));
    }

    @Override
    public Optional<RegistrationEmailChallenge> findLatest(String emailKey) {
        return challenges.values().stream()
                .filter(c -> c.getEmailKey().equals(emailKey))
                .max(Comparator.comparing(RegistrationEmailChallenge::getCreatedAt)
                        .thenComparing(RegistrationEmailChallenge::getId))
                .map(InMemoryRegistrationEmailChallengeRepository::copy);
    }

    @Override
    public Optional<RegistrationEmailChallenge> findLatestForUpdate(String emailKey) {
        return findLatest(emailKey);
    }

    @Override
    public Optional<RegistrationEmailChallenge> findByTokenHashForUpdate(String tokenHash) {
        return challenges.values().stream()
                .filter(c -> tokenHash.equals(c.getTokenHash()))
                .findFirst()
                .map(InMemoryRegistrationEmailChallengeRepository::copy);
    }

    @Override
    public void burnActiveCodes(String emailKey, Instant now) {
        challenges.replaceAll((id, c) -> c.getEmailKey().equals(emailKey) && c.getConsumedAt() == null
                ? load(c, now) : c);
    }

    @Override
    public int deleteExpiredBatch(Instant cutoff, int limit) {
        throw new UnsupportedOperationException("The purge is tested on PostgreSQL");
    }

    @Override
    public long countIssuedSince(String emailKey, Instant since) {
        return challenges.values().stream()
                .filter(c -> c.getEmailKey().equals(emailKey) && c.getCreatedAt().isAfter(since)).count();
    }

    public List<RegistrationEmailChallenge> all() {
        return List.copyOf(challenges.values());
    }

    /** Moves every challenge {@code age} into the past, past a cooldown for example. */
    public void age(Duration age) {
        challenges.replaceAll((id, c) -> RegistrationEmailChallenge.load(c.getId(), c.getEmailKey(), c.getCodeHash(),
                c.getCreatedAt().minus(age), c.getExpiresAt().minus(age), c.getFailedAttempts(), c.getConsumedAt(),
                c.getVerifiedAt(), c.getTokenHash(), c.getTokenExpiresAt() == null ? null : c.getTokenExpiresAt().minus(age),
                c.getRegisteredAt()));
    }

    private static RegistrationEmailChallenge copy(RegistrationEmailChallenge c) {
        return load(c, c.getConsumedAt());
    }

    private static RegistrationEmailChallenge load(RegistrationEmailChallenge c, Instant consumedAt) {
        return RegistrationEmailChallenge.load(c.getId(), c.getEmailKey(), c.getCodeHash(), c.getCreatedAt(),
                c.getExpiresAt(), c.getFailedAttempts(), consumedAt, c.getVerifiedAt(), c.getTokenHash(),
                c.getTokenExpiresAt(), c.getRegisteredAt());
    }
}
