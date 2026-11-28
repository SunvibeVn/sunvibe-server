package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.outbox.OutboxWorker;

/**
 * Listing's consumer of E05's event on PostgreSQL (listing-publish E06-S7 spec E06S7-D13; criterion PB-16): one delivery
 * of the unit's row of the activation, for a unit with one published and one unpublished listing, appends exactly one
 * listing event row, for the published one; the same row delivered again appends a second (a repeated delivery is a
 * repeated signal, ADR-014 decision 6). The listing API tests' context: its test subscription (S-4) lists the consumer
 * beside the test consumer of E05's event, and the worker is off in the test profile, so the test drives
 * {@link OutboxWorker#deliverConsumer(String)}. The second listing is a row inserted by SQL with another agreement, no
 * publication row, deleted after the test. Times from the JVM's clock (testing.md). Needs Docker.
 */
class ListingUnitAdvertisingConsumerPostgresTest extends ListingPublicationApiTestBase {

	private static final String CONSUMER = "listing-unit-advertising";

	@Autowired
	OutboxWorker worker;

	private String unpublishedId;

	@Override
	String phonePrefix() {
		return "0990";
	}

	@AfterEach
	void deleteUnpublishedListing() {
		if (unpublishedId != null) {
			jdbc.update("delete from outbox_events where aggregate_type = 'listing' and aggregate_id = ?", unpublishedId);
			jdbc.update("delete from listings where id = ?", unpublishedId);
			unpublishedId = null;
		}
	}

	/** PB-16: one delivery, one listing event row, for the published listing; delivered again, a second. */
	@Test
	void deliverConsumer_unitWithPublishedAndUnpublished_signalsThePublishedOnly() throws Exception {
		Drafting drafting = waitingApproval();
		String published = drafting.listingId();
		String unitId = drafting.chain().unitId();
		putLiveOk(published);
		unpublishedId = unpublishedListingOf(unitId);
		clearListingEventRows(published);
		clearListingEventRows(unpublishedId);
		long delivered = dueRowOf(unitId);
		jdbc.update("update outbox_events set status = 'DONE', published_at = ? where consumer = ? and status = 'PENDING'"
				+ " and id <> ?", Timestamp.from(now()), CONSUMER, delivered);

		worker.deliverConsumer(CONSUMER);

		assertEquals(List.of("DONE"), jdbc.queryForList("select status from outbox_events where id = ?", String.class,
				delivered));
		assertEquals(List.of(eventRow(published)), listingEventRows(published));
		assertEquals(List.of(), listingEventRows(unpublishedId));

		jdbc.update("update outbox_events set status = 'PENDING', attempts = 0, lease_token = null, published_at = null,"
				+ " next_attempt_at = ? where id = ?", Timestamp.from(now()), delivered);
		worker.deliverConsumer(CONSUMER);

		assertEquals(List.of(eventRow(published), eventRow(published)), listingEventRows(published));
		assertEquals(List.of(), listingEventRows(unpublishedId));
	}

	/** A second listing of the unit: another agreement (uq_listings_agreement_open), Unpublished, no publication row. */
	private String unpublishedListingOf(String unitId) {
		String id = ids.generateId();
		Timestamp at = Timestamp.from(now());
		jdbc.update("insert into listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
				+ " created_at, updated_at, row_version) values (?, ?, ?, 'LONG_TERM_RENT', ?, 'UNPUBLISHED', ?, ?, ?, 0)",
				id, ListingCode.draw(new SecureRandom()).value(), unitId, ids.generateId(), sale.userId(), at, at);
		return id;
	}

	/** The unit's row of the consumer that is due now: the activation's "now" event (its end-date row is due later). */
	private long dueRowOf(String unitId) {
		return jdbc.queryForObject("select id from outbox_events where consumer = ? and aggregate_id = ?"
				+ " and status = 'PENDING' and next_attempt_at <= ? order by id limit 1", Long.class, CONSUMER, unitId,
				Timestamp.from(now()));
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
