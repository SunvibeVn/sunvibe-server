package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The two revision times read back through the repository (listing-publish E06-S6 spec E06S6-D6 "Times", PL-14): a
 * Live revision answers the time it went live, a Replaced one both times, any other none. The annotations, autowired
 * fields, {@code RANDOM}, {@code created} with its cleanup and the helpers {@code listing}, {@code insert},
 * {@code insertRevision} and {@code tx} are copied as written from {@link ListingRevisionPersistencePostgresTest}, so
 * this class shares its context. The times are written by SQL from the JVM clock (testing.md). Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingRevisionTimesMappingPostgresTest {

	private static final SecureRandom RANDOM = new SecureRandom();

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
	void findRevision_liveAndReplacedRowsWithTimes_timesLoaded() {
		Listing listing = listing(ids.generateId(), now());
		insert(listing);
		Instant firstLive = now().minusSeconds(120);
		Instant replacedAt = now().minusSeconds(60);
		Instant secondLive = now().minusSeconds(60);
		jdbc.update("UPDATE listing_revisions SET status = 'REPLACED', went_live_at = ?, replaced_at = ?"
				+ " WHERE listing_id = ? AND revision_no = 1", Timestamp.from(firstLive), Timestamp.from(replacedAt),
				listing.id());
		Timestamp createdAt = Timestamp.from(now());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at, went_live_at) VALUES (?, ?, 2, 'LIVE', ?, ?, ?, ?)", ids.generateId(), listing.id(),
				ids.generateId(), createdAt, createdAt, Timestamp.from(secondLive));

		ListingRevision replaced = tx(() -> listings.findRevision(listing.id(), 1).orElseThrow());
		ListingRevision live = tx(() -> listings.findRevision(listing.id(), 2).orElseThrow());

		assertAll(
				() -> assertEquals(RevisionStatus.REPLACED, replaced.status()),
				() -> assertEquals(firstLive, replaced.wentLiveAtOrNull()),
				() -> assertEquals(replacedAt, replaced.replacedAtOrNull()),
				() -> assertEquals(RevisionStatus.LIVE, live.status()),
				() -> assertEquals(secondLive, live.wentLiveAtOrNull()),
				() -> assertNull(live.replacedAtOrNull()));
	}

	@Test
	void findRevision_otherStatus_noTimes() {
		Listing listing = listing(ids.generateId(), now());
		insert(listing);

		ListingRevision draft = tx(() -> listings.findRevision(listing.id(), 1).orElseThrow());

		assertAll(
				() -> assertEquals(RevisionStatus.DRAFT, draft.status()),
				() -> assertNull(draft.wentLiveAtOrNull()),
				() -> assertNull(draft.replacedAtOrNull()));
	}

	private Listing listing(String agreementId, Instant now) {
		Listing listing = Listing.create(ids.generateId(), code(), ids.generateId(), agreementId, ids.generateId(), now,
				ids.generateId(), ids.generateId());
		created.add(listing.id());
		return listing;
	}

	private void insert(Listing listing) {
		tx(() -> {
			listings.insert(listing);
			return null;
		});
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
