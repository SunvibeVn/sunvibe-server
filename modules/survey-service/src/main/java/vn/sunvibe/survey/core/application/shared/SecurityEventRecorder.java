package vn.sunvibe.survey.core.application.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.repository.SurveySecurityEventDomainRepository;

import java.time.temporal.ChronoUnit;

/**
 * Writes the security events of the checklist boundary (survey-checklist BR-20, E-2, spec 4.10, A-24, PD-6): after the
 * refused request's transaction has completed (also a rollback), in a short transaction of its own, so the refusal's
 * rollback cannot remove it. Outside a transaction it writes at once. A failing write is logged with ids only and never
 * changes the answer.
 */
@Component
public class SecurityEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventRecorder.class);

    private final SurveySecurityEventDomainRepository events;
    private final TransactionTemplate ownTransaction;
    private final IdGenerator<Long> snowflakes;

    public SecurityEventRecorder(SurveySecurityEventDomainRepository events,
                                 PlatformTransactionManager transactionManager,
                                 @Qualifier("Snowflake") IdGenerator<Long> snowflakes) {
        this.events = events;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.snowflakes = snowflakes;
    }

    /** A staff caller was refused on an existing resource of the lead. */
    public void refused(String userId, String leadId, SecurityResourceType resourceType, String resourceId,
                        SecurityAction action, SecurityOutcome outcome) {
        SurveySecurityEvent event = new SurveySecurityEvent(snowflakes.generateId(), userId, leadId, resourceType,
                resourceId, action, outcome, CorrelationId.current(),
                AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    write(event);
                }
            });
        } else {
            write(event);
        }
    }

    private void write(SurveySecurityEvent event) {
        try {
            ownTransaction.executeWithoutResult(status -> events.save(event));
        } catch (RuntimeException e) {
            // Ids only: the answer stays the plain 404 or 403 (spec A-24)
            log.error("Could not write survey security event {} for lead {}", event.id(), event.leadId(), e);
        }
    }
}
