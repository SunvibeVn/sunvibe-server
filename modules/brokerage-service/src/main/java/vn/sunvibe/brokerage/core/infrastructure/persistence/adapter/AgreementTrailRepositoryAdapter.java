package vn.sunvibe.brokerage.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;
import vn.sunvibe.brokerage.core.domain.repository.AgreementTrailDomainRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;

/**
 * Repository Implementation - the insert-only trail of the agreements. Plain SQL: these rows are never read back as
 * objects, so an entity would add nothing. Each write joins the caller's transaction through the shared connection.
 */
@Service
@RequiredArgsConstructor
public class AgreementTrailRepositoryAdapter implements AgreementTrailDomainRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void recordEvent(String agreementId, Integer versionNoOrNull, AgreementEventType type,
                            String actorUserIdOrNull, Instant at) {
        jdbc.update("insert into brokerage_agreement_events (agreement_id, version_no, event_type, actor_user_id,"
                + " occurred_at) values (?, ?, ?, ?, ?)", agreementId, versionNoOrNull, type.name(), actorUserIdOrNull,
                Timestamp.from(at));
    }

    @Override
    public void recordAccess(String userId, String agreementId, AgreementAccessType action, Instant at) {
        jdbc.update("insert into brokerage_agreement_access_logs (user_id, agreement_id, action, accessed_at)"
                + " values (?, ?, ?, ?)", userId, agreementId, action.name(), Timestamp.from(at));
    }

    /** One JDBC batch of the inserts {@link #recordAccess} writes one by one. */
    @Override
    public void recordAccesses(String userId, Collection<String> agreementIds, AgreementAccessType action, Instant at) {
        if (agreementIds.isEmpty()) {
            return;
        }
        Timestamp time = Timestamp.from(at);
        jdbc.batchUpdate("insert into brokerage_agreement_access_logs (user_id, agreement_id, action, accessed_at)"
                + " values (?, ?, ?, ?)", agreementIds.stream()
                .map(agreementId -> new Object[]{userId, agreementId, action.name(), time})
                .toList());
    }
}
