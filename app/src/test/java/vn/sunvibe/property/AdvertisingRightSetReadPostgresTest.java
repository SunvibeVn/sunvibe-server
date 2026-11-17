package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingPair;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightUseCase;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementStandingView;
import vn.sunvibe.brokerage.core.application.usecase.UnfinishedAgreementView;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationStatement;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The published, viewer-less answer for listing on PostgreSQL (brokerage-agreement BR-12, BR-13, US-4 AC-1, AC-2; listing
 * section 7, LM-16; ADR-012 decision 2, D12-13; E05-S6 spec S6-D9 to S6-D11; criteria SA-1, SA-2, G2-1, LK-2): the set
 * answer equals the detail's, costs one statement count for 1 and 250 pairs, refuses 251 before any statement, joins a
 * read-only caller or none, gives the commands' condition groups, group (ii), and the locking read's own transaction
 * check. The annotation set of {@link SetReadStatementCountPostgresTest}, so they share one context (plan E05S6-P2); the
 * use case is autowired, which the arch test allows a test to do. Needs Docker.
 */
@Import(StatementCounter.Config.class)
class AdvertisingRightSetReadPostgresTest extends IntakeApiTestBase {

	private static final String AGREEMENTS = "/api/v1/staff/brokerage-agreements";
	private static final String ME = "/api/v1/me/brokerage-agreements";
	private static final String OWNER_STATEMENT = "brokerage-agreement-2026-11";
	private static final String UNKNOWN = "01JUNKNOWN0000000000000000";

	@Autowired
	private AdvertisingRightUseCase rights;

	@Autowired
	private StatementCounter counter;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<String> created = new ArrayList<>();
	private final List<String> owners = new ArrayList<>();

	@Override
	String phonePrefix() {
		return "0933";
	}

	@AfterAll
	void deleteAgreementsAndNotices() {
		AgreementFixture.delete(jdbc, created);
		for (String owner : owners) {
			jdbc.update("delete from auth_email_notifications where user_id = ?", owner);
		}
	}

	// ==================== fixtures (as AgreementActivationApiPostgresTest builds them) ====================

	private record Chain(String leadId, TestUser owner, String propertyId, String unitId, String agreementId) {
	}

	private Chain drafted() throws Exception {
		Passed passed = house(unique("Set Read Street"));
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		String propertyId = completed(passed.leadId()).path("propertyId").asString();
		String unit = blockOk(sale, passed.leadId()).path("readiness").path("units").path(0).path("unitId").asString();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("leadId", passed.leadId());
		body.put("unitId", unit);
		body.put("transactionType", "LONG_TERM_RENT");
		body.put("startDate", "2027-01-01");
		body.put("endDate", "2027-12-31");
		Result answer = api.post(sale, AGREEMENTS, body);
		assertEquals(201, answer.status(), answer.rawBody());
		String id = answer.body().path("agreementId").asString();
		created.add(id);
		Map<String, Object> draft = AgreementFeePolicyApiPostgresTest.complete(rowVersion(id));
		draft.put("startDate", today().toString());
		draft.put("endDate", today().plusMonths(6).toString());
		Result saved = api.put(sale, AGREEMENTS + "/" + id + "/draft", draft);
		assertEquals(204, saved.status(), saved.rawBody());
		return new Chain(passed.leadId(), owner, propertyId, unit, id);
	}

	private Chain confirmed() throws Exception {
		Chain chain = drafted();
		Map<String, Object> send = new LinkedHashMap<>();
		send.put("expectedDataVersion", rowVersion(chain.agreementId()));
		Result sent = api.post(sale, AGREEMENTS + "/" + chain.agreementId() + "/draft/submission", send);
		assertEquals(204, sent.status(), sent.rawBody());
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("agreementId", chain.agreementId());
		item.put("versionNo", 1);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("currentPassword", passwordOf(chain.owner()));
		body.put("statementVersion", OWNER_STATEMENT);
		body.put("versions", List.of(item));
		Result answer = api.post(chain.owner(), ME + "/confirmations", body);
		assertEquals(204, answer.status(), answer.rawBody());
		return chain;
	}

	private Result activate(String agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedDataVersion", rowVersion(agreementId));
		body.put("versionNo", 1);
		body.put("statementVersion", ActivationStatement.VERSION);
		body.put("documentNumber", "HDMG-2026/014");
		body.put("signedOn", today().toString());
		body.put("sunvibeSignatoryName", "Nguyen Van A");
		body.put("sunvibeSignatoryTitle", "Director");
		return api.post(manager, AGREEMENTS + "/" + agreementId + "/activation", body);
	}

	private Chain activated() throws Exception {
		Chain chain = confirmed();
		Result answer = activate(chain.agreementId());
		assertEquals(204, answer.status(), answer.rawBody());
		return chain;
	}

	/** A fixture agreement on a unit of its own, ACTIVE with its version 1 EFFECTIVE over a period that holds today. */
	private String inEffectFixture(String status, LocalDate start, LocalDate end) {
		String id = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), status, "EFFECTIVE");
		created.add(id);
		jdbc.update("update brokerage_agreement_versions set start_date = ?, end_date = ? where agreement_id = ?",
				Date.valueOf(start), Date.valueOf(end), id);
		return id;
	}

	private String unitOf(String agreementId) {
		return jdbc.queryForObject("select unit_id from brokerage_agreements where id = ?", String.class, agreementId);
	}

	private long rowVersion(String agreementId) {
		return jdbc.queryForObject("select version from brokerage_agreements where id = ?", Long.class, agreementId);
	}

	private JsonNode detailAdvertising(String agreementId) throws Exception {
		Result answer = api.get(manager, AGREEMENTS + "/" + agreementId);
		assertEquals(200, answer.status(), answer.rawBody());
		return answer.body().path("advertising");
	}

	private <T> T inTransaction(boolean readOnly, java.util.function.Supplier<T> work) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setReadOnly(readOnly);
		return template.execute(status -> work.get());
	}

	/** The answer equals the detail's advertising, field by field. */
	private static void assertSameAnswer(JsonNode detail, AdvertisingRightView view) {
		assertEquals(detail.path("allowed").asBoolean(), view.allowed(), detail.toString());
		List<String> groups = new ArrayList<>();
		detail.path("reasonGroups").forEach(group -> groups.add(group.asString()));
		assertEquals(groups, view.reasonGroups());
		assertEquals(detail.path("endDate").isNull() ? null : LocalDate.parse(detail.path("endDate").asString()),
				view.endDateOrNull());
		assertEquals(detail.path("endsAt").isNull() ? null : Instant.parse(detail.path("endsAt").asString()),
				view.endsAtOrNull());
		assertEquals(detail.path("photoConsent").path("interior").asBoolean(), view.interiorPhotos());
		assertEquals(detail.path("photoConsent").path("exterior").asBoolean(), view.exteriorPhotos());
		JsonNode terms = detail.path("termsInEffect");
		if (terms.isNull()) {
			assertEquals(null, view.termsInEffectOrNull());
		} else {
			assertEquals(terms.path("monthlyRent").path("amount").asLong(), view.termsInEffectOrNull().monthlyRentOrNull());
			assertEquals(terms.path("minimumTermMonths").asInt(), view.termsInEffectOrNull().minimumTermMonthsOrNull());
			assertEquals(terms.path("paymentPeriodMonths").asInt(),
					view.termsInEffectOrNull().paymentPeriodMonthsOrNull());
			assertEquals(terms.path("mandatoryFees").size(), view.termsInEffectOrNull().mandatoryFees().size());
			assertEquals(terms.path("deposit").path("months").asInt(), view.termsInEffectOrNull().depositOrNull()
					.monthsOrNull());
		}
		assertEquals(detail.path("pendingVersion").isNull(), view.pendingStartDateOrNull() == null);
	}

	// ==================== SA-1: the set answer ====================

	/** SA-1: two answers, each equal to the detail's; a pair of another unit and an unknown agreement left out. */
	@Test
	void answers_pairs_equalTheDetailOthersLeftOut() throws Exception {
		Chain allowed = activated();
		Chain drafting = drafted();

		List<AdvertisingRightView> answers = rights.answers(List.of(new AdvertisingPair(allowed.unitId(),
						allowed.agreementId()), new AdvertisingPair(drafting.unitId(), drafting.agreementId()),
				new AdvertisingPair(drafting.unitId(), allowed.agreementId()), new AdvertisingPair(allowed.unitId(),
						UNKNOWN)));

		assertEquals(List.of(allowed.agreementId(), drafting.agreementId()),
				answers.stream().map(AdvertisingRightView::agreementId).toList());
		assertTrue(answers.get(0).allowed());
		assertEquals(List.of("NOT_IN_EFFECT"), answers.get(1).reasonGroups());
		assertSameAnswer(detailAdvertising(allowed.agreementId()), answers.get(0));
		assertSameAnswer(detailAdvertising(drafting.agreementId()), answers.get(1));
		assertFalse(answers.get(0).ended());
		assertFalse(answers.get(1).ended());
	}

	/**
	 * SA-1, S6-D9: one pair and 250 pairs cost the same statements; both sets reach the reads of other modules (an
	 * ACTIVE agreement on a unit with a completed intake, ownership rows and a declared owner, and 249 ACTIVE fixture
	 * agreements in effect, each on a unit of its own).
	 */
	@Test
	void answers_oneAnd250Pairs_sameStatementCount() throws Exception {
		Chain chain = activated();
		List<AdvertisingPair> pairs = new ArrayList<>();
		pairs.add(new AdvertisingPair(chain.unitId(), chain.agreementId()));
		for (int index = 0; index < 249; index++) {
			String id = inEffectFixture("ACTIVE", today().minusDays(30), today().plusDays(150));
			pairs.add(new AdvertisingPair(unitOf(id), id));
		}
		List<AdvertisingRightView> one = new ArrayList<>();
		List<AdvertisingRightView> all = new ArrayList<>();

		long forOne = counter.count(() -> one.addAll(rights.answers(pairs.subList(0, 1))));
		long forAll = counter.count(() -> all.addAll(rights.answers(pairs)));

		assertEquals(1, one.size());
		assertEquals(250, all.size());
		assertTrue(one.getFirst().allowed());
		assertTrue(all.stream().skip(1).allMatch(view -> view.reasonGroups().contains("BASIS")), all.toString());
		assertTrue(forOne > 3, "the set read reaches the other modules: " + forOne);
		assertEquals(forOne, forAll);
	}

	/** SA-1: 251 distinct pairs are refused before any statement. */
	@Test
	void answers_251Pairs_illegalArgumentNoStatement() {
		List<AdvertisingPair> pairs = new ArrayList<>();
		for (int index = 0; index < 251; index++) {
			pairs.add(new AdvertisingPair(ids.generateId(), ids.generateId()));
		}

		long statements = counter.count(() -> assertThrows(IllegalArgumentException.class, () -> rights.answers(pairs)));

		assertEquals(0, statements);
	}

	/** SA-1: inside a read-only transaction it answers and writes nothing; without one it still answers. */
	@Test
	void answers_readOnlyOrNoTransaction_answersWritesNothing() throws Exception {
		Chain chain = activated();
		AdvertisingPair pair = new AdvertisingPair(chain.unitId(), chain.agreementId());
		Map<String, Object> agreement = jdbc.queryForMap("select * from brokerage_agreements where id = ?",
				chain.agreementId());
		int accessRows = count("select count(*) from brokerage_agreement_access_logs where agreement_id = ?",
				chain.agreementId());

		List<AdvertisingRightView> readOnly = inTransaction(true, () -> rights.answers(List.of(pair)));
		List<AdvertisingRightView> none = rights.answers(List.of(pair));

		assertTrue(readOnly.getFirst().allowed());
		assertEquals(readOnly, none);
		assertEquals(agreement, jdbc.queryForMap("select * from brokerage_agreements where id = ?", chain.agreementId()));
		assertEquals(accessRows, count("select count(*) from brokerage_agreement_access_logs where agreement_id = ?",
				chain.agreementId()));
	}

	// ==================== SA-2: parity with the commands' rerun ====================

	/**
	 * SA-2 (PLAN6-D1, S6-D9): for each stop, the groups an activation attempt answers equal the condition groups the set
	 * answer gives once the same agreement is ACTIVE with its version EFFECTIVE.
	 */
	@Test
	void answers_stops_conditionGroupsEqualTheActivationRerun() throws Exception {
		Chain held = confirmed();
		hold(held.leadId());
		assertParity(held);

		Chain unit = confirmed();
		Result suspended = suspend(manager, unit.leadId(), unit.unitId(), "Room only", versionOf(manager, unit.leadId()));
		assertEquals(200, suspended.status(), suspended.rawBody());
		assertParity(unit);

		Chain coOwner = confirmed();
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
				+ " valid_to, created_at) values (?, ?, ?, 'CO_OWNER', 'UNVERIFIED', ?, null, ?)", ids.generateId(),
				coOwner.propertyId(), ids.generateId(), LocalDate.of(2026, 1, 1), Timestamp.from(Instant.now()));
		assertParity(coOwner);

		Chain staff = confirmed();
		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'",
				staff.owner().userId());
		assertParity(staff);
		jdbc.update("delete from user_roles where user_id = ? and role_id = (select id from roles where name = 'SALE')",
				staff.owner().userId());
	}

	private void assertParity(Chain chain) throws Exception {
		Result refused = activate(chain.agreementId());
		assertEquals(409, refused.status(), refused.rawBody());
		assertEquals("AGREEMENT_NOT_ELIGIBLE", refused.body().path("code").asString(), refused.rawBody());
		List<String> rerun = new ArrayList<>();
		refused.body().path("errors").forEach(entry -> rerun.add(entry.path("field").asString()
				.replace("reasonGroups.", "")));
		jdbc.update("update brokerage_agreement_versions set status = 'EFFECTIVE' where agreement_id = ?",
				chain.agreementId());
		jdbc.update("update brokerage_agreements set status = 'ACTIVE', version = version + 1 where id = ?",
				chain.agreementId());

		AdvertisingRightView answer = rights.answers(List.of(new AdvertisingPair(chain.unitId(), chain.agreementId())))
				.getFirst();

		assertFalse(rerun.isEmpty(), refused.rawBody());
		assertEquals(rerun, answer.reasonGroups(), refused.rawBody());
	}

	// ==================== G2-1: group (ii) ====================

	/** G2-1: the unfinished agreement of each unit of the type; another type answers empty. */
	@Test
	void unfinishedOf_unitsAndType_unfinishedAgreements() throws Exception {
		Chain first = activated();
		Chain second = drafted();

		List<UnfinishedAgreementView> found = rights.unfinishedOf(List.of(first.unitId(), second.unitId(),
				ids.generateId()), "LONG_TERM_RENT");

		assertEquals(List.of(new UnfinishedAgreementView(first.unitId(), first.agreementId(), "LONG_TERM_RENT"),
				new UnfinishedAgreementView(second.unitId(), second.agreementId(), "LONG_TERM_RENT")).stream()
				.sorted(java.util.Comparator.comparing(UnfinishedAgreementView::agreementId)).toList(), found.stream()
				.sorted(java.util.Comparator.comparing(UnfinishedAgreementView::agreementId)).toList());
		assertEquals(List.of(), rights.unfinishedOf(List.of(first.unitId(), second.unitId()), "SALE"));
	}

	/**
	 * G2-1: each agreement's unit, type, ended, and the basis lead's current assignee, the new one after a reassignment;
	 * final states and an ACTIVE agreement past its end date read ended; no term, fee, date, contract number or owner.
	 */
	@Test
	void standings_agreements_endedAndCurrentAssignee() throws Exception {
		Chain first = activated();
		Chain second = drafted();

		List<AgreementStandingView> standings = rights.standings(List.of(first.agreementId(), second.agreementId(),
				UNKNOWN));

		assertEquals(List.of(new AgreementStandingView(first.agreementId(), first.unitId(), "LONG_TERM_RENT", false,
						sale.userId()),
				new AgreementStandingView(second.agreementId(), second.unitId(), "LONG_TERM_RENT", false, sale.userId())),
				standings);
		fixture.checklists().reassign(leader, first.leadId(), otherSale);
		assertEquals(otherSale.userId(), rights.standings(List.of(first.agreementId())).getFirst()
				.basisLeadAssigneeIdOrNull());

		List<String> ended = new ArrayList<>();
		for (String status : List.of("EXPIRED", "TERMINATED", "CANCELLED")) {
			ended.add(inEffectFixture(status, today().minusDays(30), today().plusDays(150)));
		}
		ended.add(inEffectFixture("ACTIVE", today().minusDays(60), today().minusDays(2)));
		List<AgreementStandingView> endedViews = rights.standings(ended);
		assertEquals(4, endedViews.size());
		assertTrue(endedViews.stream().allMatch(AgreementStandingView::ended), endedViews.toString());
		assertEquals(List.of("agreementId", "basisLeadAssigneeIdOrNull", "ended", "transactionType", "unitId"),
				java.util.Arrays.stream(AgreementStandingView.class.getRecordComponents())
						.map(java.lang.reflect.RecordComponent::getName).sorted().toList());
	}

	// ==================== LK-2: the locking read's own transaction check ====================

	/** LK-2: with no transaction and inside a read-only one, its own IllegalStateException, and no statement. */
	@Test
	void answerUnderShareLock_noOrReadOnlyTransaction_illegalStateNoStatement() throws Exception {
		Chain chain = activated();
		AdvertisingPair pair = new AdvertisingPair(chain.unitId(), chain.agreementId());

		long withoutTransaction = counter.count(() -> {
			IllegalStateException refused = assertThrows(IllegalStateException.class,
					() -> rights.answerUnderShareLock(pair));
			assertEquals(IllegalStateException.class, refused.getClass());
		});
		// the refused call marks the joined transaction rollback-only, so the template rolls it back on purpose
		TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
		readOnlyTemplate.setReadOnly(true);
		long readOnly = readOnlyTemplate.execute(status -> {
			status.setRollbackOnly();
			return counter.count(() -> {
				IllegalStateException refused = assertThrows(IllegalStateException.class,
						() -> rights.answerUnderShareLock(pair));
				assertEquals(IllegalStateException.class, refused.getClass());
			});
		});

		assertEquals(0, withoutTransaction);
		assertEquals(0, readOnly);
	}

	/** LK-2: inside a read-write transaction it answers; another unit or an unknown agreement answers empty. */
	@Test
	void answerUnderShareLock_readWrite_answersOrEmpty() throws Exception {
		Chain chain = activated();
		Chain other = drafted();

		Optional<AdvertisingRightView> answer = inTransaction(false,
				() -> rights.answerUnderShareLock(new AdvertisingPair(chain.unitId(), chain.agreementId())));
		Optional<AdvertisingRightView> otherUnit = inTransaction(false,
				() -> rights.answerUnderShareLock(new AdvertisingPair(other.unitId(), chain.agreementId())));
		Optional<AdvertisingRightView> unknown = inTransaction(false,
				() -> rights.answerUnderShareLock(new AdvertisingPair(chain.unitId(), UNKNOWN)));

		assertTrue(answer.orElseThrow().allowed());
		assertEquals(8_000_000L, answer.orElseThrow().termsInEffectOrNull().monthlyRentOrNull());
		assertTrue(otherUnit.isEmpty());
		assertTrue(unknown.isEmpty());
	}
}
