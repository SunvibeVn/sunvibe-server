package vn.sunvibe.survey.core.infrastructure.persistence;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentHistoryDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistSaveDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistTakeoverDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyResumptionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveySecurityEventDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.SurveyAccessLogRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.SurveyAppointmentHistoryRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.SurveyChecklistSaveRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.SurveyChecklistTakeoverRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.SurveyResumptionRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.SurveySecurityEventRepositoryAdapter;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAccessLogEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentEventEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentRevisionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistSaveEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistTakeoverEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyResumptionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveySecurityEventEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The survey histories (revisions, events, resumptions, survey access log) are insert-only (survey BR-5,
 * survey-schedule spec I-6, W15-29): their entities are Hibernate-immutable, their ports only save and find, and
 * their adapters offer no way to change or remove a row.
 */
class InsertOnlyRepositoriesTest {

    private static final List<String> MUTATING_PREFIXES = List.of("update", "delete", "remove", "merge");

    @Test
    void entities_ofTheFourHistories_areHibernateImmutable() {
        for (Class<?> entity : List.of(SurveyAppointmentRevisionEntity.class, SurveyAppointmentEventEntity.class,
                SurveyResumptionEntity.class, SurveyAccessLogEntity.class, SurveyChecklistTakeoverEntity.class,
                SurveyChecklistSaveEntity.class, SurveySecurityEventEntity.class)) {
            assertNotNull(entity.getAnnotation(Immutable.class), entity.getSimpleName() + " must be @Immutable");
        }
    }

    @Test
    void ports_ofTheHistories_declareOnlySaveAndFindMethods() {
        for (Class<?> port : List.of(SurveyAppointmentHistoryDomainRepository.class,
                SurveyResumptionDomainRepository.class, SurveyAccessLogDomainRepository.class,
                SurveyChecklistTakeoverDomainRepository.class, SurveyChecklistSaveDomainRepository.class,
                SurveySecurityEventDomainRepository.class)) {
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
                SurveySecurityEventRepositoryAdapter.class)) {
            Arrays.stream(adapter.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .forEach(method -> assertFalse(
                            MUTATING_PREFIXES.stream().anyMatch(prefix -> method.getName().startsWith(prefix)),
                            adapter.getSimpleName() + "." + method.getName() + " changes or removes history"));
        }
    }
}
