package vn.sunvibe.acquisition.core.infrastructure.persistence;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadAccessLogDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.SaleTerritoryChangeDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.persistence.adapter.OwnerLeadAccessLogRepositoryAdapter;
import vn.sunvibe.acquisition.core.infrastructure.persistence.adapter.OwnerLeadActivityRepositoryAdapter;
import vn.sunvibe.acquisition.core.infrastructure.persistence.adapter.SaleTerritoryChangeRepositoryAdapter;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadAccessLogEntity;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.OwnerLeadActivityEntity;
import vn.sunvibe.acquisition.core.infrastructure.persistence.entity.SaleTerritoryChangeEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three histories (lead activity, lead access log, ward assignment change) are insert-only (acquisition module
 * brief BR-4, acquisition-owner-lead-queue Q9-14): their entities are Hibernate-immutable, their ports only save and
 * find, and their adapters offer no way to change or remove a row.
 */
class InsertOnlyRepositoriesTest {

    private static final List<String> MUTATING_PREFIXES = List.of("update", "delete", "remove", "merge");

    @Test
    void entities_ofTheThreeHistories_areHibernateImmutable() {
        for (Class<?> entity : List.of(OwnerLeadActivityEntity.class, OwnerLeadAccessLogEntity.class,
                SaleTerritoryChangeEntity.class)) {
            assertNotNull(entity.getAnnotation(Immutable.class), entity.getSimpleName() + " must be @Immutable");
        }
    }

    @Test
    void ports_ofTheThreeHistories_declareOnlySaveAndFindMethods() {
        for (Class<?> port : List.of(OwnerLeadActivityDomainRepository.class, OwnerLeadAccessLogDomainRepository.class,
                SaleTerritoryChangeDomainRepository.class)) {
            Method[] methods = port.getDeclaredMethods();
            assertTrue(methods.length > 0, port.getSimpleName() + " declares no method");
            for (Method method : methods) {
                assertTrue(method.getName().startsWith("save") || method.getName().startsWith("find"),
                        port.getSimpleName() + "." + method.getName() + " is neither save nor find");
            }
        }
    }

    @Test
    void adapters_ofTheThreeHistories_declareNoUpdateDeleteRemoveOrMerge() {
        for (Class<?> adapter : List.of(OwnerLeadActivityRepositoryAdapter.class,
                OwnerLeadAccessLogRepositoryAdapter.class, SaleTerritoryChangeRepositoryAdapter.class)) {
            Arrays.stream(adapter.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .forEach(method -> assertFalse(
                            MUTATING_PREFIXES.stream().anyMatch(prefix -> method.getName().startsWith(prefix)),
                            adapter.getSimpleName() + "." + method.getName() + " changes or removes history"));
        }
    }
}
