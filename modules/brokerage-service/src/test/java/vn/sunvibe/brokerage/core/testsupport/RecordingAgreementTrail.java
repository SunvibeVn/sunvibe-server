package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.repository.AgreementTrailDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Records the history and access rows written; {@link #failAccess()} makes the next access writes fail. */
public final class RecordingAgreementTrail implements AgreementTrailDomainRepository {

    /** One history row. */
    public record Event(String agreementId, Integer versionNoOrNull, AgreementEventType type, String actorUserIdOrNull,
                        Instant at) {
    }

    /** One access-log row. */
    public record Access(String userId, String agreementId, AgreementAccessType action, Instant at) {
    }

    private static final Set<AgreementEventType> AUTHOR_EVENTS = Set.of(AgreementEventType.CREATED,
            AgreementEventType.DRAFT_EDITED, AgreementEventType.DRAFT_OPENED, AgreementEventType.VERSION_SENT);

    private final List<Event> events = new ArrayList<>();
    private final List<Access> accesses = new ArrayList<>();
    private final List<String> authorReads = new ArrayList<>();
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

    /**
     * The distinct actors of the CREATED, DRAFT_EDITED, DRAFT_OPENED and VERSION_SENT rows recorded for versions 1 to
     * that number, as the store's statement reads them; each read is recorded.
     */
    @Override
    public Set<String> versionAuthors(String agreementId, int upToVersionNo) {
        authorReads.add(agreementId + "@" + upToVersionNo);
        return events.stream()
                .filter(event -> event.agreementId().equals(agreementId) && event.versionNoOrNull() != null
                        && event.versionNoOrNull() <= upToVersionNo && event.actorUserIdOrNull() != null
                        && AUTHOR_EVENTS.contains(event.type()))
                .map(Event::actorUserIdOrNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The author reads made so far, as "agreementId@versionNo", in order. */
    public List<String> authorReads() {
        return List.copyOf(authorReads);
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
