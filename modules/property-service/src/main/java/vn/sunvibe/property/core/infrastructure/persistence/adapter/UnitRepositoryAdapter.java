package vn.sunvibe.property.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.exception.UnitCodeTakenException;
import vn.sunvibe.property.core.domain.model.unit.Unit;
import vn.sunvibe.property.core.domain.repository.UnitDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.UnitEntity;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.UnitEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.UnitJpaRepository;

import java.util.Locale;
import java.util.Set;

/**
 * Repository Implementation - UnitRepositoryAdapter
 * <p>
 * Implements the domain UnitDomainRepository: maps the Unit domain model to UnitEntity and saves it.
 * Only save is needed so far.
 */
@Service
@RequiredArgsConstructor
public class UnitRepositoryAdapter implements UnitDomainRepository {

    /** The case-sensitive constraint of property-units and the index of property-create intake spec 5.2 file 3. */
    private static final Set<String> UNIT_CODE_CONSTRAINTS = Set.of("uk_units_property_id_unit_code",
            "uq_units_property_unit_code_key");

    private final UnitJpaRepository jpaRepository;
    private final UnitEntityMapper mapper;

    @Override
    public void save(Unit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("Unit cannot be null");
        }
        var entity = mapper.toEntity(unit);
        // Flushed at once: a new unit gets version 0 here, a loaded one has its stale-version check and version bump
        // here, and a code the property already has fails here (property-create intake spec 5.1, property-create BR-8), not at commit
        UnitEntity saved;
        try {
            saved = jpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (violatesUnitCode(e)) {
                throw new UnitCodeTakenException(e);
            }
            throw e;
        }
        unit.markPersisted(saved.getVersion());
    }

    private static boolean violatesUnitCode(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return UNIT_CODE_CONSTRAINTS.contains(String.valueOf(violation.getConstraintName()).toLowerCase(Locale.ROOT));
            }
        }
        return false;
    }
}
