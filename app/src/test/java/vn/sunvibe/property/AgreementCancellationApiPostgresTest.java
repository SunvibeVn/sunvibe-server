package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The cancellation over HTTP on PostgreSQL (brokerage-agreement BR-5, BR-16, US-1 AC-5; E05-S8 spec 3.4, 4, S8-D7;
 * CA-1 to CA-4): the assigned Sale cancels an agreement that never took effect, against the data version read; its
 * pending version cancelled; the unit free for a new agreement; nothing written on a refusal. Needs Docker.
 */
class AgreementCancellationApiPostgresTest extends AgreementStopApiTestBase {

	@Override
	String phonePrefix() {
		return "0923";
	}

	/** CA-1: drafting, awaiting the owner and confirmed by the owner: each cancelled with one row and one event. */
	@Test
	void cancel_draftingAndAwaiting_cancelledEvent() throws Exception {
		List<Chain> chains = List.of(drafted(today().plusDays(10), today().plusMonths(6)),
				sent(today().plusDays(10), today().plusMonths(6)), confirmed(today().plusDays(10), today().plusMonths(6)));
		for (Chain chain : chains) {
			String id = chain.agreementId();
			int outbox = outboxRows(chain.unitId());

			Result answer = cancel(sale, id, rowVersion(id));

			assertEquals(204, answer.status(), answer.rawBody());
			Map<String, Object> row = agreementRow(id);
			assertEquals("CANCELLED", row.get("status"));
			assertNotNull(row.get("ended_at"));
			assertEquals("CANCELLED", versionStatus(id, 1));
			assertEquals("CANCELLED 1 " + sale.userId(), events(id).getLast());
			assertEquals(outbox + 1, outboxRows(chain.unitId()));
		}
		assertEquals("CONFIRMED", versionRow(chains.get(2).agreementId(), 1).get("owner_answer"));
	}

	/** CA-2: a manager, an agreement in effect, an ended one, a stale data version and bad bodies; nothing written. */
	@Test
	void cancel_refusals_codesNothingWritten() throws Exception {
		Chain drafting = drafted(today().plusDays(10), today().plusMonths(6));
		String id = drafting.agreementId();
		WriteSnapshot before = snapshot(id);
		assertCode(403, "AGREEMENT_ASSIGNEE_ONLY", cancel(manager, id, rowVersion(id)));
		Result stale = cancel(sale, id, rowVersion(id) + 1);
		assertCode(409, "CONCURRENT_MODIFICATION", stale);
		assertEquals("expectedDataVersion", stale.body().path("errors").path(0).path("field").asString());
		assertValidation(cancel(sale, id, null), "expectedDataVersion", null);
		assertValidation(cancel(sale, id, -1), "expectedDataVersion", null);
		assertMalformed(postRaw(sale, AGREEMENTS + "/" + id + "/cancellation", "not json"));
		assertNothingWritten(before);

		Chain active = inEffect();
		assertRefusedNothingWritten(active.agreementId(), "AGREEMENT_TOOK_EFFECT");
		Chain suspended = inEffect();
		suspended(manager, suspended.agreementId());
		assertRefusedNothingWritten(suspended.agreementId(), "AGREEMENT_TOOK_EFFECT");
		assertEquals(204, terminate(sale, id, ownerWithdrew()).status());
		assertRefusedNothingWritten(id, "AGREEMENT_ENDED");
	}

	/** The access cases, the invisible actor a SALE who is not the lead's assignee. */
	@Test
	void cancel_unknownMalformedNoTokenCustomerInvisible_notFoundUnauthorizedForbidden() throws Exception {
		Chain chain = drafted(today().plusDays(10), today().plusMonths(6));
		String id = chain.agreementId();
		WriteSnapshot before = snapshot(id);

		assertEquals(401, cancel(null, id, rowVersion(id)).status());
		assertCode(403, "FORBIDDEN", cancel(chain.owner(), id, rowVersion(id)));
		Result unknown = cancel(sale, "01JUNKNOWN0000000000000000", 0);
		Result malformed = cancel(sale, "not-an-id", 0);
		assertCode(404, "NOT_FOUND", unknown);
		assertCode(404, "NOT_FOUND", malformed);
		assertEquals(unknown.body().path("message"), malformed.body().path("message"));
		assertEquals(unknown.body().path("errors"), malformed.body().path("errors"));
		assertCode(404, "NOT_FOUND", cancel(otherSale, id, rowVersion(id)));
		assertNothingWritten(before);
	}

	/** CA-3: the cancelled agreement frees the unit; the owner still reads the one sent to them, now withdrawn. */
	@Test
	void cancel_thenCreate_newAgreementOwnerReadsWithdrawn() throws Exception {
		Chain chain = sent(today().plusDays(10), today().plusMonths(6));
		assertEquals(204, cancel(sale, chain.agreementId(), rowVersion(chain.agreementId())).status());

		Result created = createResult(sale, chain.leadId(), chain.unitId());

		assertEquals(201, created.status(), created.rawBody());
		Result owner = api.get(chain.owner(), ME);
		assertEquals(200, owner.status(), owner.rawBody());
		JsonNode item = null;
		for (JsonNode each : owner.body().path("items")) {
			if (chain.agreementId().equals(each.path("agreementId").asString())) {
				item = each;
			}
		}
		assertNotNull(item, owner.rawBody());
		assertEquals("CANCELLED", item.path("status").asString());
		assertEquals("CANCELLED", item.path("versions").path(0).path("status").asString());
	}

	/** CA-4: a version past its 7 days lapsed at its deadline first; the cancellation's row after; v1 LAPSED. */
	@Test
	void cancel_lapseDueNotStored_lapsedRowThenCancelled() throws Exception {
		Chain chain = sent(today().plusDays(10), today().plusMonths(6));
		Instant sentAt = sentDaysAgo(chain.agreementId(), 1, 9);

		assertEquals(204, cancel(sale, chain.agreementId(), rowVersion(chain.agreementId())).status());

		List<String> events = events(chain.agreementId());
		assertEquals(List.of("VERSION_LAPSED 1 -", "CANCELLED 1 " + sale.userId()), events.subList(events.size() - 2,
				events.size()));
		assertEquals(sentAt.plus(java.time.Duration.ofDays(7)), eventAt(chain.agreementId(), "VERSION_LAPSED"));
		assertEquals("LAPSED", versionStatus(chain.agreementId(), 1));
		assertEquals("CANCELLED", status(chain.agreementId()));
	}

	private void assertRefusedNothingWritten(String agreementId, String code) throws Exception {
		WriteSnapshot before = snapshot(agreementId);
		assertCode(409, code, cancel(sale, agreementId, rowVersion(agreementId)));
		assertNothingWritten(before);
	}
}
