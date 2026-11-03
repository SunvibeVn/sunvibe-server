package vn.sunvibe.survey.core.application.shared;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.survey.core.domain.model.media.MediaIncidentKind;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaIncidentDomainRepository;

/**
 * Records overdue deletions as incidents (survey-media spec A-18, BR-18 "có bản ghi sự cố tối thiểu"): each write in
 * its own short READ COMMITTED transaction outside the lock order of spec 5.4, with an error log of ids only.
 */
@Component
public class MediaIncidentRecorder {

    private static final Logger log = LoggerFactory.getLogger(MediaIncidentRecorder.class);
    private static final int LOGGED_IDS = 20;

    private final SurveyMediaIncidentDomainRepository incidents;
    private final TransactionTemplate own;

    public MediaIncidentRecorder(SurveyMediaIncidentDomainRepository incidents,
                                 PlatformTransactionManager transactionManager) {
        this.incidents = incidents;
        this.own = new TransactionTemplate(transactionManager);
        this.own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.own.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** An overdue deletion of the kind: the incident opened or counted, and an error log with the first ids. */
    public void overdue(MediaIncidentKind kind, Instant firstOverdueAt, List<String> ids, Instant appNow) {
        if (ids.isEmpty()) {
            return;
        }
        own.executeWithoutResult(status -> incidents.recordOverdue(kind, firstOverdueAt, ids.size(), appNow));
        log.error("Survey media deletion overdue: {} {} item(s), first ids {}", kind, ids.size(),
                ids.subList(0, Math.min(LOGGED_IDS, ids.size())));
    }

    /** A resuming sweep closes every open incident. */
    public void resolveAll(Instant appNow) {
        Integer resolved = own.execute(status -> incidents.resolveOpen(appNow));
        if (resolved != null && resolved > 0) {
            log.info("Survey media incidents resolved: {}", resolved);
        }
    }
}
