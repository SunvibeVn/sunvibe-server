package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import vn.sunvibe.survey.core.domain.model.media.MediaIncidentKind;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaIncidentDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.support.MediaSql;

/** Adapter - the incident rows of overdue deletions (survey-media spec A-18, 5.2), times from the application clock. */
@Repository
public class SurveyMediaIncidentJdbcAdapter implements SurveyMediaIncidentDomainRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SurveyMediaIncidentJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordOverdue(MediaIncidentKind kind, Instant firstOverdueAt, int count, Instant appNow) {
        jdbc.update(MediaSql.RECORD_INCIDENT, new MapSqlParameterSource("kind", kind.name())
                .addValue("now", time(appNow)).addValue("first", time(firstOverdueAt)).addValue("count", count));
    }

    @Override
    public int resolveOpen(Instant appNow) {
        return jdbc.update(MediaSql.RESOLVE_INCIDENTS, new MapSqlParameterSource("now", time(appNow)));
    }

    private static OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
