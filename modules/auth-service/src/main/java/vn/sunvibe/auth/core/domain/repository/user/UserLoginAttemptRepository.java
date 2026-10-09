package vn.sunvibe.auth.core.domain.repository.user;

import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.time.Instant;

/**
 * Failed-login bookkeeping, done with atomic updates so that parallel guesses
 * against one account cannot overwrite each other's count.
 */
public interface UserLoginAttemptRepository {

    /**
     * Adds one failed attempt and returns the new count. The row stays locked
     * until the transaction ends, so concurrent callers each get a distinct count.
     */
    int incrementFailedLoginAttempts(UserId userId);

    /** Refuses password login for the user until {@code until}. */
    void lockLoginUntil(UserId userId, Instant until);

}
