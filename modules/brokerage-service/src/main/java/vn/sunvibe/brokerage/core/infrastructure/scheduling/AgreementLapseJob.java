package vn.sunvibe.brokerage.core.infrastructure.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;

/**
 * Stores the 7-day lapses of the versions the owner did not answer (brokerage-agreement BR-10, US-2 AC-3; E05-S5 spec
 * S5-D8, 5.3a), every minute by default ({@code app.brokerage.lapse-delay}), on Boot's default scheduler thread that the
 * modules' jobs share. A run is bounded at 100 agreements; it runs on every instance and stays idempotent through the
 * guarded update, so no ShedLock is needed. The stored rows follow each deadline within the delay plus one run while
 * fewer than 100 lapses fall due a minute.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgreementLapseJob {

    private final AgreementCommandUseCase commands;

    @Scheduled(fixedDelayString = "${app.brokerage.lapse-delay:60s}",
            initialDelayString = "${app.brokerage.lapse-delay:60s}")
    public void storeDueLapses() {
        int stored = commands.storeDueLapses();
        if (stored >= AgreementCommandUseCase.MAX_LAPSES_PER_RUN) {
            // a fixed line with the count only: no id, no value; the next run takes the rest
            log.info("brokerage.lapse.run-full count={}", stored);
        }
    }
}
