package vn.sunvibe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The administrative-area catalog migrations on a real PostgreSQL (acquisition-owner-intake BR-6, spec 4.1;
 * W9-15): one province and the sixteen wards of the brief, a seed that can run again, and the constraints of the
 * tables. Needs Docker, like {@link FlywayPostgresMigrationTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdministrativeAreaMigrationPostgresTest {

	private static final String SEED_SCRIPT = "db/migration/V202610161101__seed_da_nang_administrative_areas.sql";

	/** The table of acquisition-owner-intake brief BR-6, verbatim: code, name, type (Phường, Xã, Đặc khu). */
	private static final List<List<String>> BRIEF_WARDS = List.of(
			List.of("20242", "Hải Châu", "WARD"),
			List.of("20257", "Hòa Cường", "WARD"),
			List.of("20209", "Thanh Khê", "WARD"),
			List.of("20305", "An Khê", "WARD"),
			List.of("20275", "An Hải", "WARD"),
			List.of("20263", "Sơn Trà", "WARD"),
			List.of("20285", "Ngũ Hành Sơn", "WARD"),
			List.of("20200", "Hòa Khánh", "WARD"),
			List.of("20194", "Hải Vân", "WARD"),
			List.of("20197", "Liên Chiểu", "WARD"),
			List.of("20260", "Cẩm Lệ", "WARD"),
			List.of("20314", "Hòa Xuân", "WARD"),
			List.of("20320", "Hòa Vang", "COMMUNE"),
			List.of("20332", "Hòa Tiến", "COMMUNE"),
			List.of("20308", "Bà Nà", "COMMUNE"),
			List.of("20333", "Hoàng Sa", "SPECIAL_ZONE"));

	private final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private JdbcTemplate jdbc;

	@BeforeAll
	void migrateEmptyDatabase() {
		postgres.start();
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		jdbc = new JdbcTemplate(dataSource);

		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
	}

	@AfterAll
	void stop() {
		postgres.stop();
	}

	/** acquisition-owner-intake BR-6 (W9-15): Da Nang 48 and its sixteen units, codes and types as in the brief. */
	@Test
	void migrate_emptyDatabase_seedsOneProvinceAndSixteenWards() {
		assertEquals(List.of(Map.of("code", "48", "name", "Đà Nẵng")),
				jdbc.queryForList("SELECT code, name FROM provinces"));
		assertEquals(16, count("wards"));

		List<List<String>> stored = jdbc.query("SELECT code, name, type FROM wards ORDER BY code",
				(rs, row) -> List.of(rs.getString("code"), rs.getString("name"), rs.getString("type")));
		assertEquals(BRIEF_WARDS.stream().sorted((a, b) -> a.get(0).compareTo(b.get(0))).toList(), stored);

		assertEquals(12, countWardsOfType("WARD"));
		assertEquals(3, countWardsOfType("COMMUNE"));
		assertEquals(1, countWardsOfType("SPECIAL_ZONE"));
		assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM wards WHERE province_code <> '48'", Integer.class));
	}

	/** Spec 4.1: the seed is repeatable (ON CONFLICT), so running its text again changes nothing. */
	@Test
	void migrate_seedScript_runAgain_changesNothing() throws IOException {
		String seed = new ClassPathResource(SEED_SCRIPT).getContentAsString(StandardCharsets.UTF_8);
		List<Map<String, Object>> provincesBefore = jdbc.queryForList("SELECT * FROM provinces ORDER BY code");
		List<Map<String, Object>> wardsBefore = jdbc.queryForList("SELECT * FROM wards ORDER BY code");

		jdbc.execute(seed);
		jdbc.execute(seed);

		assertEquals(1, count("provinces"));
		assertEquals(16, count("wards"));
		assertEquals(provincesBefore, jdbc.queryForList("SELECT * FROM provinces ORDER BY code"));
		assertEquals(wardsBefore, jdbc.queryForList("SELECT * FROM wards ORDER BY code"));
	}

	/** Spec 4.1: the type is one of three values and a ward belongs to a province that exists. */
	@Test
	void migrate_wards_rejectUnknownTypeAndOrphanProvince() {
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
				"INSERT INTO wards (code, province_code, name, type) VALUES ('99001', '48', 'Khác', 'DISTRICT')"));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
				"INSERT INTO wards (code, province_code, name, type) VALUES ('99002', '99', 'Khác', 'WARD')"));
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
				"INSERT INTO wards (code, province_code, name, type) VALUES ('99003', '48', 'Hải Châu', 'WARD')"));

		assertEquals(16, count("wards"));
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	private int countWardsOfType(String type) {
		return jdbc.queryForObject("SELECT count(*) FROM wards WHERE type = ?", Integer.class, type);
	}
}
