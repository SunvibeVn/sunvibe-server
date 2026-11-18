package vn.sunvibe.brokerage.core.infrastructure.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;

/**
 * Stores the starts of the activated versions whose start date has come (brokerage-agreement BR-5, BR-6, US-3 AC-5;
 * E05-S6 spec S6-D6, 5.4), every minute by default ({@code app.brokerage.start-delay}), on Boot's default scheduler
 * thread that the modules' jobs share. A run is bounded at 100 agreements; it runs on every instance and stays
 * idempotent through the guarded update, so no ShedLock is needed. Every read answers a due start already; the stored
 * row follows within the delay plus one run while fewer than 100 starts fall due a minute.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgreementStartJob {

    private final AgreementCommandUseCase commands;

    @Scheduled(fixedDelayString = "${app.brokerage.start-delay:60s}",
            initialDelayString = "${app.brokerage.start-delay:60s}")
    public void storeDueStarts() {
        int stored = commands.storeDueStarts();
        if (stored >= AgreementCommandUseCase.MAX_STARTS_PER_RUN) {
            // a fixed line with the count only: no id, no value; the next run takes the rest
            log.info("brokerage.start.run-full count={}", stored);
        }
    }
}
