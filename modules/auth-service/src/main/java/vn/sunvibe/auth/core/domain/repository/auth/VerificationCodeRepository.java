package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;

import java.time.Instant;
import java.util.Optional;

public interface VerificationCodeRepository {

    void save(VerificationCode code);

    /**
     * The newest code of the user on this channel, locked until the transaction
     * ends so concurrent guesses are counted one at a time.
     */
    Optional<VerificationCode> findLatestForUpdate(String userId, VerificationChannel channel);

    /** Burns every still-usable code of the user on this channel. */
    void invalidateActive(String userId, VerificationChannel channel, Instant now);

    /** Codes issued to the user on this channel since {@code since} (daily cap). */
    long countIssuedSince(String userId, VerificationChannel channel, Instant since);

}
