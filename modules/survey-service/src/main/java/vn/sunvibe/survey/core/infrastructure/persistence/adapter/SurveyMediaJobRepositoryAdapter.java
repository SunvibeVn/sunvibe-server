package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import vn.sunvibe.survey.core.domain.model.media.MediaJobClaim;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.media.MediaJob;
import vn.sunvibe.survey.core.domain.model.media.MediaJobKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaJobEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyMediaJobJpaRepository;

import java.time.Instant;
import java.util.List;

/** Adapter - the processing and deletion jobs of photos (survey-media spec 4.4 step 3, 4.7, 5.2). */
@Service
@RequiredArgsConstructor
public class SurveyMediaJobRepositoryAdapter implements SurveyMediaJobDomainRepository {

    /** A value no user id has, so an empty exclusion list stays valid SQL. */
    private static final String NO_UPLOADER = "-";

    private final SurveyMediaJobJpaRepository jpaRepository;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void upsertProcess(SurveyMediaId mediaId, Instant dueAt, Instant now) {
        jpaRepository.upsert(mediaId.getValue(), MediaJobKind.PROCESS.name(), dueAt, now);
    }

    @Override
    public void upsertDeleteContent(SurveyMediaId mediaId, Instant dueAt, Instant now) {
        jpaRepository.upsert(mediaId.getValue(), MediaJobKind.DELETE_CONTENT.name(), dueAt, now);
    }

    @Override
    public List<MediaJob> findByMedia(SurveyMediaId mediaId) {
        return jpaRepository.findByMediaIdOrderByKindAsc(mediaId.getValue()).stream()
                .map(SurveyMediaJobRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<MediaJobClaim> claimNextProcess(Instant now, Instant hourStart,
                                                    Collection<String> excludedUploaders, String token,
                                                    Instant leaseUntil) {
        List<MediaJob> claimed = jdbc.query(MediaSql.CLAIM_NEXT_PROCESS, new MapSqlParameterSource("token", token)
                .addValue("leaseUntil", time(leaseUntil)).addValue("now", time(now))
                .addValue("hourStart", time(hourStart))
                .addValue("excluded", excludedUploaders.isEmpty() ? List.of(NO_UPLOADER) : excludedUploaders),
                (row, n) -> fromRow(row));
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        MediaJob job = claimed.get(0);
        String uploader = jdbc.queryForObject(MediaSql.UPLOADER_OF_MEDIA, new MapSqlParameterSource("id",
                job.mediaId().getValue()), String.class);
        return Optional.of(new MediaJobClaim(job, uploader));
    }

    @Override
    public Optional<MediaJob> claimNextDeleteContent(Instant now, String token, Instant leaseUntil) {
        List<MediaJob> claimed = jdbc.query(MediaSql.CLAIM_NEXT_DELETE_CONTENT, new MapSqlParameterSource("token",
                token).addValue("leaseUntil", time(leaseUntil)).addValue("now", time(now)), (row, n) -> fromRow(row));
        return claimed.stream().findFirst();
    }

    @Override
    public void closeProcess(SurveyMediaId mediaId, MediaJobState state, String code, Instant now) {
        jdbc.update(MediaSql.CLOSE_PROCESS_JOB, new MapSqlParameterSource("mediaId", mediaId.getValue())
                .addValue("state", state.name()).addValue("code", code).addValue("now", time(now)));
    }

    @Override
    public boolean anyDue(MediaJobKind kind, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject(MediaSql.ANY_DUE, new MapSqlParameterSource("kind",
                kind.name()).addValue("now", time(now)), Boolean.class));
    }

    @Override
    public boolean renewLease(long jobId, String token, Instant leaseUntil, Instant now) {
        return jdbc.update(MediaSql.RENEW_JOB_LEASE, fence(jobId, token).addValue("leaseUntil", time(leaseUntil))
                .addValue("now", time(now))) == 1;
    }

    @Override
    public Optional<MediaJob> lockForUpdate(long jobId) {
        return jdbc.query(MediaSql.LOCK_JOB, new MapSqlParameterSource("id", jobId), (row, n) -> fromRow(row))
                .stream().findFirst();
    }

    @Override
    public boolean finish(long jobId, String token, MediaJobState state, String errorCodeOrNull, Instant now) {
        return jdbc.update(MediaSql.FINISH_JOB, fence(jobId, token).addValue("state", state.name())
                .addValue("code", errorCodeOrNull).addValue("now", time(now))) == 1;
    }

    @Override
    public boolean retry(long jobId, String token, Instant dueAt, String errorCode, int limitHits, Instant now) {
        return jdbc.update(MediaSql.RETRY_JOB, fence(jobId, token).addValue("dueAt", time(dueAt))
                .addValue("code", errorCode).addValue("limitHits", limitHits).addValue("now", time(now))) == 1;
    }

    @Override
    public boolean defer(long jobId, String token, Instant dueAt, Instant now) {
        return jdbc.update(MediaSql.DEFER_JOB, fence(jobId, token).addValue("dueAt", time(dueAt))
                .addValue("now", time(now))) == 1;
    }

    private static MapSqlParameterSource fence(long jobId, String token) {
        return new MapSqlParameterSource("id", jobId).addValue("token", token);
    }

    private static MediaJob fromRow(ResultSet row) throws SQLException {
        return new MediaJob(row.getLong("id"), SurveyMediaId.of(row.getString("media_id")),
                MediaJobKind.valueOf(row.getString("kind")), MediaJobState.valueOf(row.getString("state")),
                row.getInt("attempts"), row.getInt("limit_hits"), row.getString("lease_token"),
                row.getObject("due_at", OffsetDateTime.class).toInstant(), row.getString("last_error_code"),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static MediaJob toDomain(SurveyMediaJobEntity entity) {
        return new MediaJob(entity.getId(), SurveyMediaId.of(entity.getMediaId()), entity.getKind(), entity.getState(),
                entity.getAttempts(), entity.getLimitHits(), entity.getLeaseToken(), entity.getDueAt(),
                entity.getLastErrorCode(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
