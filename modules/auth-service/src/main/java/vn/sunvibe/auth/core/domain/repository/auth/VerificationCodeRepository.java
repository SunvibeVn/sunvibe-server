package vn.sunvibe.auth.core.domain.repository.auth;

import vn.sunvibe.auth.core.domain.model.auth.VerificationCode;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence of phone/e-mail verification codes.
 */
public interface VerificationCodeRepository {

    /** Inserts a new code or updates an existing one. */
    void save(VerificationCode code);

    /** Inserts a new code at once, before its mail leaves. */
    void insert(VerificationCode code);

    /** The newest bind code of the user, without a lock. */
    Optional<VerificationCode> findLatestBind(String userId);

    /** The newest bind code of the user, locked until the transaction ends. */
    Optional<VerificationCode> findLatestBindForUpdate(String userId);

    /** Burns every bind code of the user but {@code keptId}. */
    void burnOtherBindCodes(String userId, Long keptId, Instant now);

    /** Bind codes the user asked for since {@code since} (daily cap per user). */
    long countBindIssuedSince(String userId, Instant since);

    /** Bind codes mailed to this address since {@code since}, whoever asked (daily cap per destination). */
    long countBindIssuedToDestinationSince(String destination, Instant since);

    /**
     * Deletes up to {@code limit} rows whose last expiry is before {@code cutoff}.
     *
     * @return the number of deleted rows
     */
    int deleteExpiredBatch(Instant cutoff, int limit);

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
