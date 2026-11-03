package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.media.HourlyUsage;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;

import java.time.Instant;
import java.util.OptionalLong;

/**
 * The counters, advisory locks and grouping checks of survey-media (spec A-3, A-6, A-19, A-26, A-27), in SQL: the
 * application classes call these and never JDBC directly. Each method joins the caller's transaction; the callers
 * run them in the short transactions the spec asks for.
 */
public interface MediaCountersRepository {

    /** spec A-3: the user's advisory transaction lock (two-int form, media namespace). */
    void lockUser(String userId);

    /** spec A-26, 4.4 step 4: deletes budget and counter rows of hours starting before the cutoff; returns how many. */
    int deleteBudgetsBefore(Instant hourStartBefore);

    /** spec A-3: photo records and declared bytes the user created after {@code since}. */
    HourlyUsage hourlyUsage(String userId, Instant since);

    /** The counter of the hour, 0 when it has no row. */
    long counterValue(String userId, Instant hourStart, MediaCounterKind kind);

    /**
     * spec A-26: counts one read when the hour's count is below {@code effectiveLimit}; the new count, or empty when
     * refused (nothing counted).
     */
    OptionalLong countIfBelow(String userId, Instant hourStart, MediaCounterKind kind, long effectiveLimit);

    /**
     * spec A-26: marks the hour exceeded at {@code now} unless already marked (inserting the hour with no read when it
     * has no row); true only for the call that set the mark, so one refusal of the hour writes the event, however close
     * in time parallel refusals are.
     */
    boolean markExceededFirst(String userId, Instant hourStart, MediaCounterKind kind, Instant now);

    /** spec A-27: adds to the hour's PROC_MS or EXPENSIVE counter unconditionally. */
    void add(String userId, Instant hourStart, MediaCounterKind kind, long amount);

    /** spec A-6: a MEDIA_LIST access row of the user for the checklist after {@code since}. */
    boolean hasRecentMediaListRow(String userId, String checklistId, Instant since);

    /**
     * spec A-19: the advisory transaction lock of one refusal-event key, without waiting: false when another writer
     * holds it (that writer writes or finds the key's event, so this one skips).
     */
    boolean tryLockEventKey(String userId, String resourceId, SecurityAction action, SecurityOutcome outcome);

    /** spec A-19: an event of that key after {@code since}. */
    boolean hasRecentEvent(String userId, String resourceId, SecurityAction action, SecurityOutcome outcome,
                           Instant since);
}
