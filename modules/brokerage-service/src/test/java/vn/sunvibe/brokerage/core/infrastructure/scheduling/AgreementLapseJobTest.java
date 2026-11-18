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
 * The 7-day lapse's scheduled trigger (brokerage-agreement BR-10; E05-S5 spec S5-D8, 5.3a, LP-4): each trigger calls the
 * one use case once, on a delay read from {@code app.brokerage.lapse-delay}, 60 s by default, for both delays.
 */
class AgreementLapseJobTest {

    private final List<String> calls = new ArrayList<>();

    @Test
    void run_callsStoreDueLapsesOnce() {
        AgreementLapseJob job = new AgreementLapseJob(recordingCommands());

        job.storeDueLapses();

        assertEquals(List.of("storeDueLapses"), calls);
    }

    @Test
    void scheduled_readsTheDelayPropertyWithDefault60s() throws NoSuchMethodException {
        Method run = AgreementLapseJob.class.getMethod("storeDueLapses");
        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        assertEquals("${app.brokerage.lapse-delay:60s}", scheduled.fixedDelayString());
        assertEquals("${app.brokerage.lapse-delay:60s}", scheduled.initialDelayString());
        assertEquals("", scheduled.cron());
    }

    /** A use case that records each call by method name; storeDueLapses stores one. */
    private AgreementCommandUseCase recordingCommands() {
        return (AgreementCommandUseCase) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{AgreementCommandUseCase.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    return "storeDueLapses".equals(method.getName()) ? 1 : null;
                });
    }
}
