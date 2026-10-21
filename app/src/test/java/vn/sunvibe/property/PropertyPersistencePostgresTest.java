package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.property.core.domain.exception.PropertyAlreadyExistsException;
import vn.sunvibe.property.core.domain.model.aggregate.Property;
import vn.sunvibe.property.core.domain.model.amenity.PropertyAmenityApply;
import vn.sunvibe.property.core.domain.model.document.PropertyDocument;
import vn.sunvibe.property.core.domain.model.rule.PropertyRuleApply;
import vn.sunvibe.property.core.domain.model.enums.PropertyType;
import vn.sunvibe.property.core.domain.model.image.PropertyImage;
import vn.sunvibe.property.core.domain.model.vo.Location;
import vn.sunvibe.property.core.domain.model.vo.Money;
import vn.sunvibe.property.core.domain.model.vo.PropertyCode;
import vn.sunvibe.property.core.domain.repository.AmenityTypeDomainRepository;
import vn.sunvibe.property.core.domain.repository.DocumentTypeDomainRepository;
import vn.sunvibe.property.core.domain.repository.PropertyDomainRepository;
import vn.sunvibe.property.core.domain.repository.RuleCategoryDomainRepository;

/**
 * Version lifecycle of a property on a real PostgreSQL 17 (property-units BR-14): after a save the aggregate carries
 * the version the row has, so its next save is an update guarded by that version, and a stale copy is refused
 * without changing the row. A new property is written by save itself, so a property code taken meanwhile fails there
 * as a duplicate (D-AI-2 of the 2026-10-20 refactor plan); the caller's transaction still decides. The annotations are those of {@link vn.sunvibe.FlywayPostgresMigrationTest}, so
 * both share one context. Needs Docker, like that test.
 * <p>
 * Every test uses its own property code: the database lives as long as the shared context.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
// The container's connection details replace the H2 datasource of application-test.yml
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class PropertyPersistencePostgresTest {

	private static final String OWNER_ID = "01OWNER0000000000000000000";
	private static final String CREATED_BY = "0900000001";
	private static final String UPDATED_BY = "0900000002";

	@Autowired
	private PropertyDomainRepository propertyRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RuleCategoryDomainRepository ruleCategories;

	@Autowired
	private AmenityTypeDomainRepository amenityTypes;

	@Autowired
	private DocumentTypeDomainRepository documentTypes;


	/**
	 * A new property is written by save itself (D-AI-2: a duplicate code then fails in save), inside the caller's
	 * transaction: the row goes away again when that transaction rolls back.
	 */
	@Test
	void save_newProperty_givesItVersionZeroAndWritesTheRowInTheTransaction() {
		Property property = newProperty("WT-VER-001");

		// JdbcTemplate runs on the connection of the JPA transaction, so it sees what Hibernate has flushed so far
		Integer rowsInTransaction = transaction().execute(status -> {
			propertyRepository.save(property);
			assertEquals(0L, property.getVersion());
			status.setRollbackOnly();
			return rowCount("WT-VER-001");
		});

		assertEquals(1, rowsInTransaction, "save writes the row");
		assertEquals(0, rowCount("WT-VER-001"), "the rollback removes it");
	}

	/** D-AI-2: a code taken by another property is a duplicate, whether or not the caller checked it first. */
	@Test
	void save_newPropertyWithTakenCode_isRefusedAsDuplicate() {
		propertyRepository.save(newProperty("WT-VER-006"));
		Property second = newProperty("WT-VER-006");

		assertThrows(PropertyAlreadyExistsException.class, () -> propertyRepository.save(second));
		assertNull(second.getVersion(), "a refused save leaves the version alone");
		assertEquals(1, rowCount("WT-VER-006"));
	}

	@Test
	void save_loadedAndChanged_bumpsTheVersionOfObjectAndRow() {
		Property saved = newProperty("WT-VER-002");
		propertyRepository.save(saved);
		Property loaded = load(saved);

		loaded.updateBasicInfo("Changed title", null, null, null, null, UPDATED_BY);
		propertyRepository.save(loaded);

		assertEquals(1L, loaded.getVersion());
		assertEquals(1L, rowVersion("WT-VER-002"));
		assertEquals("Changed title", jdbc.queryForObject(
				"SELECT title FROM properties WHERE property_code = ?", String.class, "WT-VER-002"));
		assertEquals(1, jdbc.queryForObject(
				"SELECT count(*) FROM property_images WHERE property_id = ?", Integer.class, saved.getId().getValue()),
				"the image is kept");
	}

	/** A saved update writes the property back with all its children: a stored price row is not orphaned. */
	@Test
	void save_loadedAndChanged_keepsTheStoredPriceHistory() {
		Property saved = newProperty("WT-VER-005");
		propertyRepository.save(saved);
		jdbc.update("INSERT INTO property_prices (id, property_id, monthly_price, deposit_price, effective_date, "
				+ "end_date, reason, is_current) VALUES (?, ?, 5000000, 5000000, DATE '2026-10-01', NULL, NULL, TRUE)",
				"0b0c7c2e-1f7a-4c39-9a51-3c7e2f1d9a01", saved.getId().getValue());
		Property loaded = load(saved);

		loaded.updateBasicInfo("Changed title", null, null, null, null, UPDATED_BY);
		propertyRepository.save(loaded);

		assertEquals(1, loaded.getPriceHistories().size());
		assertEquals(1, jdbc.queryForObject(
				"SELECT count(*) FROM property_prices WHERE property_id = ?", Integer.class, saved.getId().getValue()));
	}

	/**
	 * A loaded save writes rules, amenities and documents back as the same rows (same ids, same master references), not
	 * as deleted and re-inserted ones. Master ids from the seed: rule 1 (PETS), amenity 10 (WiFi), document type 1.
	 */
	@Test
	void save_loadedWithRuleAmenityAndDocument_keepsTheirRows() {
		Property saved = newProperty("WT-VER-007");
		saved.applyRule(PropertyRuleApply.createDefault(saved.getId(), ruleCategories.findById(1).orElseThrow()));
		saved.applyAmenity(PropertyAmenityApply.included(saved.getId(), amenityTypes.findById(10).orElseThrow()));
		saved.addDocument(PropertyDocument.create(saved.getId(), documentTypes.findById(1).orElseThrow(),
				"/docs/deed.pdf", null, LocalDate.of(2026, 1, 5), "Da Nang", CREATED_BY));
		propertyRepository.save(saved);
		String id = saved.getId().getValue();
		Map<String, Object> ruleBefore = jdbc.queryForMap("SELECT * FROM property_rules WHERE property_id = ?", id);
		Map<String, Object> amenityBefore = jdbc.queryForMap("SELECT * FROM property_amenity WHERE property_id = ?", id);
		Map<String, Object> documentBefore = jdbc.queryForMap(
				"SELECT * FROM property_document WHERE property_id = ?", id);
		Property loaded = load(saved);

		loaded.updateBasicInfo("Changed title", null, null, null, null, UPDATED_BY);
		propertyRepository.save(loaded);

		assertEquals(ruleBefore, jdbc.queryForMap("SELECT * FROM property_rules WHERE property_id = ?", id));
		assertEquals(amenityBefore, jdbc.queryForMap("SELECT * FROM property_amenity WHERE property_id = ?", id));
		assertEquals(documentBefore, jdbc.queryForMap("SELECT * FROM property_document WHERE property_id = ?", id));
		assertEquals(1, loaded.getAppliedRules().size());
		assertEquals("PETS", loaded.getAppliedRules().getFirst().getRuleCategory().getName());
		assertEquals(1, loaded.getAmenities().size());
		assertEquals(1, loaded.getDocuments().size());
	}

	@Test
	void save_staleCopy_isRefusedAndLeavesTheRow() {
		Property saved = newProperty("WT-VER-003");
		propertyRepository.save(saved);
		Property first = load(saved);
		Property stale = load(saved);
		first.updateBasicInfo("First change", null, null, null, null, UPDATED_BY);
		propertyRepository.save(first);

		stale.updateBasicInfo("Stale change", null, null, null, null, UPDATED_BY);

		assertThrows(ObjectOptimisticLockingFailureException.class, () -> propertyRepository.save(stale));
		assertEquals(0L, stale.getVersion(), "a refused save leaves the version alone");
		assertEquals(1L, rowVersion("WT-VER-003"));
		assertEquals("First change", jdbc.queryForObject(
				"SELECT title FROM properties WHERE property_code = ?", String.class, "WT-VER-003"));
	}

	@Test
	void save_sameNewObjectAgain_updatesTheRowInsteadOfInsertingIt() {
		Property property = newProperty("WT-VER-004");
		assertNull(property.getVersion());
		propertyRepository.save(property);

		property.updateBasicInfo("Saved twice", null, null, null, null, UPDATED_BY);
		propertyRepository.save(property);

		assertEquals(1L, property.getVersion());
		assertEquals(1, rowCount("WT-VER-004"));
		assertEquals(1L, rowVersion("WT-VER-004"));
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	/** In a transaction of its own: the property mapper reads the lazy collections of the entity. */
	private Property load(Property saved) {
		return transaction().execute(status -> propertyRepository.findById(saved.getId()).orElseThrow());
	}

	private Integer rowCount(String code) {
		return jdbc.queryForObject("SELECT count(*) FROM properties WHERE property_code = ?", Integer.class, code);
	}

	private Long rowVersion(String code) {
		return jdbc.queryForObject("SELECT version FROM properties WHERE property_code = ?", Long.class, code);
	}

	/** A property as property create stores it, with one primary image. */
	private static Property newProperty(String code) {
		Property property = Property.create(PropertyCode.of(code), "Version lifecycle", "Studio near the beach",
				Location.of("1 Bach Dang", "Thach Thang", "Hai Chau", null), PropertyType.STUDIO, 1, 1, 35.5,
				Money.vnd(5_000_000), Money.vnd(5_000_000), 6, OWNER_ID, CREATED_BY);
		property.addImage(PropertyImage.createPrimary(property.getId(), "https://img/1.jpg", null, null, CREATED_BY));
		return property;
	}
}
