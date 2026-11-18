package vn.sunvibe.brokerage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.brokerage.AgreementListFixture.Version;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryDomainRepository;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryPage;
import vn.sunvibe.brokerage.core.domain.repository.AgreementHistoryRow;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The read of an agreement's stored history on a real PostgreSQL (E05-S7 spec S7-D7, S7-D8, 5.3; criteria HS-1, HS-5,
 * HS-6 in their repository parts). The annotations are those of {@link AgreementPersistencePostgresTest}, so both share
 * one context. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class AgreementHistoryPersistencePostgresTest {

	private static final String STAFF = "01JSTAFF000000000000000000";
	private static final Instant T0 = Instant.parse("2026-12-01T02:00:00.123456Z");

	@Autowired
	private AgreementHistoryDomainRepository histories;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private IdGenerator<String> ids;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<String> created = new ArrayList<>();

	@AfterEach
	void deleteRows() {
		AgreementFixture.delete(jdbc, created);
		created.clear();
	}

	/** Newest first; two rows of one time, the later inserted first; a null version and a null actor read as null. */
	@Test
	void history_rows_newestFirstThenIdWithCount() {
		String agreementId = agreement();
		AgreementListFixture.event(jdbc, agreementId, 1, "CREATED", STAFF, T0);
		AgreementListFixture.event(jdbc, agreementId, 1, "VERSION_SENT", STAFF, T0.plusSeconds(60));
		AgreementListFixture.event(jdbc, agreementId, null, "VERSION_LAPSED", null, T0.plusSeconds(60));
		AgreementListFixture.event(jdbc, agreementId(), 1, "CREATED", STAFF, T0.plusSeconds(120));

		AgreementHistoryPage page = read(agreementId, 0, 50);

		assertEquals(List.of(new AgreementHistoryRow("VERSION_LAPSED", null, null, T0.plusSeconds(60)),
				new AgreementHistoryRow("VERSION_SENT", 1, STAFF, T0.plusSeconds(60)),
				new AgreementHistoryRow("CREATED", 1, STAFF, T0)), page.rows());
		assertEquals(3, page.totalElements());
	}

	/** HS-5: pages of two go through five rows without repeat or gap; the count is every row. */
	@Test
	void history_pageOfTwo_noRepeatNoGap() {
		String agreementId = agreement();
		List<AgreementHistoryRow> newestFirst = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			AgreementListFixture.event(jdbc, agreementId, 1, "DRAFT_EDITED", STAFF, T0.plusSeconds(index));
			newestFirst.addFirst(new AgreementHistoryRow("DRAFT_EDITED", 1, STAFF, T0.plusSeconds(index)));
		}

		List<AgreementHistoryRow> paged = new ArrayList<>();
		List<Integer> sizes = new ArrayList<>();
		for (int page = 0; page < 3; page++) {
			AgreementHistoryPage read = read(agreementId, page, 2);
			assertEquals(5, read.totalElements());
			sizes.add(read.rows().size());
			paged.addAll(read.rows());
		}

		assertEquals(List.of(2, 2, 1), sizes);
		assertEquals(newestFirst, paged);
	}

	/** HS-6 (repository part): a code the enum does not hold is read as stored. */
	@Test
	void history_unknownCode_rowAsStored() {
		String agreementId = agreement();
		AgreementListFixture.event(jdbc, agreementId, 2, "SOMETHING_NEW", STAFF, T0);

		assertEquals(List.of(new AgreementHistoryRow("SOMETHING_NEW", 2, STAFF, T0)), read(agreementId, 0, 50).rows());
	}

	private String agreement() {
		String id = AgreementListFixture.insert(jdbc, ids, ids.generateId(), null, "DRAFTING", T0,
				new Version(1, "DRAFT", LocalDate.parse("2027-01-01"), LocalDate.parse("2027-12-31")));
		created.add(id);
		return id;
	}

	/** Another agreement, whose rows never reach the first one's history. */
	private String agreementId() {
		return agreement();
	}

	private AgreementHistoryPage read(String agreementId, int page, int size) {
		return tx(() -> histories.history(agreementId, page, size));
	}

	private <T> T tx(Supplier<T> work) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setReadOnly(true);
		return template.execute(status -> work.get());
	}
}
