package vn.sunvibe.brokerage.core.infrastructure.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The stored expiry's scheduled trigger (brokerage-agreement BR-6; E05-S8 spec S8-D2, 5.4): each trigger calls the one
 * use case once, on a delay read from {@code app.brokerage.expiry-delay}, 60 s by default, for both delays.
 */
class AgreementExpiryJobTest {

    private final List<String> calls = new ArrayList<>();

    @Test
    void run_callsStoreDueExpiriesOnce() {
        AgreementExpiryJob job = new AgreementExpiryJob(recordingCommands());

        job.storeDueExpiries();

        assertEquals(List.of("storeDueExpiries"), calls);
    }

    @Test
    void scheduled_readsTheExpiryDelayKey() throws NoSuchMethodException {
        Method run = AgreementExpiryJob.class.getMethod("storeDueExpiries");
        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        assertEquals("${app.brokerage.expiry-delay:60s}", scheduled.fixedDelayString());
        assertEquals("${app.brokerage.expiry-delay:60s}", scheduled.initialDelayString());
        assertEquals("", scheduled.cron());
    }

    /** A use case that records each call by method name; storeDueExpiries stores one. */
    private AgreementCommandUseCase recordingCommands() {
        return (AgreementCommandUseCase) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{AgreementCommandUseCase.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    return "storeDueExpiries".equals(method.getName()) ? 1 : null;
                });
    }
}
