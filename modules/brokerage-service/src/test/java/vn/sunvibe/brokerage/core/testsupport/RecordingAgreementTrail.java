package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.repository.AgreementTrailDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Records the history and access rows written; {@link #failAccess()} makes the next access writes fail. */
public final class RecordingAgreementTrail implements AgreementTrailDomainRepository {

    /** One history row. */
    public record Event(String agreementId, Integer versionNoOrNull, AgreementEventType type, String actorUserIdOrNull,
                        Instant at) {
    }

    /** One access-log row. */
    public record Access(String userId, String agreementId, AgreementAccessType action, Instant at) {
    }

    private final List<Event> events = new ArrayList<>();
    private final List<Access> accesses = new ArrayList<>();
    private boolean failAccess;

    @Override
    public void recordEvent(String agreementId, Integer versionNoOrNull, AgreementEventType type,
                            String actorUserIdOrNull, Instant at) {
        events.add(new Event(agreementId, versionNoOrNull, type, actorUserIdOrNull, at));
    }

    @Override
    public void recordAccess(String userId, String agreementId, AgreementAccessType action, Instant at) {
        if (failAccess) {
            throw new IllegalStateException("the access log refused the row");
        }
        accesses.add(new Access(userId, agreementId, action, at));
    }

    /** Records one access row per agreement, in the order given; the batch fails whole as {@link #failAccess()} says. */
    @Override
    public void recordAccesses(String userId, Collection<String> agreementIds, AgreementAccessType action, Instant at) {
        if (failAccess) {
            throw new IllegalStateException("the access log refused the rows");
        }
        agreementIds.forEach(agreementId -> accesses.add(new Access(userId, agreementId, action, at)));
    }

    /** Every access write from now on throws, as a failed insert would. */
    public RecordingAgreementTrail failAccess() {
        failAccess = true;
        return this;
    }

    public List<Event> events() {
        return List.copyOf(events);
    }

    public List<Access> accesses() {
        return List.copyOf(accesses);
    }
}
