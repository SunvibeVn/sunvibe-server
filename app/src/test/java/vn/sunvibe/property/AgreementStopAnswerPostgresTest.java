package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.application.usecase.AgreementStandingView;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The published answers after a stop on PostgreSQL (brokerage-agreement US-5 AC-1, AC-3 to AC-5, BR-2, BR-3, BR-12,
 * BR-13; E05-S8 spec S8-D13, S8-D14; AV-1, CS-1, CS-2): each stop is reported from its moment, the set read, the
 * standings and the detail agree, and endDate and endsAt stay the planned end of the version that counts; the stops
 * recorded in other modules turn the answer off from the next read and back when cleared, the stored state unchanged; an
 * ended basis never returns, and the way on is the termination. Needs Docker.
 */
class AgreementStopAnswerPostgresTest extends AgreementStopApiTestBase {

	@Override
	String phonePrefix() {
		return "0920";
	}

	// ==================== AV-1 ====================

	/** AV-1: SUSPENDED and the final states reached through the commands, read right after each. */
	@Test
	void answers_eachStopFromItsMoment_setStandingsAndDetailAgree() throws Exception {
		LocalDate end = today().plusMonths(6);
		Chain suspended = inEffect();
		assertAgree(suspended, false, List.of(), end);
		suspended(manager, suspended.agreementId());
		assertAgree(suspended, false, List.of("SUSPENDED"), end);

		Chain terminatedFromActive = inEffect();
		assertEquals(204, terminate(sale, terminatedFromActive.agreementId(), ownerWithdrew()).status());
		assertAgree(terminatedFromActive, true, List.of("ENDED"), end);

		Chain terminatedFromSuspended = inEffect();
		suspended(manager, terminatedFromSuspended.agreementId());
		assertEquals(204, terminate(sale, terminatedFromSuspended.agreementId(), ownerWithdrew()).status());
		assertAgree(terminatedFromSuspended, true, List.of("ENDED"), end);

		Chain terminatedFromDrafting = drafted(today().plusDays(10), end);
		assertAgree(terminatedFromDrafting, false, List.of("NOT_IN_EFFECT"), null);
		assertEquals(204, terminate(sale, terminatedFromDrafting.agreementId(), ownerWithdrew()).status());
		assertAgree(terminatedFromDrafting, true, List.of("ENDED"), null);

		Chain cancelled = drafted(today().plusDays(10), end);
		assertEquals(204, cancel(sale, cancelled.agreementId(), rowVersion(cancelled.agreementId())).status());
		assertAgree(cancelled, true, List.of("ENDED"), null);

		// terminated before its activated version started: no version that counts, so no end (spec S8-D13)
		Chain notStarted = activated(today().plusDays(2), end);
		assertAgree(notStarted, false, List.of("NOT_STARTED"), end);
		assertEquals(204, terminate(sale, notStarted.agreementId(), ownerWithdrew()).status());
		assertAgree(notStarted, true, List.of("ENDED"), null);
	}

	/**
	 * AV-1, EXPIRED: an activated v1 that started and ended with neither stored reads the same answer before the store
	 * (effective) and after it (stored); v1 stored EFFECTIVE, its start's row before the expiry's.
	 */
	@Test
	void answers_expiredBeforeAndAfterStore_sameAnswer() throws Exception {
		Chain chain = activated(today().plusDays(2), today().plusMonths(6));
		String id = chain.agreementId();
		movePeriod(id, 1, today().minusMonths(3), today().minusDays(1));
		AdvertisingRightView before = answer(chain);
		AgreementStandingView standingBefore = standing(id);
		JsonNode advertisingBefore = detail(manager, id).path("advertising");
		assertEquals("ACTIVE", status(id));

		commands.storeDueExpiries();

		assertEquals(before, answer(chain));
		assertEquals(standingBefore, standing(id));
		assertEquals(advertisingBefore, detail(manager, id).path("advertising"));
		assertTrue(before.ended(), before.toString());
		assertEquals(List.of("ENDED"), before.reasonGroups());
		assertEquals(today().minusDays(1), before.endDateOrNull());
		assertEquals(startOf(today()), before.endsAtOrNull());
		assertEquals("EXPIRED", status(id));
		assertEquals("EFFECTIVE", versionStatus(id, 1));
		List<String> events = events(id);
		assertEquals(List.of("VERSION_STARTED 1 -", "EXPIRED 1 -"), events.subList(events.size() - 2, events.size()));
	}

	// ==================== CS-1, CS-2 ====================

	/**
	 * CS-1: a hold of the basis lead, a whole-lead suspension of another lead of the property and a staff role given to
	 * the owner each turn the answer off from the next read, the stored status still ACTIVE and the list row showing the
	 * group; each cleared, the next read is allowed again.
	 */
	@Test
	void advertising_stopsRecordedElsewhere_offFromNextReadAndBack() throws Exception {
		TwoLeads pair = twoLeadsOnOneUnit();
		Chain chain = pair.chain();
		assertAllowed(chain);

		hold(chain.leadId());
		assertOff(chain, "BASIS", true);
		jdbc.update("update owner_leads set status = 'QUALIFIED', version = version + 1 where id = ?", chain.leadId());
		assertAllowed(chain);

		Result suspension = suspend(manager, pair.otherLeadId(), null, "Data request",
				versionOf(manager, pair.otherLeadId()));
		assertEquals(200, suspension.status(), suspension.rawBody());
		assertOff(chain, "PROPERTY", true);
		String suspensionId = jdbc.queryForObject("select id from inventory_suspensions where lead_id = ?", String.class,
				pair.otherLeadId());
		Result release = release(manager, pair.otherLeadId(), suspensionId, "The request was answered",
				versionOf(manager, pair.otherLeadId()));
		assertEquals(200, release.status(), release.rawBody());
		assertAllowed(chain);

		jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'SALE'",
				chain.owner().userId());
		try {
			assertOff(chain, "OWNER", false);
		} finally {
			jdbc.update("delete from user_roles where user_id = ? and role_id = (select id from roles where name = 'SALE')",
					chain.owner().userId());
		}
		assertAllowed(chain);
	}

	/** CS-2: the basis lead closed answers BASIS on every later read; the Sale then terminates the agreement. */
	@Test
	void advertising_basisEnded_neverBackThenTerminated() throws Exception {
		Chain chain = inEffect();
		assertAllowed(chain);

		close(chain.leadId());
		assertOff(chain, "BASIS", true);
		assertOff(chain, "BASIS", true);
		Result terminated = terminate(sale, chain.agreementId(), ownerWithdrew());

		assertEquals(204, terminated.status(), terminated.rawBody());
		assertEquals("TERMINATED", status(chain.agreementId()));
		AdvertisingRightView answer = answer(chain);
		assertTrue(answer.ended(), answer.toString());
		assertEquals(List.of("ENDED"), answer.reasonGroups());
	}

	// ==================== helpers ====================

	/**
	 * The set read, the standing and the detail's advertising of the chain, read now, agree: the groups, whether it ended,
	 * and the planned end of the version that counts (null without one), never the moment it ended.
	 */
	private void assertAgree(Chain chain, boolean ended, List<String> groups, LocalDate endDateOrNull) throws Exception {
		AdvertisingRightView answer = answer(chain);
		JsonNode detail = detail(manager, chain.agreementId());
		JsonNode advertising = detail.path("advertising");
		Instant endsAtOrNull = endDateOrNull == null ? null : startOf(endDateOrNull.plusDays(1));

		assertEquals(ended, answer.ended(), answer.toString());
		assertEquals(ended, standingEnded(chain.agreementId()), "standing of " + answer);
		assertEquals(groups, answer.reasonGroups(), answer.toString());
		assertEquals(groups, strings(advertising.path("reasonGroups")), advertising.toString());
		assertEquals(groups.isEmpty(), answer.allowed(), answer.toString());
		assertEquals(answer.allowed(), advertising.path("allowed").asBoolean(), advertising.toString());
		assertEquals(endDateOrNull, answer.endDateOrNull(), answer.toString());
		assertEquals(endsAtOrNull, answer.endsAtOrNull(), answer.toString());
		if (endDateOrNull == null) {
			assertTrue(advertising.path("endDate").isNull(), advertising.toString());
			assertTrue(advertising.path("endsAt").isNull(), advertising.toString());
		} else {
			assertEquals(endDateOrNull.toString(), advertising.path("endDate").asString());
			assertEquals(endsAtOrNull, Instant.parse(advertising.path("endsAt").asString()));
		}
		if (ended && endsAtOrNull != null) {
			assertNotEquals(endsAtOrNull, Instant.parse(detail.path("endedAt").asString()), detail.toString());
		}
	}

	/** Off with the group (exactly that group, or among others), not ended, from this read, the stored state ACTIVE. */
	private void assertOff(Chain chain, String group, boolean only) throws Exception {
		AdvertisingRightView answer = answer(chain);
		List<String> detailGroups = strings(detail(manager, chain.agreementId()).path("advertising").path("reasonGroups"));
		JsonNode row = listRow(chain.agreementId());
		List<String> rowGroups = strings(row.path("advertising").path("reasonGroups"));

		assertFalse(answer.allowed(), answer.toString());
		assertFalse(answer.ended(), answer.toString());
		assertFalse(standingEnded(chain.agreementId()));
		for (List<String> groups : List.of(answer.reasonGroups(), detailGroups, rowGroups)) {
			if (only) {
				assertEquals(List.of(group), groups, row.toString());
			} else {
				assertTrue(groups.contains(group), groups.toString());
			}
		}
		assertEquals(answer.reasonGroups(), detailGroups);
		assertEquals(answer.reasonGroups(), rowGroups);
		assertEquals("ACTIVE", status(chain.agreementId()));
		assertEquals("ACTIVE", row.path("status").asString());
	}

	/** Allowed in the set read, the detail and the list row; the stored state ACTIVE. */
	private void assertAllowed(Chain chain) throws Exception {
		AdvertisingRightView answer = answer(chain);
		assertTrue(answer.allowed(), answer.toString());
		assertTrue(detail(manager, chain.agreementId()).path("advertising").path("allowed").asBoolean());
		assertTrue(listRow(chain.agreementId()).path("advertising").path("allowed").asBoolean());
		assertEquals("ACTIVE", status(chain.agreementId()));
	}

	private AgreementStandingView standing(String agreementId) {
		return advertisingRights.standings(List.of(agreementId)).getFirst();
	}

	/** The agreement's row on the manager's first page of 50 of the staff list; it must be there. */
	private JsonNode listRow(String agreementId) throws Exception {
		Result page = api.get(manager, AGREEMENTS + "?size=50");
		assertEquals(200, page.status(), page.rawBody());
		for (JsonNode item : page.body().path("items")) {
			if (agreementId.equals(item.path("agreementId").asString())) {
				return item;
			}
		}
		throw new AssertionError(agreementId + " not on the manager's first page");
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(value -> values.add(value.asString()));
		return values;
	}

	/** An agreement in effect on the one unit of a property, and another lead of the owner linked onto that unit. */
	private record TwoLeads(Chain chain, String otherLeadId) {
	}

	/**
	 * Two houses on one street: the first creates the property and its one unit, the second links onto that unit; the
	 * agreement in effect from today rests on the first lead.
	 */
	private TwoLeads twoLeadsOnOneUnit() throws Exception {
		String street = unique("Stop Answer Street");
		Passed first = house(street);
		TestUser owner = owner();
		owners.add(owner.userId());
		checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		available(first.leadId(), first.spaceIds(), "VACANT");
		JsonNode result = completed(first.leadId());
		String propertyId = result.path("propertyId").asString();
		String unit = result.path("units").path(0).path("unitId").asString();
		units.add(unit);
		Passed second = house(street);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(), List.of(unit)));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		Chain chain = new Chain(first.leadId(), owner, propertyId, unit, create(sale, first.leadId(), unit));
		saveComplete(chain.agreementId(), today(), today().plusMonths(6));
		send(chain.agreementId());
		confirm(owner, chain.agreementId(), 1);
		Result activation = activate(admin, chain.agreementId());
		assertEquals(204, activation.status(), activation.rawBody());
		return new TwoLeads(chain, second.leadId());
	}
}
