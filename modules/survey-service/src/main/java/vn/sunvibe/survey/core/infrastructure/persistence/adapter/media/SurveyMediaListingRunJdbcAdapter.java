package vn.sunvibe.survey.core.infrastructure.persistence.adapter.media;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import vn.sunvibe.survey.core.domain.model.media.ListingRun;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaListingRunDomainRepository;

/** Adapter - the leased, fenced runs of the full listing (survey-media spec 4.4 step 4, 5.2). */
@Repository
public class SurveyMediaListingRunJdbcAdapter implements SurveyMediaListingRunDomainRepository {

    private static final String COLUMNS = "id, started_at, lease_token, lease_until, start_after_key, keys_seen,"
            + " undeleted_count, finished_at";
    /** An abandoned run that never listed a page keeps this as its key: it did not list to the end. */
    private static final String ABANDONED = "!abandoned";
    private static final String LEASE_OPEN = "UPDATE survey_media_listing_runs SET lease_token = :token,"
            + " lease_until = :leaseUntil WHERE finished_at IS NULL AND (lease_until IS NULL OR lease_until < :now)"
            + " RETURNING " + COLUMNS;
    private static final String START_IF_DUE = "INSERT INTO survey_media_listing_runs (started_at, lease_token,"
            + " lease_until, keys_seen, undeleted_count) SELECT :now, :token, :leaseUntil, 0, 0 WHERE NOT EXISTS"
            + " (SELECT 1 FROM survey_media_listing_runs WHERE finished_at IS NULL OR started_at > :dueBefore)"
            + " ON CONFLICT DO NOTHING RETURNING " + COLUMNS;
    private static final String PROGRESS = "UPDATE survey_media_listing_runs SET start_after_key = :key,"
            + " keys_seen = keys_seen + :seen, undeleted_count = undeleted_count + :undeleted, lease_until ="
            + " :leaseUntil WHERE id = :id AND lease_token = :token AND finished_at IS NULL";
    private static final String FINISH = "UPDATE survey_media_listing_runs SET finished_at = GREATEST(:now,"
            + " started_at), lease_token = NULL, lease_until = NULL, start_after_key = NULL WHERE id = :id"
            + " AND lease_token = :token AND finished_at IS NULL";
    private static final String ABANDON = "UPDATE survey_media_listing_runs SET finished_at = GREATEST(:now,"
            + " started_at), lease_token = NULL, lease_until = NULL, start_after_key = COALESCE(start_after_key,"
            + " '" + ABANDONED + "') WHERE finished_at IS NULL AND started_at < :cutoff RETURNING id";
    // survey-media spec A-18, 4.4 step 4, SM-14 (C-14, 2026-11-03): the open run's count blocks, and so does the
    // latest finished run's count while that run listed to the end; a newer run ends an older count only when it
    // finishes, by listing to the end (its own count decides) or by abandonment (it finished with a key left and
    // blocks nothing), so no run blocks the resume for ever
    private static final String BLOCKS_RESUME = "SELECT EXISTS (SELECT 1 FROM survey_media_listing_runs"
            + " WHERE finished_at IS NULL AND undeleted_count > 0) OR COALESCE((SELECT start_after_key IS NULL"
            + " AND undeleted_count > 0 FROM survey_media_listing_runs WHERE finished_at IS NOT NULL"
            + " ORDER BY started_at DESC, id DESC LIMIT 1), false)";

    private final NamedParameterJdbcTemplate jdbc;

    public SurveyMediaListingRunJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ListingRun> leaseOpen(String token, Instant leaseUntil, Instant now) {
        return jdbc.query(LEASE_OPEN, new MapSqlParameterSource("token", token).addValue("leaseUntil",
                time(leaseUntil)).addValue("now", time(now)), (row, n) -> run(row)).stream().findFirst();
    }

    @Override
    public Optional<ListingRun> startIfDue(String token, Instant leaseUntil, Instant dueBefore, Instant now) {
        return jdbc.query(START_IF_DUE, new MapSqlParameterSource("token", token).addValue("leaseUntil",
                time(leaseUntil)).addValue("dueBefore", time(dueBefore)).addValue("now", time(now)),
                (row, n) -> run(row)).stream().findFirst();
    }

    @Override
    public boolean progress(long id, String token, String startAfterKey, int keysSeen, int undeleted,
                            Instant leaseUntil) {
        return jdbc.update(PROGRESS, new MapSqlParameterSource("id", id).addValue("token", token)
                .addValue("key", startAfterKey).addValue("seen", keysSeen).addValue("undeleted", undeleted)
                .addValue("leaseUntil", time(leaseUntil))) == 1;
    }

    @Override
    public boolean finish(long id, String token, Instant now) {
        return jdbc.update(FINISH, new MapSqlParameterSource("id", id).addValue("token", token)
                .addValue("now", time(now))) == 1;
    }

    @Override
    public List<Long> abandonStartedBefore(Instant cutoff, Instant now) {
        return jdbc.queryForList(ABANDON, new MapSqlParameterSource("cutoff", time(cutoff)).addValue("now", time(now)),
                Long.class);
    }

    @Override
    public boolean blocksResume() {
        return Boolean.TRUE.equals(jdbc.queryForObject(BLOCKS_RESUME, new MapSqlParameterSource(), Boolean.class));
    }

    private static ListingRun run(ResultSet row) throws SQLException {
        return new ListingRun(row.getLong("id"), instant(row.getTimestamp("started_at")), row.getString("lease_token"),
                instant(row.getTimestamp("lease_until")), row.getString("start_after_key"), row.getLong("keys_seen"),
                row.getInt("undeleted_count"), instant(row.getTimestamp("finished_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static OffsetDateTime time(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
