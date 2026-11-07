package vn.sunvibe.survey.core.infrastructure.persistence.adapter.media;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.repository.media.IntakeHealthRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Adapter - the intake health row in SQL (survey-media spec A-18). The read joins the caller's transaction; each write
 * runs in its own REQUIRES_NEW, READ COMMITTED transaction that touches this row only. The SQL mirrors the pure rules
 * of {@link IntakeHealth}.
 */
@Service
public class IntakeHealthJdbcAdapter implements IntakeHealthRepository {

    static final String READ = "SELECT failure_seq, state_epoch, last_sweep_ok_at, last_probe_ok_at, last_failure_at,"
            + " last_worker_beat_at, last_deletion_beat_at, scan_blocked_since, intake_paused_since, pause_reason"
            + " FROM survey_media_health WHERE id = 1";
    static final String FAILURE = "UPDATE survey_media_health SET failure_seq = failure_seq + 1,"
            + " last_failure_at = :now WHERE id = 1";
    static final String FAILURE_AND_PAUSE = "UPDATE survey_media_health SET failure_seq = failure_seq + 1,"
            + " last_failure_at = :now,"
            + " state_epoch = state_epoch + CASE WHEN intake_paused_since IS NULL THEN 1 ELSE 0 END,"
            + " intake_paused_since = COALESCE(intake_paused_since, :now),"
            + " pause_reason = COALESCE(pause_reason, :reason) WHERE id = 1";
    static final String SET_SCAN_BLOCKED = "UPDATE survey_media_health"
            + " SET scan_blocked_since = COALESCE(scan_blocked_since, :now) WHERE id = 1";
    static final String PROBE_PASSED = "UPDATE survey_media_health SET last_probe_ok_at = :now,"
            + " scan_blocked_since = CASE WHEN :clearScanBlock THEN NULL ELSE scan_blocked_since END WHERE id = 1";
    static final String RESUME_IF = "UPDATE survey_media_health SET intake_paused_since = NULL, pause_reason = NULL,"
            + " state_epoch = state_epoch + 1, last_sweep_ok_at = :now"
            + " WHERE id = 1 AND failure_seq = :seqAtStart AND intake_paused_since IS NOT NULL";
    static final String SWEEP_COMPLETED = "UPDATE survey_media_health SET last_sweep_ok_at = :now WHERE id = 1";
    static final String BEAT_WORKER = "UPDATE survey_media_health SET last_worker_beat_at = :now WHERE id = 1";
    static final String BEAT_DELETION = "UPDATE survey_media_health SET last_deletion_beat_at = :now WHERE id = 1";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;

    public IntakeHealthJdbcAdapter(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public IntakeHealth read() {
        return jdbc.getJdbcTemplate().queryForObject(READ, (row, n) -> toHealth(row));
    }

    @Override
    public void recordFailure(PauseReason reason, boolean pause, Instant appNow) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", time(appNow)).addValue("reason",
                reason.name());
        write(pause ? FAILURE_AND_PAUSE : FAILURE, params);
    }

    @Override
    public void setScanBlocked(Instant appNow) {
        write(SET_SCAN_BLOCKED, new MapSqlParameterSource("now", time(appNow)));
    }

    @Override
    public void recordProbePassed(boolean clearScanBlock, Instant appNow) {
        write(PROBE_PASSED, new MapSqlParameterSource("now", time(appNow)).addValue("clearScanBlock", clearScanBlock));
    }

    @Override
    public boolean resumeIf(long seqAtStart, Instant appNow) {
        return write(RESUME_IF, new MapSqlParameterSource("now", time(appNow)).addValue("seqAtStart", seqAtStart))
                == 1;
    }

    @Override
    public void recordSweepCompleted(Instant appNow) {
        write(SWEEP_COMPLETED, new MapSqlParameterSource("now", time(appNow)));
    }

    @Override
    public void beatWorker(Instant appNow) {
        write(BEAT_WORKER, new MapSqlParameterSource("now", time(appNow)));
    }

    @Override
    public void beatDeletion(Instant appNow) {
        write(BEAT_DELETION, new MapSqlParameterSource("now", time(appNow)));
    }

    private int write(String sql, MapSqlParameterSource params) {
        Integer rows = ownTransaction.execute(tx -> jdbc.update(sql, params));
        return rows == null ? 0 : rows;
    }

    private static IntakeHealth toHealth(ResultSet row) throws SQLException {
        String reason = row.getString("pause_reason");
        return new IntakeHealth(row.getLong("failure_seq"), row.getLong("state_epoch"),
                instant(row, "last_sweep_ok_at"), instant(row, "last_probe_ok_at"), instant(row, "last_failure_at"),
                instant(row, "last_worker_beat_at"), instant(row, "last_deletion_beat_at"),
                instant(row, "scan_blocked_since"), instant(row, "intake_paused_since"),
                reason == null ? null : PauseReason.valueOf(reason));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
