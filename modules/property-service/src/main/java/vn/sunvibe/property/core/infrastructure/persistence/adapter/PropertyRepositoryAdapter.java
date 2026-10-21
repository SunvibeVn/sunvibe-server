package vn.sunvibe.property.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.exception.PropertyAlreadyExistsException;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.PropertyEntity;
import vn.sunvibe.property.core.infrastructure.persistence.repository.PropertyJpaRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.PropertyEntityMapper;

import java.util.Optional;

/**
 * Repository Implementation - PropertyRepositoryAdapter
 * <p>
 * This is the implementation of the domain PropertyDomainRepository.
 * It maps between domain models (Property) and JPA entities (PropertyEntity).
 * <p>
 * Note: This is the only place in the infrastructure layer where entities are visible.
 * <p>
 * After a save the aggregate carries the version the row has (property-units BR-14), so saving the same object again
 * is an update guarded by that version, and a stale version fails with an optimistic locking error. A property code
 * that is already taken fails with PropertyAlreadyExistsException, even when the caller's check missed it.
 */
@Service
@RequiredArgsConstructor
public class PropertyRepositoryAdapter implements PropertyDomainRepository {

    /** Unique constraint on properties.property_code (V202610150221__create_property_tables.sql). */
    static final String PROPERTY_CODE_CONSTRAINT = "idx_property_code";

    private final PropertyJpaRepository jpaRepository;
    private final PropertyEntityMapper mapper;

    @Override
    public void save(Property property) {
        if (property == null) {
            throw new IllegalArgumentException("Property cannot be null");
        }
        var entity = mapper.toEntity(property);
        // Flushed at once: a new property gets version 0 here, a loaded one has its stale-version check and its
        // version bump here, and a code taken by a concurrent create fails here as a duplicate (409) instead of at
        // commit (500). Unit and ownership, saved after the property, are still written at commit
        PropertyEntity saved;
        try {
            saved = jpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (violates(e, PROPERTY_CODE_CONSTRAINT)) {
                throw new PropertyAlreadyExistsException("Property code already exists.", e);
            }
            throw e;
        }
        property.markPersisted(saved.getVersion());
    }

    @Override
    public Optional<Property> findById(PropertyId id) {
        if (id == null) {
            throw new IllegalArgumentException("PropertyId cannot be null");
        }
        return jpaRepository.findById(id.getValue())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(PropertyCode code) {
        if (code == null) {
            throw new IllegalArgumentException("PropertyCode cannot be null");
        }
        return jpaRepository.existsByPropertyCode(code.getValue());
    }

    /** Whether the failure is a violation of the named constraint, as Hibernate reports it in the cause chain. */
    private static boolean violates(DataIntegrityViolationException e, String constraintName) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
