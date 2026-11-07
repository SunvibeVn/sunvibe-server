package vn.sunvibe.survey.core.infrastructure.media;

import org.springframework.stereotype.Component;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** The media clock on {@link AppClock}, truncated to microseconds (plan Global Constraints). */
@Component
public class SystemMediaClock implements MediaClock {

    @Override
    public Instant now() {
        return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
    }
}
