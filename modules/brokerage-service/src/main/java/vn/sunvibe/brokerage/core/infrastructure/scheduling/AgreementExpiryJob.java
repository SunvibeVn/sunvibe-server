package vn.sunvibe.brokerage.core.infrastructure.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sunvibe.brokerage.core.application.usecase.AgreementCommandUseCase;

/**
 * Stores the expiries of the agreements whose end has come (brokerage-agreement BR-5, BR-6, US-5 AC-1; E05-S8 spec
 * S8-D2, 5.4), every minute by default ({@code app.brokerage.expiry-delay}), on Boot's default scheduler thread that the
 * modules' jobs share. A run is bounded at 100 agreements; it runs on every instance and stays idempotent through the
 * guarded update, so no ShedLock is needed. Every read answers a due expiry already; the stored row follows within the
 * delay plus one run while fewer than 100 expiries fall due a minute.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgreementExpiryJob {

    private final AgreementCommandUseCase commands;

    @Scheduled(fixedDelayString = "${app.brokerage.expiry-delay:60s}",
            initialDelayString = "${app.brokerage.expiry-delay:60s}")
    public void storeDueExpiries() {
        int stored = commands.storeDueExpiries();
        if (stored >= AgreementCommandUseCase.MAX_EXPIRIES_PER_RUN) {
            // a fixed line with the count only: no id, no value; the next run takes the rest
            log.info("BROKERAGE_EXPIRIES_STORED count={}", stored);
        }
    }
}
