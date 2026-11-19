package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.time.Instant;

/**
 * One time-driven transition of an agreement that is due at a read but not stored yet (E05-S8 spec S8-D3, S8-D8 step 5;
 * plan E05S8-P3): the version it concerns, its history event ({@link AgreementEventType#VERSION_LAPSED},
 * {@link AgreementEventType#VERSION_STARTED} or {@link AgreementEventType#EXPIRED}) and the moment it fell due, which
 * dates its history row.
 */
public record DueTransition(int versionNo, AgreementEventType type, Instant at) {

    public DueTransition {
        if (type == null || at == null) {
            throw new IllegalArgumentException("A due transition needs its event and its moment.");
        }
    }
}
