package vn.sunvibe.property;

import java.net.http.HttpRequest;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.test.context.TestPropertySource;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * What the public read API tests of listing-publish E06-S7 share (plan Task A8): {@link ListingPublicationApiTestBase}
 * (unchanged, with its agreements, its cleanup and its publication fixtures) in a context whose rate limiter is off, so
 * the many anonymous GETs of one address in these classes never meet the public read's 60 a minute (PB-14 holds that
 * rule in a context of its own); the GET of the public path anonymously, as a caller or with a bearer token as text; a
 * visible listing; and the SQL that dates its latest valid confirmation back. Needs Docker.
 */
@TestPropertySource(properties = "app.security.rate-limit.enabled=false")
abstract class ListingPublicReadApiTestBase extends ListingPublicationApiTestBase {

	static final String PUBLIC = "/api/v1/public/listings/";

	/** A well-formed code no listing has (32^8 codes, drawn at random). */
	static final String UNKNOWN_CODE = "TD-ZZZZ-ZZZZ";

	/** The anonymous GET of the public read. */
	Result publicRead(String code) throws Exception {
		return api.get(null, PUBLIC + code);
	}

	/** The GET of the public read with the caller's valid token. */
	Result publicRead(TestUser caller, String code) throws Exception {
		return api.get(caller, PUBLIC + code);
	}

	/** The GET of the public read with this text as its bearer token. */
	Result publicReadWithToken(String token, String code) throws Exception {
		return api.send(api.request(null, PUBLIC + code).header("Authorization", "Bearer " + token).GET().build());
	}

	/** The anonymous request of the public path with another method, no body. */
	Result publicRequest(String method, String code) throws Exception {
		return api.send(api.request(null, PUBLIC + code).method(method, HttpRequest.BodyPublishers.noBody()).build());
	}

	/** The listing's public code as stored. */
	String publicCodeOf(String listingId) {
		return jdbc.queryForObject("select code from listings where id = ?", String.class, listingId);
	}

	/** A listing whose revision 1 the assigned Sale put live while every condition holds: visible now. */
	Drafting liveListing() throws Exception {
		Drafting drafting = waitingApproval();
		putLiveOk(drafting.listingId());
		return drafting;
	}

	/**
	 * The listing's latest valid confirmation dated that many days back in Vietnam, from the JVM's clock (testing.md):
	 * the record's contact date and the two summary columns together (PB-4).
	 */
	void confirmedDaysAgo(String listingId, long days) {
		LocalDate day = today().minusDays(days);
		jdbc.update("update listing_freshness_records set contact_date = ? where listing_id = ? and valid"
				+ " and listing_row_version = (select max(listing_row_version) from listing_freshness_records"
				+ " where listing_id = ? and valid)", Date.valueOf(day), listingId, listingId);
		jdbc.update("update listings set freshness_confirmed_on = ?, freshness_latest_valid = true where id = ?",
				Date.valueOf(day), listingId);
	}

	/** A freshness record by call with these answers, no note. */
	static Map<String, Object> record(long dataVersion, String outcome, Boolean termsStillApply,
			Boolean unitUnchanged) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("dataVersion", dataVersion);
		body.put("channel", "CALL");
		body.put("outcome", outcome);
		body.put("termsStillApply", termsStillApply);
		body.put("unitUnchanged", unitUnchanged);
		body.put("note", null);
		return body;
	}

	/** The row version of the listing's publication row. */
	long publicationRowVersion(String listingId) {
		return jdbc.queryForObject("select row_version from listing_publications where listing_id = ?", Long.class,
				listingId);
	}

	int publicationRows(String listingId) {
		return count("select count(*) from listing_publications where listing_id = ?", listingId);
	}
}
