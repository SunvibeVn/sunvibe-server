package vn.sunvibe.property.core.infrastructure.persistence.adapter.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.repository.IntakeTrailDomainRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

/**
 * Repository Implementation - the insert-only trail. Plain SQL: these rows are never read back as objects, so an entity
 * would add nothing. Each write joins the caller's transaction through the shared connection.
 */
@Service
@RequiredArgsConstructor
public class IntakeTrailRepositoryAdapter implements IntakeTrailDomainRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void recordEvent(String intakeId, String actorUserIdOrNull, String action, Instant occurredAt) {
        jdbc.update("insert into inventory_intake_events (intake_id, actor_user_id, action, occurred_at)"
                + " values (?, ?, ?, ?)", intakeId, actorUserIdOrNull, action, Timestamp.from(occurredAt));
    }

    @Override
    public void recordAccess(String userId, String leadId, String intakeIdOrNull, String caseIdOrNull, String action,
                             Instant accessedAt) {
        jdbc.update("insert into inventory_access_logs (user_id, lead_id, intake_id, case_id, action, accessed_at)"
                + " values (?, ?, ?, ?, ?, ?)", userId, leadId, intakeIdOrNull, caseIdOrNull, action,
                Timestamp.from(accessedAt));
    }

    @Override
    public Set<String> actorsOfLead(String leadId) {
        return Set.copyOf(jdbc.queryForList("select distinct e.actor_user_id from inventory_intake_events e"
                + " join inventory_intakes i on i.id = e.intake_id where i.lead_id = ? and e.actor_user_id is not null",
                String.class, leadId));
    }
}
