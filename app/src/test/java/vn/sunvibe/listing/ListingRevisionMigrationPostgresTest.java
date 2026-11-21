package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The revision content columns, their CHECKs and the fee table on a real PostgreSQL (listing-publish E06-S2 spec 5.3,
 * E06S2-D15, plan E06S2-P2; RD-17). The annotations, autowired fields, {@code RANDOM}, {@code created} with its
 * cleanup, and the helpers {@code insertListingRow}, {@code insertRevision}, {@code refused}, {@code tx}, {@code code}
 * and {@code now} are copied as written from {@link ListingPersistencePostgresTest} (:51-82, :268-304, plan
 * E06S2-P1), so this class shares its context; the cleanup gains only the delete of the fee rows, before their
 * revisions. Every row it writes hangs under a listing in {@code created}. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingRevisionMigrationPostgresTest {

	private static final SecureRandom RANDOM = new SecureRandom();

	/** E06-S1's two CHECKs, the eleven of migration 1 and the five of the fee table (plan E06S2-P2). */
	private static final Set<String> CHECKS = Set.of("ck_listing_revisions_number", "ck_listing_revisions_status",
			"ck_listing_revisions_feature_furnishing", "ck_listing_revisions_feature_washing_machine",
			"ck_listing_revisions_feature_kitchen", "ck_listing_revisions_feature_pets",
			"ck_listing_revisions_terms_source", "ck_listing_revisions_terms_shape", "ck_listing_revisions_terms_deposit",
			"ck_listing_revisions_unit_place", "ck_listing_revisions_unit_asset_type", "ck_listing_revisions_submission",
			"ck_listing_revisions_pending_content", "ck_listing_revision_fees_fee_no", "ck_listing_revision_fees_basis",
			"ck_listing_revision_fees_charge_unit", "ck_listing_revision_fees_amount", "ck_listing_revision_fees_name");

	private static final List<String> MIGRATIONS = List.of("V202611201100__add_listing_revision_content.sql",
			"V202611201101__create_listing_revision_fees.sql", "V202611201102__validate_listing_revision_checks.sql");

	@Autowired
	private ListingDomainRepository listings;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private IdGenerator<String> ids;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<String> created = new ArrayList<>();

	@AfterEach
	void deleteRows() {
		for (String id : created) {
			jdbc.update("DELETE FROM listing_revision_fees WHERE revision_id IN"
					+ " (SELECT id FROM listing_revisions WHERE listing_id = ?)", id);
			jdbc.update("DELETE FROM listing_history WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listings WHERE id = ?", id);
		}
		created.clear();
	}

	@Test
	void checks_pendingWithoutContent_refused() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");
		Map<String, Object> noUnit = complete();
		noUnit.remove("unit_bedrooms");

		assertAll(
				refused("ck_listing_revisions_pending_content", () -> insertRevision(listing, 1, "PENDING_REVIEW")),
				refused("ck_listing_revisions_pending_content", () -> insertRow(listing, 1, "PENDING_REVIEW", noUnit)),
				refused("ck_listing_revisions_pending_content", () -> insertRow(listing, 1, "PENDING_REVIEW",
						without(complete(), "title"))),
				refused("ck_listing_revisions_pending_content", () -> insertRow(listing, 1, "PENDING_REVIEW",
						without(complete(), "submitted_at", "submitted_by"))));
		insertRow(listing, 1, "PENDING_REVIEW", complete());
		assertEquals(1, count("SELECT count(*) FROM listing_revisions WHERE listing_id = ?", listing));
	}

	@Test
	void checks_optionFeatureOffList_refused() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");

		assertAll(
				refused("ck_listing_revisions_feature_furnishing",
						() -> insertRow(listing, 1, "DRAFT", Map.of("feature_furnishing", "RENT"))),
				refused("ck_listing_revisions_feature_washing_machine",
						() -> insertRow(listing, 1, "DRAFT", Map.of("feature_washing_machine", "OWN"))),
				refused("ck_listing_revisions_feature_kitchen",
						() -> insertRow(listing, 1, "DRAFT", Map.of("feature_kitchen", "NONE"))),
				refused("ck_listing_revisions_feature_pets",
						() -> insertRow(listing, 1, "DRAFT", Map.of("feature_pets", "CATS"))));
		insertRow(listing, 1, "DRAFT", Map.of("feature_furnishing", "BASIC", "feature_washing_machine", "SHARED",
				"feature_kitchen", "PRIVATE", "feature_pets", "NOT_ALLOWED"));
	}

	@Test
	void checks_halfFilledTerms_refused() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");

		assertAll(
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						without(terms(), "terms_monthly_rent"))),
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						without(terms(), "terms_captured_at"))),
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						without(terms(), "terms_deposit_basis", "terms_deposit_months"))),
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						without(terms(), "terms_minimum_term_months"))),
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						without(terms(), "terms_source"))),
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						with(terms(), "terms_monthly_rent", 0))),
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						with(terms(), "terms_minimum_term_months", 121))),
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						with(terms(), "terms_payment_period_months", 13))),
				refused("ck_listing_revisions_terms_source", () -> insertRow(listing, 1, "DRAFT",
						with(terms(), "terms_source", "LATER"))));
		insertRow(listing, 1, "DRAFT", terms());
	}

	@Test
	void checks_pendingDateWithoutPendingStart_refused() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");
		Date start = Date.valueOf(LocalDate.parse("2026-12-01"));

		assertAll(
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						with(terms(), "terms_pending_start_date", start))),
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						with(terms(), "terms_source", "PENDING_START"))),
				refused("ck_listing_revisions_terms_shape", () -> insertRow(listing, 1, "DRAFT",
						Map.of("terms_pending_start_date", start))));
		insertRow(listing, 1, "DRAFT", with(with(terms(), "terms_source", "PENDING_START"),
				"terms_pending_start_date", start));
	}

	@Test
	void checks_depositShape_refused() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");

		assertAll(
				refused("ck_listing_revisions_terms_deposit", () -> insertRow(listing, 1, "DRAFT",
						with(terms(), "terms_deposit_amount", 100))),
				refused("ck_listing_revisions_terms_deposit", () -> insertRow(listing, 1, "DRAFT",
						with(terms(), "terms_deposit_months", 13))),
				refused("ck_listing_revisions_terms_deposit", () -> insertRow(listing, 1, "DRAFT",
						with(without(with(terms(), "terms_deposit_basis", "AMOUNT"), "terms_deposit_months"),
								"terms_deposit_amount", -1))),
				refused("ck_listing_revisions_terms_deposit", () -> insertRow(listing, 1, "DRAFT",
						with(with(terms(), "terms_deposit_basis", "AMOUNT"), "terms_deposit_amount", 5_000_000))),
				refused("ck_listing_revisions_terms_deposit", () -> insertRow(listing, 1, "DRAFT",
						with(terms(), "terms_deposit_basis", "WEEKS"))));
		insertRow(listing, 1, "DRAFT", with(without(with(terms(), "terms_deposit_basis", "AMOUNT"),
				"terms_deposit_months"), "terms_deposit_amount", 0));
	}

	@Test
	void checks_halfPlace_refused() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");

		assertAll(
				refused("ck_listing_revisions_unit_place", () -> insertRow(listing, 1, "DRAFT",
						Map.of("unit_province_code", "48"))),
				refused("ck_listing_revisions_unit_place", () -> insertRow(listing, 1, "DRAFT",
						without(place(), "unit_ward_name"))));
		insertRow(listing, 1, "DRAFT", place());
	}

	@Test
	void checks_assetTypeOffList_refused() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");

		assertAll(refused("ck_listing_revisions_unit_asset_type", () -> insertRow(listing, 1, "DRAFT",
				Map.of("unit_asset_type", "CASTLE"))));
		insertRow(listing, 1, "DRAFT", Map.of("unit_asset_type", "WHOLE_BUILDING"));
	}

	@Test
	void checks_halfSubmission_refused() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");

		assertAll(
				refused("ck_listing_revisions_submission", () -> insertRow(listing, 1, "DRAFT",
						Map.of("submitted_at", Timestamp.from(now())))),
				refused("ck_listing_revisions_submission", () -> insertRow(listing, 1, "DRAFT",
						Map.of("submitted_by", ids.generateId()))));
	}

	@Test
	void checks_feeOffE05Rules_refused() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");
		String revision = insertRow(listing, 1, "DRAFT", terms());

		assertAll(
				refused("ck_listing_revision_fees_fee_no", () -> insertFee(revision, 0, "Fee", "INCLUDED", null, null)),
				refused("ck_listing_revision_fees_fee_no", () -> insertFee(revision, 21, "Fee", "INCLUDED", null, null)),
				refused("ck_listing_revision_fees_basis", () -> insertFee(revision, 1, "Fee", "FREE", null, null)),
				refused("ck_listing_revision_fees_amount", () -> insertFee(revision, 1, "Fee", "AMOUNT", 0L, "PER_MONTH")),
				refused("ck_listing_revision_fees_amount", () -> insertFee(revision, 1, "Fee", "AMOUNT", 1_000L, null)),
				refused("ck_listing_revision_fees_amount", () -> insertFee(revision, 1, "Fee", "INCLUDED", 1_000L, null)),
				refused("ck_listing_revision_fees_charge_unit",
						() -> insertFee(revision, 1, "Fee", "AMOUNT", 1_000L, "PER_DAY")),
				refused("ck_listing_revision_fees_name", () -> insertFee(revision, 1, "", "INCLUDED", null, null)));
		insertFee(revision, 1, "Điện", "AMOUNT", 3_500L, "PER_KWH");
		insertFee(revision, 20, "Internet", "INCLUDED", null, null);
	}

	@Test
	void checks_contentlessDraftOfE06S1_accepted() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");

		insertRevision(listing, 1, "DRAFT");

		Map<String, Object> row = jdbc.queryForMap("SELECT title, feature_furnishing, feature_balcony,"
				+ " feature_no_shared_landlord, terms_source, unit_bedrooms, submitted_at FROM listing_revisions"
				+ " WHERE listing_id = ?", listing);
		assertEquals(null, row.get("title"));
		assertEquals(null, row.get("feature_furnishing"));
		assertEquals(false, row.get("feature_balcony"));
		assertEquals(false, row.get("feature_no_shared_landlord"));
		assertEquals(null, row.get("terms_source"));
		assertEquals(null, row.get("unit_bedrooms"));
		assertEquals(null, row.get("submitted_at"));
	}

	@Test
	void checks_everyCheck_validated() {
		List<Map<String, Object>> rows = jdbc.queryForList("SELECT conname, convalidated FROM pg_constraint"
				+ " WHERE contype = 'c' AND conrelid IN ('listing_revisions'::regclass, 'listing_revision_fees'::regclass)");

		assertEquals(CHECKS, rows.stream().map(row -> (String) row.get("conname")).collect(Collectors.toSet()));
		assertEquals(CHECKS.size(), rows.size());
		for (Map<String, Object> row : rows) {
			assertEquals(true, row.get("convalidated"), String.valueOf(row.get("conname")));
		}
	}

	@Test
	void deleteRevision_feeRows_cascade() {
		String listing = insertListingRow(code().value(), "LONG_TERM_RENT", "UNPUBLISHED");
		String revision = insertRow(listing, 1, "DRAFT", terms());
		insertFee(revision, 1, "Điện", "AMOUNT", 3_500L, "PER_KWH");
		insertFee(revision, 2, "Internet", "INCLUDED", null, null);

		jdbc.update("DELETE FROM listing_revisions WHERE id = ?", revision);

		assertEquals(0, count("SELECT count(*) FROM listing_revision_fees WHERE revision_id = ?", revision));
	}

	@Test
	void migrations_eachFile_setsLockTimeoutFirst() throws IOException {
		for (String migration : MIGRATIONS) {
			try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/migration/" + migration)) {
				assertNotNull(in, migration);
				String firstStatement = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
						.map(String::strip)
						.filter(line -> !line.isEmpty() && !line.startsWith("--"))
						.findFirst()
						.orElse("");
				assertEquals("SET LOCAL lock_timeout = '5s';", firstStatement, migration);
			}
		}
		assertFalse(MIGRATIONS.isEmpty());
	}

	/** Complete terms of a version in effect, as the terms snapshot writes them. */
	private static Map<String, Object> terms() {
		Map<String, Object> columns = new LinkedHashMap<>();
		columns.put("terms_source", "IN_EFFECT");
		columns.put("terms_captured_at", Timestamp.from(now()));
		columns.put("terms_monthly_rent", 8_000_000L);
		columns.put("terms_deposit_basis", "MONTHS");
		columns.put("terms_deposit_months", 2);
		columns.put("terms_minimum_term_months", 6);
		columns.put("terms_payment_period_months", 1);
		return columns;
	}

	private static Map<String, Object> place() {
		Map<String, Object> columns = new LinkedHashMap<>();
		columns.put("unit_province_code", "48");
		columns.put("unit_province_name", "Đà Nẵng");
		columns.put("unit_ward_code", "20194");
		columns.put("unit_ward_name", "Phường Hải Châu");
		return columns;
	}

	/** Every column a Pending review revision needs. */
	private Map<String, Object> complete() {
		Map<String, Object> columns = new LinkedHashMap<>(terms());
		columns.put("title", "Phòng trọ mới gần chợ");
		columns.put("description", "Mô tả ".repeat(30));
		columns.put("unit_bedrooms", 2);
		columns.put("unit_bathrooms", 1);
		columns.put("unit_area_sqm", 32.5);
		columns.put("submitted_at", Timestamp.from(now()));
		columns.put("submitted_by", ids.generateId());
		return columns;
	}

	private static Map<String, Object> with(Map<String, Object> columns, String column, Object value) {
		Map<String, Object> copy = new LinkedHashMap<>(columns);
		copy.put(column, value);
		return copy;
	}

	private static Map<String, Object> without(Map<String, Object> columns, String... removed) {
		Map<String, Object> copy = new LinkedHashMap<>(columns);
		for (String column : removed) {
			copy.remove(column);
		}
		return copy;
	}

	/** A revision row with the header columns and the given content columns (fixed names of this test only). */
	private String insertRow(String listingId, int number, String status, Map<String, Object> columns) {
		String id = ids.generateId();
		Timestamp now = Timestamp.from(now());
		List<Object> values = new ArrayList<>(List.of(id, listingId, number, status, ids.generateId(), now, now));
		values.addAll(columns.values());
		String names = columns.keySet().stream().map(name -> ", " + name).collect(Collectors.joining());
		String marks = columns.keySet().stream().map(name -> ", ?").collect(Collectors.joining());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at" + names + ") VALUES (?, ?, ?, ?, ?, ?, ?" + marks + ")", values.toArray());
		return id;
	}

	private void insertFee(String revisionId, int number, String name, String basis, Long amount, String chargeUnit) {
		jdbc.update("INSERT INTO listing_revision_fees (revision_id, fee_no, name, basis, amount, charge_unit)"
				+ " VALUES (?, ?, ?, ?, ?, ?)", revisionId, number, name, basis, amount, chargeUnit);
	}

	private int count(String sql, Object... arguments) {
		return jdbc.queryForObject(sql, Integer.class, arguments);
	}

	/** A listings row written with SQL only, on its own agreement; otherwise valid. */
	private String insertListingRow(String code, String transactionType, String state) {
		String id = ids.generateId();
		created.add(id);
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
						+ " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				id, code, ids.generateId(), transactionType, ids.generateId(), state, ids.generateId(), now, now);
		return id;
	}

	private void insertRevision(String listingId, int number, String status) {
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)", ids.generateId(), listingId, number, status,
				ids.generateId(), now, now);
	}

	/** One case: the insert is refused by the named constraint. */
	private static Executable refused(String constraint, Runnable insert) {
		return () -> {
			DataIntegrityViolationException refusal =
					assertThrows(DataIntegrityViolationException.class, insert::run, constraint);
			assertTrue(String.valueOf(refusal.getMostSpecificCause().getMessage()).contains(constraint), constraint);
		};
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private static ListingCode code() {
		return ListingCode.draw(RANDOM);
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
