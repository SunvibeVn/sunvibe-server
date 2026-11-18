package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.outbox.UnitAdvertisingChangedEvent;

/**
 * The H2 API test contexts create {@code outbox_events} from the entity (E05-S5b S5b-D2, S5b-AC-15), so an append with
 * a subscriber writes its row there too. AuthV1OpenApiTest's class annotations, so it shares that context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OutboxH2AppendTest {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	@Qualifier("Snowflake")
	private IdGenerator<Long> snowflake;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void append_h2Context_writesTheRow() {
		EventOutboxAdapter adapter = new EventOutboxAdapter(entityManager, snowflake,
				OutboxPostgresTestBase.subscriptions("test-a"), objectMapper, Clock.systemUTC());
		try {
			new TransactionTemplate(transactionManager).executeWithoutResult(
					status -> adapter.append(UnitAdvertisingChangedEvent.now(OutboxPostgresTestBase.UNIT_ID)));

			assertThat(jdbc.queryForList("SELECT consumer FROM outbox_events WHERE aggregate_id = ?", String.class,
					OutboxPostgresTestBase.UNIT_ID)).containsExactly("test-a");
		} finally {
			jdbc.update("DELETE FROM outbox_events");
		}
	}
}
