package vn.sunvibe.auth.core.infrastructure.email;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.shared.external.email.MailSendGatePort;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * {@link MailSendGatePort} in memory, per instance, like the rate limiter. The warn line is the signal: the repo has no
 * metrics library (auth-email-access spec 5.6, R-7).
 */
@Log4j2
@Component
public class MailSendGate implements MailSendGatePort {

    private static final int MAX_CONCURRENT_SENDS = 4;
    private static final long SECONDS_PER_WINDOW = Duration.ofHours(1).toSeconds();

    private final Semaphore bulkhead = new Semaphore(MAX_CONCURRENT_SENDS);
    private final int maxPerHour;
    private final Supplier<Instant> clock;
    private long window = Long.MIN_VALUE;
    private int chargedInWindow;

    @Autowired
    public MailSendGate(@Value("${app.auth.mail.max-per-hour:300}") int maxPerHour) {
        this(maxPerHour, AppClock::currentUTC);
    }

    MailSendGate(int maxPerHour, Supplier<Instant> clock) {
        this.maxPerHour = maxPerHour;
        this.clock = clock;
    }

    @Override
    public boolean tryEnter() {
        boolean entered = bulkhead.tryAcquire();
        if (!entered) {
            log.warn("auth.mail.refused reason=bulkhead");
        }
        return entered;
    }

    @Override
    public synchronized boolean tryCharge() {
        long current = clock.get().getEpochSecond() / SECONDS_PER_WINDOW;
        if (current != window) {
            window = current;
            chargedInWindow = 0;
        }
        if (chargedInWindow >= maxPerHour) {
            log.warn("auth.mail.refused reason=budget");
            return false;
        }
        chargedInWindow++;
        return true;
    }

    @Override
    public void leave() {
        bulkhead.release();
    }
}
