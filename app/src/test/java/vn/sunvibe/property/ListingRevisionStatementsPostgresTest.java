package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.listing.core.domain.model.listing.Furnishing;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.Pets;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.ShareMode;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;

/**
 * The bounded loads and the narrow write of a listing with 21 revisions on PostgreSQL (listing-publish E06-S2 RD-18;
 * E06S2-D14, E06S2-D19; E06S1-D17): a command's load is four statements for any number of revisions, the revision read
 * two, and a content save writes only its own revision row. {@link StatementCounter} counts the calling thread only and
 * records no SQL text, so each count is taken around the repository call alone, inside a transaction on the test thread;
 * the scope check and E05's reads stay outside it. The listing is created over HTTP; its other revisions and the fee
 * rows are written by SQL. Needs Docker.
 */
@Import(StatementCounter.Config.class)
class ListingRevisionStatementsPostgresTest extends ListingRevisionApiTestBase {

	private static final RevisionFeatures FEATURES = new RevisionFeatures(Furnishing.FULL, true, false, true,
			ShareMode.PRIVATE, ShareMode.SHARED, false, true, false, true, false, Pets.NOT_ALLOWED, true);

	@Autowired
	private ListingDomainRepository listings;

	@Autowired
	private StatementCounter counter;

	@Override
	String phonePrefix() {
		return "0914";
	}

	/** RD-18: with target 5, the listing row, the revisions {21, 5}, their fees and the greatest number: four statements. */
	@Test
	void findForCommand_twentyOneRevisions_fourStatements() throws Exception {
		String listingId = twentyOneRevisions();
		AtomicReference<Listing> loaded = new AtomicReference<>();

		long statements = tx(() -> counter.count(() -> loaded.set(listings.findForCommand(listingId, 5).orElseThrow())));

		assertEquals(4, statements);
		assertEquals(Set.of(5, 21), loaded.get().revisions().stream().map(ListingRevision::revisionNo)
				.collect(Collectors.toSet()));
		assertEquals(21, loaded.get().newestRevisionNo());
		assertEquals(3, loaded.get().revision(21).orElseThrow().termsOrNull().fees().size());
	}

	/** RD-18, E06S2-D14: the read of one revision is that revision and its fee rows: two statements. */
	@Test
	void findRevision_twentyOneRevisions_twoStatements() throws Exception {
		String listingId = twentyOneRevisions();
		AtomicReference<ListingRevision> loaded = new AtomicReference<>();

		long statements = tx(() -> counter.count(() -> loaded.set(listings.findRevision(listingId, 21).orElseThrow())));

		assertEquals(2, statements);
		assertEquals(21, loaded.get().revisionNo());
		assertEquals(List.of("Điện", "Internet", "Nước"), loaded.get().termsOrNull().fees().stream()
				.map(fee -> fee.name()).toList());
	}

	/**
	 * RD-18: after a content save of revision 21, only revision 21's row was written ({@code xmin} moved); every other
	 * revision row and every fee row keep their {@code xmin}, and the fee rows stay three.
	 */
	@Test
	void update_contentSave_onlyItsRevisionRowWritten() throws Exception {
		String listingId = twentyOneRevisions();
		Map<String, String> revisionsBefore = revisionVersions(listingId);
		Map<String, String> feesBefore = feeVersions(listingId);
		String draftId = jdbc.queryForObject("select id from listing_revisions where listing_id = ? and revision_no = 21",
				String.class, listingId);

		tx(() -> {
			Listing listing = listings.findForCommand(listingId, null).orElseThrow();
			listing.saveContent(21, new RevisionContent(TITLE, DESCRIPTION, FEATURES), ids.generateId(), sale.userId(),
					Instant.now().truncatedTo(ChronoUnit.MICROS));
			listings.update(listing);
			return null;
		});

		Map<String, String> revisionsAfter = revisionVersions(listingId);
		assertNotEquals(revisionsBefore.get(draftId), revisionsAfter.get(draftId));
		revisionsBefore.remove(draftId);
		revisionsAfter.remove(draftId);
		assertEquals(20, revisionsBefore.size());
		assertEquals(revisionsBefore, revisionsAfter);
		assertEquals(3, feesBefore.size());
		assertEquals(feesBefore, feeVersions(listingId));
		assertEquals(DESCRIPTION, jdbc.queryForObject("select description from listing_revisions where id = ?",
				String.class, draftId));
	}

	/**
	 * A listing created over HTTP, then by SQL: revision 1 Cancelled, 2 to 20 added Cancelled, 21 added as a Draft with
	 * content, terms in effect and three fee rows, so one revision is open.
	 */
	private String twentyOneRevisions() throws Exception {
		String listingId = listingInEffect().listingId();
		jdbc.update("update listing_revisions set status = 'CANCELLED' where listing_id = ?", listingId);
		Timestamp now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
		for (int number = 2; number <= 20; number++) {
			jdbc.update("insert into listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
					+ " updated_at) values (?, ?, ?, 'CANCELLED', ?, ?, ?)", ids.generateId(), listingId, number,
					sale.userId(), now, now);
		}
		String draftId = ids.generateId();
		jdbc.update("insert into listing_revisions (id, listing_id, revision_no, status, created_by, created_at, updated_at,"
				+ " title, description, feature_furnishing, feature_air_conditioner, feature_water_heater, feature_fridge,"
				+ " feature_washing_machine, feature_kitchen, feature_balcony, feature_elevator, feature_motorbike_parking,"
				+ " feature_car_parking, feature_internet, feature_pets, feature_no_shared_landlord, terms_source,"
				+ " terms_captured_at, terms_monthly_rent, terms_deposit_basis, terms_deposit_months,"
				+ " terms_minimum_term_months, terms_payment_period_months) values (?, ?, 21, 'DRAFT', ?, ?, ?,"
				+ " ?, ?, 'BASIC', false, false, false, null, null, false, false, false, false, false, null, false,"
				+ " 'IN_EFFECT', ?, 8000000, 'MONTHS', 2, 6, 1)",
				draftId, listingId, sale.userId(), now, now, "Phòng cũ gần chợ Hải Châu, giá tốt", "Mô tả cũ.", now);
		jdbc.update("insert into listing_revision_fees (revision_id, fee_no, name, basis, amount, charge_unit)"
				+ " values (?, 1, 'Điện', 'AMOUNT', 3500, 'PER_KWH')", draftId);
		jdbc.update("insert into listing_revision_fees (revision_id, fee_no, name, basis, amount, charge_unit)"
				+ " values (?, 2, 'Internet', 'INCLUDED', null, null)", draftId);
		jdbc.update("insert into listing_revision_fees (revision_id, fee_no, name, basis, amount, charge_unit)"
				+ " values (?, 3, 'Nước', 'PROVIDER_TARIFF', null, null)", draftId);
		return listingId;
	}

	/** Each revision row's id and its {@code xmin}: the transaction that last wrote it. */
	private Map<String, String> revisionVersions(String listingId) {
		Map<String, String> versions = new LinkedHashMap<>();
		jdbc.query("select id, xmin::text as written from listing_revisions where listing_id = ? order by revision_no",
				row -> {
					versions.put(row.getString("id"), row.getString("written"));
				}, listingId);
		return versions;
	}

	/** Each fee row's revision and number, and its {@code xmin}. */
	private Map<String, String> feeVersions(String listingId) {
		Map<String, String> versions = new LinkedHashMap<>();
		jdbc.query("select f.revision_id || ':' || f.fee_no as fee, f.xmin::text as written from listing_revision_fees f"
				+ " join listing_revisions r on r.id = f.revision_id where r.listing_id = ? order by r.revision_no,"
				+ " f.fee_no", row -> {
					versions.put(row.getString("fee"), row.getString("written"));
				}, listingId);
		return versions;
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}
}
