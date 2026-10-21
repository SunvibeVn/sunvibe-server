package vn.sunvibe.property.core.testsupport;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vn.sunvibe.property.core.domain.exception.PropertyAlreadyExistsException;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.enums.PropertyStatus;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;

import static org.junit.jupiter.api.Assertions.*;

/** The fake keeps the contract of PropertyRepositoryAdapter that service tests rely on (PropertyDomainRepository). */
class InMemoryPropertyDomainRepositoryTest {

    private final InMemoryPropertyDomainRepository repository = new InMemoryPropertyDomainRepository();

    @Test
    void save_staleCopy_isRefusedAndKeepsItsVersion() {
        Property property = property("DA-APT-001");
        repository.save(property);
        Property stale = copyAtVersion(property, 0L);
        repository.save(property);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> repository.save(stale));
        assertEquals(0L, stale.getVersion());
        assertEquals(1L, property.getVersion());
    }

    @Test
    void save_newPropertyWithTakenCode_isRefusedAsDuplicate() {
        repository.save(property("DA-APT-001"));
        Property second = property("DA-APT-001");

        assertThrows(PropertyAlreadyExistsException.class, () -> repository.save(second));
        assertNull(second.getVersion());
        assertEquals(1, repository.saved().size());
    }

    private static Property property(String code) {
        return Property.create(PropertyCode.of(code), "Studio", null, Location.of("1 Bach Dang", "Thach Thang",
                        "Hai Chau", null), PropertyType.STUDIO, 1, 1, 30.0, Money.vnd(5_000_000), Money.vnd(5_000_000),
                6, "01OWNER0000000000000000000", "0900000001");
    }

    private static Property copyAtVersion(Property p, Long version) {
        return Property.load(p.getId(), p.getPropertyCode(), p.getTitle(), p.getDescription(), p.getLocation(),
                p.getType(), p.getBedrooms(), p.getBathrooms(), p.getSquareMeters(), p.getMonthlyPrice(),
                p.getDepositPrice(), p.getMinRentMonths(), p.getOwnerId(), PropertyStatus.AVAILABLE, p.getCreatedAt(),
                p.getUpdatedAt(), p.getCreatedBy(), p.getUpdatedBy(), version);
    }
}
