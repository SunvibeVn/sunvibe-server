package vn.sunvibe.auth.core.presentation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationChallengePurgeUseCase;

/**
 * Every code sent before registration adds a row, so expired ones are deleted daily
 * ({@code app.security.verification.registration-cleanup-cron}, default 03:45 server time).
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RegistrationChallengeCleanupScheduler {

    private final RegistrationChallengePurgeUseCase registrationChallengePurgeUseCase;

    @Scheduled(cron = "${app.security.verification.registration-cleanup-cron:0 45 3 * * *}")
    public void purgeExpiredRegistrationChallenges() {
        int deleted = registrationChallengePurgeUseCase.execute();
        log.info("Purged {} expired registration phone challenges", deleted);
    }
}
