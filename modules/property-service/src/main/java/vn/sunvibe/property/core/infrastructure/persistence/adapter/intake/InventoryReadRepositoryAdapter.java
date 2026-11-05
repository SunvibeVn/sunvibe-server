package vn.sunvibe.property.core.infrastructure.persistence.adapter.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.policy.AddressNormalizer;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.shared.time.AppClock;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repository Implementation - scalar reads of properties and units (property-create intake spec 4.7, 4.10). Plain SQL, so an
 * odd legacy row cannot fail a read and no entity stays managed. Every value is a bound parameter.
 */
@Service
@RequiredArgsConstructor
public class InventoryReadRepositoryAdapter implements InventoryReadRepository {

    private static final String PROPERTY_COLUMNS = "select p.id, p.property_code, p.title, p.owner_id, p.asset_type, p.rental_layout,"
            + " p.ward_code, p.ward, p.street, p.address_norm, p.latitude, p.longitude, p.version, p.intake_id from properties p";
    private static final String UNIT_COLUMNS = "select u.id, u.property_id, u.unit_code, u.location_label, u.version,"
            + " u.availability_state from units u";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final RowMapper<PropertyFacts> PROPERTY = (row, number) -> new PropertyFacts(row.getString("id"),
            row.getString("property_code"), row.getString("title"), row.getString("owner_id"), row.getString("asset_type"),
            row.getString("rental_layout"), row.getString("ward_code"), row.getString("ward"), row.getString("street"),
            row.getString("address_norm"), row.getObject("latitude", Double.class),
            row.getObject("longitude", Double.class), row.getLong("version"), row.getString("intake_id"));
    private static final RowMapper<UnitFacts> UNIT = (row, number) -> new UnitFacts(row.getString("id"),
            row.getString("property_id"), row.getString("unit_code"), row.getString("location_label"), row.getLong("version"),
            row.getString("availability_state"));

    private final JdbcTemplate jdbc;

    @Override
    public Optional<PropertyFacts> property(String propertyId) {
        return jdbc.query(PROPERTY_COLUMNS + " where p.id = ?", PROPERTY, propertyId).stream().findFirst();
    }

    @Override
    public List<UnitFacts> unitsOf(String propertyId) {
        return jdbc.query(UNIT_COLUMNS + " where u.property_id = ? order by u.unit_code, u.id", UNIT, propertyId);
    }

    @Override
    public List<UnitFacts> units(Collection<String> unitIds) {
        if (unitIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", Collections.nCopies(unitIds.size(), "?"));
        return jdbc.query(UNIT_COLUMNS + " where u.id in (" + marks + ") order by u.unit_code, u.id", UNIT, unitIds.toArray());
    }

    /** Legacy rows are matched in Java: they stop growing at T-3, and section 9 counts them (spec 4.10). */
    @Override
    public List<PropertyFacts> propertiesOfWard(String wardCode, String normalizedWardName) {
        List<PropertyFacts> ward = new ArrayList<>(jdbc.query(PROPERTY_COLUMNS + " where p.ward_code = ?", PROPERTY, wardCode));
        jdbc.query(PROPERTY_COLUMNS + " where p.ward_code is null", PROPERTY).stream()
                .filter(legacy -> Objects.equals(AddressNormalizer.normalize(legacy.wardName()), normalizedWardName))
                .forEach(ward::add);
        return ward;
    }

    @Override
    public Optional<PropertyFacts> ownedByCode(String ownerId, String propertyCode) {
        return jdbc.query(PROPERTY_COLUMNS + " where p.owner_id = ? and p.property_code = ?", PROPERTY, ownerId, propertyCode)
                .stream().findFirst();
    }

    @Override
    public List<PropertyFacts> ownedPage(String ownerId, String wardCodeOrNull, String qOrNull, int offset, int limit) {
        List<Object> arguments = new ArrayList<>();
        String filter = ownedFilter(ownerId, wardCodeOrNull, qOrNull, arguments);
        arguments.add(limit);
        arguments.add(offset);
        return jdbc.query(PROPERTY_COLUMNS + filter + " order by p.id limit ? offset ?", PROPERTY, arguments.toArray());
    }

    @Override
    public long ownedCount(String ownerId, String wardCodeOrNull, String qOrNull) {
        List<Object> arguments = new ArrayList<>();
        String filter = ownedFilter(ownerId, wardCodeOrNull, qOrNull, arguments);
        return jdbc.queryForObject("select count(*) from properties p" + filter, Long.class, arguments.toArray());
    }

    private static String ownedFilter(String ownerId, String wardCodeOrNull, String qOrNull, List<Object> arguments) {
        StringBuilder filter = new StringBuilder(" where p.owner_id = ?");
        arguments.add(ownerId);
        if (wardCodeOrNull != null) {
            // spec 4.7: a legacy row has no ward code, so q alone can find it
            filter.append(qOrNull == null ? " and p.ward_code = ?" : " and (p.ward_code = ? or p.ward_code is null)");
            arguments.add(wardCodeOrNull);
        }
        if (qOrNull != null) {
            String pattern = "%" + escapeLike(qOrNull) + "%";
            // Punctuation alone normalizes to nothing, and an empty pattern would match every row
            String folded = AddressNormalizer.normalize(qOrNull);
            String normalized = folded.isBlank() ? pattern : "%" + escapeLike(folded) + "%";
            filter.append(" and (lower(p.street) like lower(?) escape '\\' or p.address_norm like ? escape '\\'"
                    + " or exists (select 1 from units u where u.property_id = p.id"
                    + " and lower(u.unit_code) like lower(?) escape '\\'))");
            arguments.add(pattern);
            arguments.add(normalized);
            arguments.add(pattern);
        }
        return filter.toString();
    }

    /** A-6: q is an escaped substring, so its wildcards match themselves. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public boolean ownsNow(String propertyId, String userId) {
        LocalDate today = LocalDate.ofInstant(AppClock.currentUTC(), VIETNAM);
        return jdbc.queryForObject("select count(*) from property_ownerships where property_id = ? and user_id = ?"
                        + " and relation_type = 'OWNER' and valid_from <= ? and (valid_to is null or valid_to >= ?)",
                Integer.class, propertyId, userId, Date.valueOf(today), Date.valueOf(today)) > 0;
    }

    @Override
    public long nextIntakeCodeNumber() {
        return jdbc.queryForObject("select nextval('property_intake_code_seq')", Long.class);
    }
}
