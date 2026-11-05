package vn.sunvibe.auth.core.domain.repository.user;

import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.time.Instant;

/**
 * Sign-in bookkeeping, done with atomic updates so that parallel guesses against
 * one account cannot overwrite each other's count, and a login never writes the
 * whole row back. The persistence context is cleared: a caller that needs the row
 * afterwards reads it again.
 */
public interface UserLoginAttemptRepository {

    /**
     * Adds one failed attempt and returns the new count. The row stays locked
     * until the transaction ends, so concurrent callers each get a distinct count.
     */
    int incrementFailedLoginAttempts(UserId userId);

    /** Refuses password login for the user until {@code until}. */
    void lockLoginUntil(UserId userId, Instant until);

    /** Adds one wrong password at a reauthentication and returns the new count, as {@link #incrementFailedLoginAttempts}. */
    int incrementReauthFailures(UserId userId);

    /** Refuses reauthentication for the user until {@code until}. */
    void lockReauthUntil(UserId userId, Instant until);

    /** Forgets wrong passwords at a reauthentication and lifts its lock. */
    void clearReauthFailures(UserId userId);

    /** A session started: records when, forgets failed attempts and lifts a temporary lock, in one guarded write. */
    void recordLoginSuccess(UserId userId, Instant when);

    /** Moves a PENDING account to ACTIVE and leaves any other status alone, in one guarded write. */
    void activateIfPending(UserId userId);

}
