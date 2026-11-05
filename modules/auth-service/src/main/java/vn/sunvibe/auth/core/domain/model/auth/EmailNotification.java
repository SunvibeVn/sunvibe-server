package vn.sunvibe.auth.core.domain.model.auth;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A notice still to be sent to an address the account gave up (auth-email-access spec 5.6, BR-13). It is kept in the
 * transaction of the change, so a failing sender loses nothing, and deleted when sent or given up, so the address is not
 * kept. It never names the new address.
 */
@Getter
public class EmailNotification {

    /** The verified email of the account was replaced. */
    public static final String KIND_EMAIL_CHANGED = "EMAIL_CHANGED";

    /** The wait after each failed attempt; one more failure gives up. */
    private static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6));

    private final Long id;
    private final String userId;
    private final String kind;
    private final String destination;
    private final Instant createdAt;
    private int attempts;
    private Instant nextAttemptAt;

    private EmailNotification(Long id, String userId, String kind, String destination, Instant createdAt, int attempts,
                              Instant nextAttemptAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.attempts = attempts;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    /** A notice to the replaced address, due at once. */
    public static EmailNotification emailChanged(Long id, String userId, String oldAddress, Instant now) {
        return new EmailNotification(id, userId, KIND_EMAIL_CHANGED, oldAddress, now, 0, now);
    }

    /** Rebuilds a notice from storage. */
    public static EmailNotification load(Long id, String userId, String kind, String destination, Instant createdAt,
                                         int attempts, Instant nextAttemptAt) {
        return new EmailNotification(id, userId, kind, destination, createdAt, attempts, nextAttemptAt);
    }

    /**
     * Counts a failed send and sets the next attempt.
     *
     * @return false if this was the last attempt: the notice is given up
     */
    public boolean registerFailure(Instant now) {
        if (attempts >= RETRY_DELAYS.size()) {
            return false;
        }
        nextAttemptAt = now.plus(RETRY_DELAYS.get(attempts));
        attempts++;
        return true;
    }
}
