package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingPublication;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.PutLiveMoment;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The publication row written by the put live on a real PostgreSQL (listing-publish E06-S7 spec E06S7-D2, D3, D4, D11;
 * criterion PB-9): each successful put live, at its three moments, replaces the listing's row and fee rows with the live
 * revision's, in its own guarded write, with a row version one above the replaced row's; a refused try leaves the row
 * as it is; a live revision whose unchecked fee name breaks a rule deletes the row and writes none; the rows go with
 * their listing; the two reads answer them back. The annotations are those of {@link ListingPutLivePersistencePostgresTest},
 * so this class shares its context; its rows are seeded by SQL first (testing.md), times from the JVM clock. Needs
 * Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingPublicationPersistencePostgresTest {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String FIRST_TITLE = "Phòng trọ mới gần chợ Hải Châu";
	private static final String SECOND_TITLE = "Phòng trọ rộng gần biển Mỹ Khê";
	private static final String PHONE_FEE_NAME = "Gửi xe, gọi 0905 123 456";

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
			jdbc.update("DELETE FROM listing_freshness_records WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revision_fees WHERE revision_id IN"
					+ " (SELECT id FROM listing_revisions WHERE listing_id = ?)", id);
			jdbc.update("DELETE FROM listing_history WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listings WHERE id = ?", id);
		}
		created.clear();
	}

	/**
	 * PB-9: the Sale's command writes the row and the fee rows of the live revision, row version 0; the approval's try
	 * that puts a second revision live replaces them, row version 1.
	 */
	@Test
	void update_saleCommandThenApproval_rowOfEachLiveRevisionVersionUp() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", FIRST_TITLE, true, "Điện", "Internet");
		Instant first = now();

		putLive(listingId, 1, ListingState.UNPUBLISHED, PutLiveMoment.SALE_COMMAND, first);

		assertAll(
				() -> assertEquals(code(listingId) + " LONG_TERM_RENT APARTMENT 48 20194 2 1 32.5 8000000 MONTHS 2 6 1 "
						+ FIRST_TITLE + " 0", publicationRow(listingId)),
				() -> assertEquals(first, wentLiveAt(listingId)),
				() -> assertEquals(List.of("1 Điện AMOUNT 3500 PER_KWH", "2 Internet INCLUDED - -"),
						publicationFees(listingId)),
				() -> assertEquals(revisionFees(listingId, 1), publicationFees(listingId)),
				() -> assertEquals(64, digest(listingId).length()));

		insertFull(listingId, 2, "APPROVED", SECOND_TITLE, true, "Nước");
		Instant second = now();
		putLive(listingId, 2, ListingState.PUBLISHED, PutLiveMoment.APPROVAL, second);

		assertAll(
				() -> assertEquals(code(listingId) + " LONG_TERM_RENT APARTMENT 48 20194 2 1 32.5 8000000 MONTHS 2 6 1 "
						+ SECOND_TITLE + " 1", publicationRow(listingId)),
				() -> assertEquals(second, wentLiveAt(listingId)),
				() -> assertEquals(List.of("1 Nước AMOUNT 3500 PER_KWH"), publicationFees(listingId)),
				() -> assertEquals(1, count("SELECT count(*) FROM listing_publications WHERE listing_id = ?", listingId)));
	}

	/**
	 * PB-9, E06S7-D3: a refused try on a listing whose revision 1 a first put live published, written by the approval's
	 * update: the row and its fee rows stay exactly revision 1's, row version 0.
	 */
	@Test
	void update_refusedTryAfterAFirstPutLive_rowStaysTheFormerLiveRevisions() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", FIRST_TITLE, true, "Điện", "Internet");
		putLive(listingId, 1, ListingState.UNPUBLISHED, PutLiveMoment.SALE_COMMAND, now());
		String before = publicationRow(listingId);
		List<String> feesBefore = publicationFees(listingId);
		insertFull(listingId, 2, "APPROVED", SECOND_TITLE, true, "Nước");

		tx(() -> {
			Listing listing = listings.findForPutLive(listingId, 2).orElseThrow();
			DisplayConditions failing = new DisplayConditions(ListingState.PUBLISHED, false, true, List.of(),
					listing.revision(2).orElseThrow().termsOrNull(), UnitAvailability.OCCUPIED, false,
					new FreshnessSummary(FreshnessPolicy.today(now()), true));
			assertEquals(1, listing.tryPutLive(2, failing, PutLiveMoment.APPROVAL, null, now(), ids.generateId(),
					ids.generateId()).size());
			listings.update(listing);
			return null;
		});

		assertAll(
				() -> assertEquals(before, publicationRow(listingId)),
				() -> assertTrue(before.endsWith(FIRST_TITLE + " 0"), before),
				() -> assertEquals(feesBefore, publicationFees(listingId)));
	}

	/** PB-9: a valid confirmation that puts live writes the row with the record, in recordFreshnessWithPutLive. */
	@Test
	void recordFreshnessWithPutLive_validConfirmation_rowOfTheLiveRevision() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", FIRST_TITLE, true, "Điện");
		long version = rowVersion(listingId);
		Instant now = now();
		LocalDate today = FreshnessPolicy.today(now);
		String sale = ids.generateId();

		tx(() -> {
			Listing listing = listings.findForPutLive(listingId, null).orElseThrow();
			listing.tryPutLive(1, holding(listing, 1, ListingState.UNPUBLISHED, now), PutLiveMoment.VALID_CONFIRMATION,
					null, now, ids.generateId(), ids.generateId());
			FreshnessRecord record = new FreshnessRecord(ids.generateId(), listingId, version + 1, today, now, sale,
					FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, true, null);
			listings.recordFreshnessWithPutLive(listing, version, now, new FreshnessSummary(today, true), record,
					new ListingHistoryEntry(ids.generateId(), ListingHistoryAction.FRESHNESS_RECORDED, null, sale, now));
			return null;
		});

		assertAll(
				() -> assertEquals(code(listingId) + " LONG_TERM_RENT APARTMENT 48 20194 2 1 32.5 8000000 MONTHS 2 6 1 "
						+ FIRST_TITLE + " 0", publicationRow(listingId)),
				() -> assertEquals(now, wentLiveAt(listingId)),
				() -> assertEquals(List.of("1 Điện AMOUNT 3500 PER_KWH"), publicationFees(listingId)));
	}

	/**
	 * E06S7-D11: a revision submitted before the check whose fee name breaks a rule goes live, and the listing's row
	 * (written by revision 1's put live) is deleted, none written.
	 */
	@Test
	void update_uncheckedBrokenFeeName_liveRowDeletedNoneWritten() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", FIRST_TITLE, true, "Điện");
		putLive(listingId, 1, ListingState.UNPUBLISHED, PutLiveMoment.SALE_COMMAND, now());
		insertFull(listingId, 2, "APPROVED", SECOND_TITLE, false, PHONE_FEE_NAME);

		putLive(listingId, 2, ListingState.PUBLISHED, PutLiveMoment.SALE_COMMAND, now());

		assertAll(
				() -> assertEquals("LIVE", jdbc.queryForObject("SELECT status FROM listing_revisions WHERE listing_id = ?"
						+ " AND revision_no = 2", String.class, listingId)),
				() -> assertEquals(0, count("SELECT count(*) FROM listing_publications WHERE listing_id = ?", listingId)),
				() -> assertEquals(0, count("SELECT count(*) FROM listing_publication_fees WHERE listing_id = ?",
						listingId)));
	}

	/** E06S7-D2: the row and its fee rows go with their listing. */
	@Test
	void deleteListing_publicationAndFeesGo() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", FIRST_TITLE, true, "Điện");
		putLive(listingId, 1, ListingState.UNPUBLISHED, PutLiveMoment.SALE_COMMAND, now());

		deleteRows();

		assertEquals(List.of(0, 0), List.of(
				count("SELECT count(*) FROM listing_publications WHERE listing_id = ?", listingId),
				count("SELECT count(*) FROM listing_publication_fees WHERE listing_id = ?", listingId)));
	}

	/** E06S7-D5, D13: the publication by its code, with its fees in order; the unit's listings with a row, in id order. */
	@Test
	void reads_publicationByCodeAndPublishedListingsOfTheUnit() {
		String listingId = insertListingRow("UNPUBLISHED");
		insertFull(listingId, 1, "APPROVED", FIRST_TITLE, true, "Điện", "Internet");
		Instant now = now();
		putLive(listingId, 1, ListingState.UNPUBLISHED, PutLiveMoment.SALE_COMMAND, now);
		String unitId = jdbc.queryForObject("SELECT unit_id FROM listings WHERE id = ?", String.class, listingId);
		String other = insertListingRow("UNPUBLISHED");

		Optional<ListingPublication> found = tx(() -> listings.findPublication(code(listingId)));

		ListingPublication publication = found.orElseThrow();
		assertAll(
				() -> assertEquals(List.of(listingId, unitId, FIRST_TITLE), List.of(publication.listingId(),
						publication.unitId(), publication.title())),
				() -> assertEquals(now, publication.wentLiveAt()),
				() -> assertEquals(now, publication.terms().capturedAt()),
				() -> assertEquals(List.of("Điện", "Internet"), publication.terms().fees().stream()
						.map(fee -> fee.name()).toList()),
				() -> assertEquals(digest(listingId), publication.termsDigest()),
				() -> assertEquals(Optional.empty(), tx(() -> listings.findPublication(code(other)))),
				() -> assertEquals(List.of(listingId), tx(() -> listings.findPublishedListingIds(unitId))),
				() -> assertEquals(List.of(), tx(() -> listings.findPublishedListingIds(ids.generateId()))));
	}

	private void putLive(String listingId, int revisionNo, ListingState state, PutLiveMoment moment, Instant now) {
		tx(() -> {
			Listing listing = listings.findForPutLive(listingId, revisionNo).orElseThrow();
			assertEquals(List.of(), listing.tryPutLive(revisionNo, holding(listing, revisionNo, state, now), moment,
					moment == PutLiveMoment.SALE_COMMAND ? ids.generateId() : null, now, ids.generateId(),
					ids.generateId()));
			listings.update(listing);
			return null;
		});
	}

	/** Every display condition holding for the loaded revision: its own terms in effect, available, confirmed today. */
	private static DisplayConditions holding(Listing listing, int revisionNo, ListingState state, Instant now) {
		return new DisplayConditions(state, false, true, List.of(),
				listing.revision(revisionNo).orElseThrow().termsOrNull(), UnitAvailability.AVAILABLE, false,
				new FreshnessSummary(FreshnessPolicy.today(now), true));
	}

	/**
	 * A revision with every column a Pending review revision needs, in the status given, with its fee names' flag and
	 * fee rows: the first an AMOUNT fee of 3,500 per kWh, the others INCLUDED.
	 */
	private void insertFull(String listingId, int number, String status, String title, boolean feeNamesChecked,
							String... feeNames) {
		Timestamp at = Timestamp.from(now());
		String revisionId = ids.generateId();
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at, title, description, terms_source, terms_captured_at, terms_monthly_rent,"
				+ " terms_deposit_basis, terms_deposit_months, terms_minimum_term_months, terms_payment_period_months,"
				+ " unit_asset_type, unit_province_code, unit_province_name, unit_ward_code, unit_ward_name, unit_bedrooms,"
				+ " unit_bathrooms, unit_area_sqm, submitted_at, submitted_by, fee_names_checked) VALUES (?, ?, ?, ?, ?,"
				+ " ?, ?, ?, ?, 'IN_EFFECT', ?, 8000000, 'MONTHS', 2, 6, 1, 'APARTMENT', '48', 'Đà Nẵng', '20194',"
				+ " 'Phường Hải Châu', 2, 1, 32.5, ?, ?, ?)", revisionId, listingId, number, status, ids.generateId(), at,
				at, title, "Mô tả ".repeat(30), at, at, ids.generateId(), feeNamesChecked);
		for (int index = 0; index < feeNames.length; index++) {
			if (index == 0) {
				jdbc.update("INSERT INTO listing_revision_fees (revision_id, fee_no, name, basis, amount, charge_unit)"
						+ " VALUES (?, 1, ?, 'AMOUNT', 3500, 'PER_KWH')", revisionId, feeNames[index]);
			} else {
				jdbc.update("INSERT INTO listing_revision_fees (revision_id, fee_no, name, basis, amount, charge_unit)"
						+ " VALUES (?, ?, ?, 'INCLUDED', NULL, NULL)", revisionId, index + 1, feeNames[index]);
			}
		}
	}

	/** A listings row written with SQL only, on its own agreement and unit, in the stored state given. */
	private String insertListingRow(String state) {
		String id = ids.generateId();
		created.add(id);
		Timestamp at = Timestamp.from(now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
						+ " created_at, updated_at) VALUES (?, ?, ?, 'LONG_TERM_RENT', ?, ?, ?, ?, ?)",
				id, ListingCode.draw(RANDOM).value(), ids.generateId(), ids.generateId(), state, ids.generateId(), at, at);
		return id;
	}

	/** The row's public values and its row version, as "code type asset province ward rooms area terms title version". */
	private String publicationRow(String listingId) {
		return jdbc.queryForObject("SELECT code || ' ' || transaction_type || ' ' || property_type || ' ' || province_code"
				+ " || ' ' || ward_code || ' ' || bedrooms || ' ' || bathrooms || ' ' || area_sqm || ' ' || monthly_rent"
				+ " || ' ' || deposit_basis || ' ' || deposit_months || ' ' || minimum_term_months || ' '"
				+ " || payment_period_months || ' ' || title || ' ' || row_version FROM listing_publications"
				+ " WHERE listing_id = ?", String.class, listingId);
	}

	private List<String> publicationFees(String listingId) {
		return jdbc.queryForList("SELECT fee_no || ' ' || name || ' ' || basis || ' ' || coalesce(amount::text, '-')"
				+ " || ' ' || coalesce(charge_unit, '-') FROM listing_publication_fees WHERE listing_id = ? ORDER BY fee_no",
				String.class, listingId);
	}

	private List<String> revisionFees(String listingId, int revisionNo) {
		return jdbc.queryForList("SELECT f.fee_no || ' ' || f.name || ' ' || f.basis || ' ' || coalesce(f.amount::text,"
				+ " '-') || ' ' || coalesce(f.charge_unit, '-') FROM listing_revision_fees f JOIN listing_revisions r"
				+ " ON r.id = f.revision_id WHERE r.listing_id = ? AND r.revision_no = ? ORDER BY f.fee_no", String.class,
				listingId, revisionNo);
	}

	private Instant wentLiveAt(String listingId) {
		return jdbc.queryForObject("SELECT went_live_at FROM listing_publications WHERE listing_id = ?", Timestamp.class,
				listingId).toInstant();
	}

	private String digest(String listingId) {
		return jdbc.queryForObject("SELECT terms_digest FROM listing_publications WHERE listing_id = ?", String.class,
				listingId);
	}

	private String code(String listingId) {
		return jdbc.queryForObject("SELECT code FROM listings WHERE id = ?", String.class, listingId);
	}

	private long rowVersion(String listingId) {
		return jdbc.queryForObject("SELECT row_version FROM listings WHERE id = ?", Long.class, listingId);
	}

	private int count(String sql, Object... arguments) {
		return jdbc.queryForObject(sql, Integer.class, arguments);
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
