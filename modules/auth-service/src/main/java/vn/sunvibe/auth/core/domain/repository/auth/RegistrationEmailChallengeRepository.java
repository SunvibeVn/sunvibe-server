package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.auth.RegistrationEmailChallenge;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence of pre-registration email challenges.
 */
public interface RegistrationEmailChallengeRepository {

    /**
     * Inserts a new challenge at once, before its mail leaves.
     *
     * @return false if the unique index refused it: another unused code exists for the address. PostgreSQL has then
     *         aborted the transaction, so the caller runs no further SQL in it.
     */
    boolean insert(RegistrationEmailChallenge challenge);

    /** Writes the changes of an existing challenge. */
    void save(RegistrationEmailChallenge challenge);

    /** The newest challenge of the address, without a lock. */
    Optional<RegistrationEmailChallenge> findLatest(String emailKey);

    /** The newest challenge of the address, locked until the transaction ends: guesses are counted one at a time. */
    Optional<RegistrationEmailChallenge> findLatestForUpdate(String emailKey);

    /** The challenge that issued the token with this hash, locked until the transaction ends. */
    Optional<RegistrationEmailChallenge> findByTokenHashForUpdate(String tokenHash);

    /** Burns every unused code of the address. A verified token stays usable (auth-email-access spec 4.7). */
    void burnActiveCodes(String emailKey, Instant now);

    /** Challenges issued to the address since {@code since} (daily cap). */
    long countIssuedSince(String emailKey, Instant since);

    /**
     * Deletes up to {@code limit} rows whose last expiry is before {@code cutoff}.
     *
     * @return the number of deleted rows
     */
    int deleteExpiredBatch(Instant cutoff, int limit);

}
