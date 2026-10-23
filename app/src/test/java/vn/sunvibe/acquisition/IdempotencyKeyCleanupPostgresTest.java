package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.core.application.usecase.IdempotencyKeyCleanupUseCase;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentChannel;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentPurpose;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentRecord;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PhoneNumber;
import vn.sunvibe.acquisition.core.domain.model.lead.RequestCode;
import vn.sunvibe.acquisition.core.domain.repository.ConsentRecordDomainRepository;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadDomainRepository;
import vn.sunvibe.acquisition.core.infrastructure.scheduling.IdempotencyKeyCleanupJob;

/**
 * The daily cleanup of the owner intake idempotency keys on PostgreSQL (acquisition-owner-intake spec 5.1; W9-26):
 * the use case runs its bulk delete in its own transaction, called the way the scheduler calls it (no transaction
 * around it). Keys older than 24 hours go; younger keys and every lead stay. Same annotations as
 * AcquisitionPersistencePostgresTest, so both share one context. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class IdempotencyKeyCleanupPostgresTest {

	private static final String HASH = "a".repeat(64);

	@Autowired
	private IdempotencyKeyCleanupUseCase cleanup;

	@Autowired
	private IdempotencyKeyCleanupJob job;

	@Autowired
	private OwnerLeadDomainRepository leads;

	@Autowired
	private ConsentRecordDomainRepository consents;

	@Autowired
	private JdbcTemplate jdbc;

	/** The lead every key points to; it must outlive the cleanup. */
	private OwnerLead lead;

	@BeforeEach
	void saveLead() {
		jdbc.update("delete from owner_intake_idempotency_keys");
		Instant createdAt = Instant.now().minus(Duration.ofDays(3));
		ConsentRecord consent = ConsentRecord.create(ConsentRecord.CURRENT_NOTICE_VERSION,
				ConsentPurpose.OWNER_INTAKE_CONTACT, ConsentChannel.WEB, HASH, null, createdAt);
		consents.save(consent);
		lead = OwnerLead.load(OwnerLeadId.generate(), RequestCode.generate(new SecureRandom()), OwnerLeadStatus.NEW,
				OwnerLeadIntent.LONG_TERM_RENT, OwnerLeadSource.WEB, "Tran Thi B", PhoneNumber.parse("0941000001"),
				null, "48", "Đà Nẵng", "20242", "Hải Châu", "1 a", "1 a", Optional.empty(), null, Optional.empty(),
				null, null, null, consent.getId(), "{}", null, null, null, null, createdAt, createdAt, null, 1);
		leads.save(lead);
	}

	@AfterEach
	void deleteRows() {
		jdbc.update("delete from owner_intake_idempotency_keys");
		jdbc.update("delete from owner_lead_activities where lead_id = ?", lead.getId().getValue());
		jdbc.update("delete from owner_leads where id = ?", lead.getId().getValue());
		jdbc.update("delete from consent_records where id = ?", lead.getConsentRecordId().getValue());
	}

	@Test
	void purgeExpired_onPostgres_deletesOnlyKeysOlderThan24h() {
		Instant now = Instant.now();
		insertKey("old-key-0001", now.minus(Duration.ofHours(25)));
		insertKey("old-key-0002", now.minus(Duration.ofDays(3)));
		insertKey("new-key-0001", now.minus(Duration.ofHours(23)));

		int deleted = cleanup.purgeExpired();

		assertEquals(2, deleted);
		assertEquals(List.of("new-key-0001"),
				jdbc.queryForList("select idempotency_key from owner_intake_idempotency_keys", String.class));
		assertEquals(1, jdbc.queryForObject("select count(*) from owner_leads where id = ?", Integer.class,
				lead.getId().getValue()), "the lead of a deleted key stays");
	}

	@Test
	void job_scheduledRun_deletesExpiredKeys() {
		insertKey("old-key-0003", Instant.now().minus(Duration.ofHours(30)));

		job.purgeExpiredIdempotencyKeys();

		assertEquals(0, jdbc.queryForObject("select count(*) from owner_intake_idempotency_keys", Integer.class));
	}

	private void insertKey(String key, Instant createdAt) {
		jdbc.update("insert into owner_intake_idempotency_keys (idempotency_key, request_hash, lead_id, created_at) "
				+ "values (?, ?, ?, ?)", key, HASH, lead.getId().getValue(), Timestamp.from(createdAt));
	}
}
