package vn.sunvibe.survey.core.application.shared.media;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.stereotype.Component;

import vn.sunvibe.survey.core.application.exception.media.MediaLimitReachedException;
import vn.sunvibe.survey.core.application.exception.media.MediaRateLimitedException;
import vn.sunvibe.survey.core.domain.model.media.HourlyUsage;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.media.MediaLimits;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;

/**
 * The limits of survey photo intake, counted in the database (spec A-3, A-27, BR-6): under the user's advisory
 * transaction lock (two-int form, media namespace) the photo records and declared bytes of the rolling hour and the
 * expensive outcomes of the clock hour; under the set lock the selected and pending photos of the checklist. Exact
 * across instances (SV-017). Runs in the caller's READ COMMITTED transaction.
 */
@Component
public class MediaLimitCounter {

    private static final Duration HOUR = Duration.ofHours(1);

    private final MediaCountersRepository counters;

    public MediaLimitCounter(MediaCountersRepository counters) {
        this.counters = counters;
    }

    /** spec A-3: the user's advisory lock, held until the caller's transaction ends (spec 5.4 lock order). */
    public void lockUser(String userId) {
        counters.lockUser(userId);
    }

    /**
     * spec 5.4 create (11): 300 photo records and 3 GiB of declared size per rolling hour ({@code records},
     * {@code bytes}; retry once the oldest record has left the hour), then the expensive outcomes (A-27).
     *
     * @throws MediaRateLimitedException 429 with the field and the seconds to wait
     */
    public void requireHourlyRoom(String userId, long newBytes, Instant now) {
        HourlyUsage usage = counters.hourlyUsage(userId, now.minus(HOUR));
        Optional<String> breach = MediaLimits.hourlyBreach(usage.records(), usage.bytes(), newBytes);
        if (breach.isPresent()) {
            Instant oldest = usage.oldestOrNull() == null ? now : usage.oldestOrNull();
            throw new MediaRateLimitedException(breach.get(), secondsUntil(now, oldest.plus(HOUR)));
        }
        requireExpensiveBelowCap(userId, now);
    }

    /**
     * spec A-27: at 20 expensive outcomes of the uploader in the clock hour, refused until the hour passes.
     *
     * @throws MediaRateLimitedException 429 on {@code expensive}
     */
    public void requireExpensiveBelowCap(String userId, Instant now) {
        Instant hourStart = ReadBudgetArithmetic.hourStart(now);
        long expensive = counters.counterValue(userId, hourStart, MediaCounterKind.EXPENSIVE);
        if (MediaLimits.expensiveReached((int) Math.min(expensive, Integer.MAX_VALUE))) {
            throw new MediaRateLimitedException("expensive", secondsUntil(now, hourStart.plus(HOUR)));
        }
    }

    /**
     * spec 5.4 create (13), BR-6: 100 selected and 20 pending photos per checklist, {@code selected} first.
     *
     * @throws MediaLimitReachedException 409 on the field
     */
    public static void requireSelectionRoom(Collection<SurveyMedia> ofChecklist) {
        int selected = 0;
        int pending = 0;
        for (SurveyMedia media : ofChecklist) {
            if (media.isSelected()) {
                selected++;
            }
            if (media.isPending()) {
                pending++;
            }
        }
        Optional<String> breach = MediaLimits.selectionBreach(selected, pending);
        if (breach.isPresent()) {
            throw new MediaLimitReachedException(breach.get());
        }
    }

    private static long secondsUntil(Instant now, Instant until) {
        long millis = Duration.between(now, until).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
