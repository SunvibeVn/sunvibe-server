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
 * The stored start's scheduled trigger (brokerage-agreement BR-5, BR-6; E05-S6 spec S6-D6, 5.4): each trigger calls the
 * one use case once, on a delay read from {@code app.brokerage.start-delay}, 60 s by default, for both delays.
 */
class AgreementStartJobTest {

    private final List<String> calls = new ArrayList<>();

    @Test
    void run_callsStoreDueStartsOnce() {
        AgreementStartJob job = new AgreementStartJob(recordingCommands());

        job.storeDueStarts();

        assertEquals(List.of("storeDueStarts"), calls);
    }

    @Test
    void scheduled_readsTheDelayPropertyWithDefault60s() throws NoSuchMethodException {
        Method run = AgreementStartJob.class.getMethod("storeDueStarts");
        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        assertEquals("${app.brokerage.start-delay:60s}", scheduled.fixedDelayString());
        assertEquals("${app.brokerage.start-delay:60s}", scheduled.initialDelayString());
        assertEquals("", scheduled.cron());
    }

    /** A use case that records each call by method name; storeDueStarts stores one. */
    private AgreementCommandUseCase recordingCommands() {
        return (AgreementCommandUseCase) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{AgreementCommandUseCase.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    return "storeDueStarts".equals(method.getName()) ? 1 : null;
                });
    }
}
