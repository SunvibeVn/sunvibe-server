package vn.sunvibe.survey.core.infrastructure.persistence;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.repository.common.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.appointment.SurveyAppointmentHistoryDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDecisionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistEvidenceDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistRequalificationDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSaveDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSubmissionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistTakeoverDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaVariantDomainRepository;
import vn.sunvibe.survey.core.domain.repository.appointment.SurveyResumptionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.common.SurveySecurityEventDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.common.SurveyAccessLogRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.appointment.SurveyAppointmentHistoryRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist.SurveyChecklistDecisionRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist.SurveyChecklistEvidenceRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist.SurveyChecklistRequalificationRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist.SurveyChecklistSaveRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist.SurveyChecklistSubmissionRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist.SurveyChecklistTakeoverRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.media.SurveyMediaVariantRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.appointment.SurveyResumptionRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.common.SurveySecurityEventRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.common.SurveyAccessLogEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.appointment.SurveyAppointmentEventEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.appointment.SurveyAppointmentRevisionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistDecisionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistEvidenceEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistEvidenceRejectionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistRequalificationEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistSaveEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistSubmissionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistTakeoverEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.media.SurveyMediaVariantEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.appointment.SurveyResumptionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.common.SurveySecurityEventEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The survey histories (revisions, events, resumptions, survey access log) and the photo variants (survey-media
 * spec 5.2) are insert-only (survey BR-5,
 * survey-schedule spec I-6, W15-29): their entities are Hibernate-immutable, their ports only save and find, and
 * their adapters offer no way to change or remove a row.
 */
class InsertOnlyRepositoriesTest {

    private static final List<String> MUTATING_PREFIXES = List.of("update", "delete", "remove", "merge");

    @Test
    void entities_ofTheFourHistories_areHibernateImmutable() {
        for (Class<?> entity : List.of(SurveyAppointmentRevisionEntity.class, SurveyAppointmentEventEntity.class,
                SurveyResumptionEntity.class, SurveyAccessLogEntity.class, SurveyChecklistTakeoverEntity.class,
                SurveyChecklistSaveEntity.class, SurveySecurityEventEntity.class, SurveyMediaVariantEntity.class,
                SurveyChecklistSubmissionEntity.class, SurveyChecklistEvidenceEntity.class,
                SurveyChecklistDecisionEntity.class, SurveyChecklistEvidenceRejectionEntity.class,
                SurveyChecklistRequalificationEntity.class)) {
            assertNotNull(entity.getAnnotation(Immutable.class), entity.getSimpleName() + " must be @Immutable");
        }
    }

    @Test
    void ports_ofTheHistories_declareOnlySaveAndFindMethods() {
        for (Class<?> port : List.of(SurveyAppointmentHistoryDomainRepository.class,
                SurveyResumptionDomainRepository.class, SurveyAccessLogDomainRepository.class,
                SurveyChecklistTakeoverDomainRepository.class, SurveyChecklistSaveDomainRepository.class,
                SurveySecurityEventDomainRepository.class, SurveyMediaVariantDomainRepository.class,
                SurveyChecklistSubmissionDomainRepository.class, SurveyChecklistEvidenceDomainRepository.class,
                SurveyChecklistDecisionDomainRepository.class,
                SurveyChecklistRequalificationDomainRepository.class)) {
            Method[] methods = port.getDeclaredMethods();
            assertTrue(methods.length > 0, port.getSimpleName() + " declares no method");
            for (Method method : methods) {
                assertTrue(method.getName().startsWith("save") || method.getName().startsWith("find"),
                        port.getSimpleName() + "." + method.getName() + " is neither save nor find");
            }
        }
    }

    @Test
    void adapters_ofTheHistories_declareNoUpdateDeleteRemoveOrMerge() {
        for (Class<?> adapter : List.of(SurveyAppointmentHistoryRepositoryAdapter.class,
                SurveyResumptionRepositoryAdapter.class, SurveyAccessLogRepositoryAdapter.class,
                SurveyChecklistTakeoverRepositoryAdapter.class, SurveyChecklistSaveRepositoryAdapter.class,
                SurveySecurityEventRepositoryAdapter.class, SurveyMediaVariantRepositoryAdapter.class,
                SurveyChecklistSubmissionRepositoryAdapter.class, SurveyChecklistEvidenceRepositoryAdapter.class,
                SurveyChecklistDecisionRepositoryAdapter.class,
                SurveyChecklistRequalificationRepositoryAdapter.class)) {
            Arrays.stream(adapter.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .forEach(method -> assertFalse(
                            MUTATING_PREFIXES.stream().anyMatch(prefix -> method.getName().startsWith(prefix)),
                            adapter.getSimpleName() + "." + method.getName() + " changes or removes history"));
        }
    }
}
