package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.auth.RegistrationPhoneChallenge;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence of pre-registration phone challenges.
 */
public interface RegistrationPhoneChallengeRepository {

    /** Inserts a new challenge or updates an existing one. */
    void save(RegistrationPhoneChallenge challenge);

    /**
     * The newest challenge of the phone number, locked until the transaction
     * ends so concurrent guesses are counted one at a time.
     */
    Optional<RegistrationPhoneChallenge> findLatestForUpdate(String phone);

    /** The challenge that issued the token with this hash, locked until the transaction ends. */
    Optional<RegistrationPhoneChallenge> findByTokenHashForUpdate(String tokenHash);

    /** Burns every still-usable code of the phone and revokes its unused registration tokens. */
    void invalidateActive(String phone, Instant now);

    /** Challenges issued to the phone since {@code since} (daily cap). */
    long countIssuedSince(String phone, Instant since);

    /** Deletes challenges whose code and token both expired before {@code cutoff}. */
    int deleteExpiredBefore(Instant cutoff);

}
