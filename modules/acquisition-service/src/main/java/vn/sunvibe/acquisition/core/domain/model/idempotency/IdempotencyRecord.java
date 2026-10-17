package vn.sunvibe.acquisition.core.domain.model.idempotency;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;

import java.time.Duration;
import java.time.Instant;

/**
 * Remembers which lead an {@code Idempotency-Key} produced and a hash of the request body, so a repeated request is
 * answered with the same lead and a reused key with another body is refused. Technical data, not lead data.
 */
public record IdempotencyRecord(String key, String requestHash, OwnerLeadId leadId, Instant createdAt) {

    /**
     * True when the record is more than {@code age} old at {@code now}; such a key may be used again.
     */
    public boolean isOlderThan(Duration age, Instant now) {
        return createdAt.plus(age).isBefore(now);
    }
}
