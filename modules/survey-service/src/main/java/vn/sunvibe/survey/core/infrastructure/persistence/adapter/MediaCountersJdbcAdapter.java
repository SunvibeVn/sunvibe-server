package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.media.HourlyUsage;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.repository.MediaCountersRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.support.MediaSql;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;

/**
 * Adapter - the survey-media counters, advisory locks and grouping checks in SQL (survey-media spec A-3, A-6, A-19,
 * A-26, A-27); the statements are {@link MediaSql}'s. Times bind as {@code timestamptz}.
 */
@Service
@RequiredArgsConstructor
public class MediaCountersJdbcAdapter implements MediaCountersRepository {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void lockUser(String userId) {
        jdbc.query(MediaSql.LOCK_USER, new MapSqlParameterSource("namespace", MediaSql.USER_LOCK_NAMESPACE)
                .addValue("userId", userId), row -> null);
    }

    @Override
    public HourlyUsage hourlyUsage(String userId, Instant since) {
        return jdbc.queryForObject(MediaSql.HOURLY_USAGE, new MapSqlParameterSource("userId", userId)
                .addValue("since", time(since)), (row, n) -> {
                    OffsetDateTime oldest = row.getObject("oldest", OffsetDateTime.class);
                    return new HourlyUsage(row.getInt("records"), row.getLong("bytes"),
                            oldest == null ? null : oldest.toInstant());
                });
    }

    @Override
    public long counterValue(String userId, Instant hourStart, MediaCounterKind kind) {
        List<Long> values = jdbc.queryForList(MediaSql.COUNTER_VALUE, key(userId, hourStart, kind), Long.class);
        return values.isEmpty() ? 0L : values.get(0);
    }

    @Override
    public OptionalLong countIfBelow(String userId, Instant hourStart, MediaCounterKind kind, long effectiveLimit) {
        List<Long> counted = jdbc.queryForList(MediaSql.COUNT_IF_BELOW, key(userId, hourStart, kind)
                .addValue("effectiveLimit", effectiveLimit), Long.class);
        return counted.isEmpty() ? OptionalLong.empty() : OptionalLong.of(counted.get(0));
    }

    @Override
    public boolean markExceededFirst(String userId, Instant hourStart, MediaCounterKind kind, Instant now) {
        MapSqlParameterSource parameters = key(userId, hourStart, kind).addValue("now", time(now));
        if (jdbc.update(MediaSql.INSERT_EXCEEDED, parameters) == 1) {
            return true;
        }
        return jdbc.update(MediaSql.MARK_EXCEEDED_IF_NONE, parameters) == 1;
    }

    @Override
    public void add(String userId, Instant hourStart, MediaCounterKind kind, long amount) {
        jdbc.update(MediaSql.ADD_TO_COUNTER, key(userId, hourStart, kind).addValue("amount", amount));
    }

    @Override
    public boolean hasRecentMediaListRow(String userId, String checklistId, Instant since) {
        return Boolean.TRUE.equals(jdbc.queryForObject(MediaSql.HAS_RECENT_MEDIA_LIST_ROW,
                new MapSqlParameterSource("userId", userId).addValue("checklistId", checklistId)
                        .addValue("since", time(since)), Boolean.class));
    }

    @Override
    public boolean tryLockEventKey(String userId, String resourceId, SecurityAction action,
                                   SecurityOutcome outcome) {
        return Boolean.TRUE.equals(jdbc.queryForObject(MediaSql.TRY_LOCK_EVENT_KEY, new MapSqlParameterSource(
                "namespace", MediaSql.EVENT_LOCK_NAMESPACE).addValue("key", userId + "|" + resourceId + "|" + action
                + "|" + outcome), Boolean.class));
    }

    @Override
    public boolean hasRecentEvent(String userId, String resourceId, SecurityAction action, SecurityOutcome outcome,
                                  Instant since) {
        return Boolean.TRUE.equals(jdbc.queryForObject(MediaSql.HAS_RECENT_EVENT,
                new MapSqlParameterSource("userId", userId).addValue("resourceId", resourceId)
                        .addValue("action", action.name()).addValue("outcome", outcome.name())
                        .addValue("since", time(since)), Boolean.class));
    }

    private static MapSqlParameterSource key(String userId, Instant hourStart, MediaCounterKind kind) {
        return new MapSqlParameterSource("userId", userId).addValue("hourStart", time(hourStart))
                .addValue("kind", kind.name());
    }

    @Override
    public int deleteBudgetsBefore(Instant hourStartBefore) {
        return jdbc.update(MediaSql.DELETE_OLD_BUDGETS, new MapSqlParameterSource("cutoff", time(hourStartBefore)));
    }

    private static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
