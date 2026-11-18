package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

import vn.sunvibe.PostgresContainerConfig.PostgresTestDatabase;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * The runbook scripts of the outbox (E05-S5b spec 4.9, S5b-D10, S5b-AC-12), each run as written with psql in the shared
 * container against this context's database. Needs Docker.
 */
class OutboxRunbookScriptPostgresTest extends OutboxPostgresTestBase {

	private static final Path LIST = Path.of("..", "scripts", "ops", "outbox-dead-list.sql");
	private static final Path REQUEUE_ROW = Path.of("..", "scripts", "ops", "outbox-requeue-dead-row.sql");
	private static final Path REQUEUE_CONSUMER = Path.of("..", "scripts", "ops", "outbox-requeue-dead-consumer.sql");

	@Autowired
	private PostgresTestDatabase database;

	@Test
	void requeueDeadRow_onlyThatRow() throws Exception {
		long target = insertRow("x", "DEAD", "CONSUMER_ERROR", 10);
		long otherDead = insertRow("x", "DEAD", "DELIVERY_TIMEOUT", 10);
		long pending = insertRow("x", "PENDING", "CONSUMER_ERROR", 3);

		ExecResult result = psql(REQUEUE_ROW, "row_id=" + target);

		assertThat(result.getExitCode()).as(result.getStderr()).isZero();
		Map<String, Object> requeued = row(target);
		assertThat(requeued.get("status")).isEqualTo("PENDING");
		assertThat(requeued.get("attempts")).isEqualTo(0);
		assertThat(requeued.get("lease_token")).isNull();
		assertThat(requeued.get("last_error_code")).isNull();
		assertThat(requeued.get("next_attempt_at")).isNotNull();
		assertThat(row(otherDead).get("status")).isEqualTo("DEAD");
		assertThat(row(otherDead).get("last_error_code")).isEqualTo("DELIVERY_TIMEOUT");
		assertThat(row(pending).get("attempts")).isEqualTo(3);
		assertThat(row(pending).get("last_error_code")).isEqualTo("CONSUMER_ERROR");
	}

	@Test
	void requeueDeadConsumer_onlyThatConsumersDeadRows() throws Exception {
		long deadX1 = insertRow("x", "DEAD", "CONSUMER_ERROR", 10);
		long deadX2 = insertRow("x", "DEAD", "DELIVERY_TIMEOUT", 10);
		long deadY = insertRow("y", "DEAD", "CONSUMER_ERROR", 10);
		long pendingX = insertRow("x", "PENDING", "CONSUMER_ERROR", 4);

		ExecResult result = psql(REQUEUE_CONSUMER, "consumer=x");

		assertThat(result.getExitCode()).as(result.getStderr()).isZero();
		for (long id : List.of(deadX1, deadX2)) {
			Map<String, Object> requeued = row(id);
			assertThat(requeued.get("status")).isEqualTo("PENDING");
			assertThat(requeued.get("attempts")).isEqualTo(0);
			assertThat(requeued.get("last_error_code")).isNull();
		}
		assertThat(row(deadY).get("status")).isEqualTo("DEAD");
		assertThat(row(pendingX).get("attempts")).isEqualTo(4);
		assertThat(row(pendingX).get("last_error_code")).isEqualTo("CONSUMER_ERROR");
	}

	@Test
	void listDead_noPayload() throws Exception {
		long dead = insertRow("x", "DEAD", "CONSUMER_ERROR", 10);
		insertRow("x", "PENDING", null, 0);

		ExecResult result = psql(LIST, null);

		assertThat(result.getExitCode()).as(result.getStderr()).isZero();
		assertThat(result.getStdout()).contains(String.valueOf(dead), "CONSUMER_ERROR")
				.doesNotContain("payload").doesNotContain("aggregate_id").doesNotContain(UNIT_ID)
				.contains("(1 row)");
	}

	private ExecResult psql(Path script, String variable) throws Exception {
		assertThat(Files.isRegularFile(script)).as("script missing: " + script.toAbsolutePath().normalize()).isTrue();
		String inContainer = "/tmp/" + script.getFileName();
		database.container().copyFileToContainer(MountableFile.forHostPath(script), inContainer);
		List<String> command = new ArrayList<>(List.of("psql", "-v", "ON_ERROR_STOP=1", "-U",
				database.container().getUsername(), "-d", database.name()));
		if (variable != null) {
			command.addAll(List.of("-v", variable));
		}
		command.addAll(List.of("-f", inContainer));
		return database.container().execInContainer(command.toArray(String[]::new));
	}

	private long insertRow(String consumer, String status, String lastErrorCode, int attempts) {
		long id = snowflake.generateId();
		OffsetDateTime at = OffsetDateTime.ofInstant(START.minus(Duration.ofDays(1)), ZoneOffset.UTC);
		jdbc.update("INSERT INTO outbox_events (id, event_id, event_type, event_version, consumer, source_module,"
				+ " aggregate_type, aggregate_id, payload, occurred_at, status, attempts, next_attempt_at, last_error_code)"
				+ " VALUES (?, ?, ?, 1, ?, 'brokerage', 'unit', ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)",
				id, id, UnitAdvertisingChangedEvent.TYPE, consumer, UNIT_ID, "{\"unitId\":\"" + UNIT_ID + "\"}", at,
				status, attempts, at, lastErrorCode);
		return id;
	}

	private Map<String, Object> row(long id) {
		return jdbc.queryForMap("SELECT * FROM outbox_events WHERE id = ?", id);
	}
}
