package vn.sunvibe.auth.core.domain.repository.user;

import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Finds accounts that were registered but never verified their phone number.
 */
public interface PendingAccountRepository {

    /**
     * PENDING, not deleted, phone never verified, registered before {@code cutoff};
     * oldest first, at most {@code limit}.
     */
    List<UserId> findUnverifiedPendingCreatedBefore(Instant cutoff, int limit);

}
