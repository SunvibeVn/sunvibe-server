package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Times the survey-media migrations that touch the populated shared tables on a synthetic copy sized to the
 * three-year growth of survey-media spec 4.12 on its ten-times path (plan PD-8): 2,000,000 W15/W16 access-log rows and
 * 1,000,000 security events before the media migrations, then 10,000,000 media access-log rows (1,900,000 MEDIA_LIST)
 * after files 1 to 3. Files 4 (the MEDIA_LIST index) and 5 (VALIDATE) are timed while a second connection samples the
 * locks they hold. Spec 5.2: a plain index build must stay under 5 seconds, otherwise file 4 becomes CREATE INDEX
 * CONCURRENTLY. Tagged: ./gradlew :app:migrationTimingTest (excluded from ./gradlew build, PD-7).
 */
@Tag("migration-timing")
class SurveyMediaMigrationTimingPostgresTest {

	private static final String W16_VERSION = "202610241731";
	private static final String AFTER_FILE_3 = "202611021402";
	private static final String FILE_4 = "202611021403";
	private static final String FILE_5 = "202611021404";
	private static final String FILE_4_SCRIPT = "db/migration/V202611021403__index_survey_access_logs_media_list.sql";
	private static final Duration PLAIN_INDEX_LIMIT = Duration.ofSeconds(5);

	@Test
	void timeIndexAndValidate_onThreeYearCopy() throws Exception {
		try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"))
				.withSharedMemorySize(1024L * 1024 * 1024)) {
			postgres.start();
			DriverManagerDataSource dataSource = new DriverManagerDataSource(
					postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
			JdbcTemplate jdbc = new JdbcTemplate(dataSource);
			System.out.println("[PD-8] " + jdbc.queryForObject("SELECT version()", String.class));

			migrate(dataSource, W16_VERSION);
			long start = System.nanoTime();
			// W15/W16 values only: media values do not pass the old CHECKs (PD-8)
			jdbc.execute("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type, resource_id)"
					+ " SELECT g, lpad((g % 30000)::text, 26, 'L'), lpad((g % 200)::text, 26, 'U'),"
					+ " timestamptz '2026-11-01 00:00:00+00' - (g % 94608000) * interval '1 second',"
					+ " (ARRAY[NULL, 'SURVEY_BLOCK', 'CHECKLIST', 'CHECKPOINT'])[g % 4 + 1],"
					+ " CASE WHEN g % 4 = 0 THEN NULL ELSE lpad((g % 38000)::text, 26, 'C') END"
					+ " FROM generate_series(1, 2000000) g");
			jdbc.execute("INSERT INTO survey_security_events (id, user_id, lead_id, resource_type, resource_id, action,"
					+ " outcome, correlation_id, occurred_at)"
					+ " SELECT g, lpad((g % 200)::text, 26, 'U'), lpad((g % 30000)::text, 26, 'L'),"
					+ " (ARRAY['LEAD', 'APPOINTMENT', 'CHECKLIST', 'CHECKPOINT'])[g % 4 + 1],"
					+ " lpad((g % 38000)::text, 26, 'C'), (ARRAY['READ', 'START', 'SAVE', 'TAKEOVER'])[g % 4 + 1],"
					+ " (ARRAY['NOT_VISIBLE', 'NOT_ASSIGNEE'])[g % 2 + 1], NULL,"
					+ " timestamptz '2026-11-01 00:00:00+00' - (g % 94608000) * interval '1 second'"
					+ " FROM generate_series(1, 1000000) g");
			migrate(dataSource, AFTER_FILE_3);
			// 36 months of media reads: 1,900,000 MEDIA_LIST rows, the rest PREVIEW, FULL and DOWNLOAD
			jdbc.execute("INSERT INTO survey_access_logs (id, lead_id, user_id, accessed_at, resource_type, resource_id,"
					+ " request_id)"
					+ " SELECT g, lpad((g % 30000)::text, 26, 'L'), lpad((g % 200)::text, 26, 'U'),"
					+ " timestamptz '2026-11-01 00:00:00+00' - (g % 94608000) * interval '1 second',"
					+ " CASE WHEN g % 100 < 19 THEN 'MEDIA_LIST'"
					+ " ELSE (ARRAY['MEDIA_PREVIEW', 'MEDIA_FULL', 'MEDIA_DOWNLOAD'])[g % 3 + 1] END,"
					+ " CASE WHEN g % 100 < 19 THEN lpad((g % 38000)::text, 26, 'C')"
					+ " ELSE lpad((g % 1080000)::text, 26, 'M') END, lpad(g::text, 26, 'R')"
					+ " FROM generate_series(2000001, 12000000) g");
			jdbc.execute("ANALYZE survey_access_logs");
			jdbc.execute("ANALYZE survey_security_events");
			System.out.printf("[PD-8] copy generated in %d s: survey_access_logs %d rows (MEDIA_LIST %d),"
					+ " survey_security_events %d rows%n", seconds(start),
					jdbc.queryForObject("SELECT count(*) FROM survey_access_logs", Long.class),
					jdbc.queryForObject("SELECT count(*) FROM survey_access_logs WHERE resource_type = 'MEDIA_LIST'",
							Long.class),
					jdbc.queryForObject("SELECT count(*) FROM survey_security_events", Long.class));

			Timed index = timed(dataSource, jdbc, FILE_4);
			Timed validate = timed(dataSource, jdbc, FILE_5);
			System.out.printf("[PD-8] file 4 (MEDIA_LIST index): %d ms, locks %s%n", index.millis(), index.locks());
			System.out.printf("[PD-8] file 5 (VALIDATE): %d ms, locks %s%n", validate.millis(), validate.locks());

			assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE NOT convalidated",
					Integer.class));
			assertTrue(validate.locks().contains("survey_access_logs:ShareUpdateExclusiveLock"), validate.locks()
					.toString());
			String file4 = new ClassPathResource(FILE_4_SCRIPT).getContentAsString(StandardCharsets.UTF_8);
			if (!file4.contains("CONCURRENTLY")) {
				// spec 5.2: the plain build holds a SHARE lock, acceptable only under 5 seconds on this copy
				assertTrue(index.locks().contains("survey_access_logs:ShareLock"), index.locks().toString());
				assertTrue(index.millis() < PLAIN_INDEX_LIMIT.toMillis(),
						"plain index build took " + index.millis() + " ms: switch file 4 to CONCURRENTLY (spec 5.2)");
			}
		}
	}

	private static void migrate(DriverManagerDataSource dataSource, String target) {
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
	}

	/** Migrates to the target while a second connection samples the locks other sessions hold on the two tables. */
	private static Timed timed(DriverManagerDataSource dataSource, JdbcTemplate sampler, String target)
			throws InterruptedException {
		Set<String> locks = new ConcurrentSkipListSet<>();
		AtomicBoolean running = new AtomicBoolean(true);
		Thread sampling = new Thread(() -> {
			while (running.get()) {
				locks.addAll(sampler.queryForList("SELECT c.relname || ':' || l.mode FROM pg_locks l"
						+ " JOIN pg_class c ON c.oid = l.relation WHERE l.granted AND l.pid <> pg_backend_pid()"
						+ " AND c.relname IN ('survey_access_logs', 'survey_security_events')", String.class));
			}
		});
		sampling.start();
		long start = System.nanoTime();
		migrate(dataSource, target);
		long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
		running.set(false);
		sampling.join();
		return new Timed(millis, new TreeSet<>(locks));
	}

	private static long seconds(long startNanos) {
		return Duration.ofNanos(System.nanoTime() - startNanos).toSeconds();
	}

	private record Timed(long millis, Set<String> locks) {
	}
}
