package vn.sunvibe.survey.core.application.shared.media;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.logging.RequestId;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;
import vn.sunvibe.survey.core.domain.repository.common.SurveyAccessLogDomainRepository;

/**
 * The access log of survey photos (survey-media spec A-6, 4.9): rows hold user, lead, resource type and id, time and
 * the server request id; never an IP, user agent, name, path or hash. Each row is written in a short READ COMMITTED
 * transaction of its own, never inside a list's snapshot.
 */
@Component
public class MediaAccessLogger {

    /** survey-media spec A-6: one MEDIA_LIST row per user and checklist per 10 minutes of list reads, a 204 poll included. */
    public static final Duration LIST_ROW_EVERY = Duration.ofMinutes(10);

    private final SurveyAccessLogDomainRepository accessLogs;
    private final MediaCountersRepository counters;
    private final IdGenerator<Long> snowflakes;
    private final MediaClock clock;
    private final TransactionTemplate ownTransaction;

    public MediaAccessLogger(SurveyAccessLogDomainRepository accessLogs, MediaCountersRepository counters,
                             @Qualifier("Snowflake") IdGenerator<Long> snowflakes, MediaClock clock,
                             PlatformTransactionManager transactionManager) {
        this.accessLogs = accessLogs;
        this.counters = counters;
        this.snowflakes = snowflakes;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** survey-media spec A-6, A-23: one row per delivered PREVIEW, FULL, download or ADMIN held read; resource = the photo. */
    public void contentRead(String userId, String leadId, AccessResourceType type, String mediaId) {
        Instant now = clock.now();
        ownTransaction.executeWithoutResult(status -> accessLogs.save(SurveyAccessLog.create(
                snowflakes.generateId(), leadId, userId, now, type, mediaId, RequestId.current())));
    }

    /** survey-media spec A-6: a list read; a row only when the user has none for the checklist in the last 10 minutes. */
    public void listRead(String userId, String leadId, String checklistId) {
        Instant now = clock.now();
        ownTransaction.executeWithoutResult(status -> {
            if (!counters.hasRecentMediaListRow(userId, checklistId, now.minus(LIST_ROW_EVERY))) {
                accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), leadId, userId, now,
                        AccessResourceType.MEDIA_LIST, checklistId, RequestId.current()));
            }
        });
    }
}
