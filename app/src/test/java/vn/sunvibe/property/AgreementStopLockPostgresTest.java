package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.brokerage.AgreementFixture;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageAgreement;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;

/**
 * The agreement row lock on PostgreSQL (brokerage-agreement E05-S8 spec S8-D8 step 3, S8-D18, S8-D2, A-1; LK-2; plan
 * E05S8-P6, P9): the locked read waits for a writer that holds the row and then reads what it committed; every guarded
 * update takes the agreement row before any row of its graph, so a writer that waits on a held agreement row holds no
 * version row; the selection of the due expiries. Needs Docker; a Linux run is the evidence of a race test (testing.md).
 */
class AgreementStopLockPostgresTest extends AgreementStopApiTestBase {

	@Override
	String phonePrefix() {
		return "0926";
	}

	// ==================== the locked read ====================

	@Test
	void findByIdForUpdate_rowHeldByWriter_waitsThenReadsTheCommittedState() throws Exception {
		String agreementId = fixture("DRAFTING", "DRAFT");
		Future<Optional<BrokerageAgreement>> read;
		try (Hold writer = new Hold(() -> {
			jdbc.update("update brokerage_agreements set status = 'CANCELLED', version = version + 1 where id = ?",
					agreementId);
			jdbc.update("update brokerage_agreement_versions set status = 'CANCELLED' where agreement_id = ?",
					agreementId);
		})) {
			read = executor.submit(() -> tx(() -> agreements.findByIdForUpdate(agreementId)));
			awaitWaiters(1);
			assertFalse(read.isDone());
		}

		BrokerageAgreement locked = read.get(30, TimeUnit.SECONDS).orElseThrow();

		assertEquals(AgreementStatus.CANCELLED, locked.getStatus());
		assertEquals(VersionStatus.CANCELLED, locked.getVersions().getFirst().getStatus());
		assertEquals(1L, locked.getRowVersionOrNull());
	}

	@Test
	void findByIdForUpdate_unknownId_empty() {
		assertTrue(tx(() -> agreements.findByIdForUpdate(ids.generateId())).isEmpty());
	}

	// ==================== LK-2: the agreement row before any row of its graph ====================

	/**
	 * LK-2: the owner's confirmation waits on the held agreement row before it writes its version row, so the holder
	 * updates that version row without waiting; after the holder's commit the confirmation's merge meets the moved row
	 * version and writes nothing. No deadlock either side.
	 */
	@Test
	void ownerConfirm_agreementRowHeld_waitsHoldingNoVersionRowThenConflict() throws Exception {
		Chain chain = sent(today().plusDays(10), today().plusMonths(6));
		String agreementId = chain.agreementId();
		Future<Result> confirmation;
		try (Hold hold = holdAgreementRow(agreementId)) {
			confirmation = executor.submit(() -> confirmResult(chain.owner(), agreementId, 1));
			awaitWaiters(1);
			assertEquals(0, versionRowLocksOfWaiters());
			hold.beforeCommit(() -> {
				jdbc.update("update brokerage_agreement_versions set updated_at = ? where agreement_id = ?",
						Timestamp.from(Instant.now()), agreementId);
				jdbc.update("update brokerage_agreements set version = version + 1 where id = ?", agreementId);
			});
		}

		Result answer = confirmation.get(60, TimeUnit.SECONDS);

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertEquals("AWAITING_OWNER", versionStatus(agreementId, 1));
		assertEquals(0, eventCount(agreementId, "OWNER_CONFIRMED"));
	}

	/** LK-2: the start store waits on the held agreement row holding no version row, then stores nothing. */
	@Test
	void storeDueStarts_agreementRowHeld_waitsHoldingNoVersionRowThenStoresNothing() throws Exception {
		Chain chain = activated(today().plusDays(2), today().plusMonths(6));
		String agreementId = chain.agreementId();
		assertEquals("ACTIVATED", versionStatus(agreementId, 1));
		movePeriod(agreementId, 1, today().minusDays(1), today().plusMonths(6));
		Future<Integer> run;
		try (Hold hold = holdAgreementRow(agreementId)) {
			run = executor.submit(() -> commands.storeDueStarts());
			awaitWaiters(1);
			assertEquals(0, versionRowLocksOfWaiters());
			hold.beforeCommit(() -> {
				jdbc.update("update brokerage_agreement_versions set updated_at = ? where agreement_id = ?",
						Timestamp.from(Instant.now()), agreementId);
				jdbc.update("update brokerage_agreements set version = version + 1 where id = ?", agreementId);
			});
		}

		assertEquals(0, run.get(60, TimeUnit.SECONDS));
		assertEquals("ACTIVATED", versionStatus(agreementId, 1));
		assertEquals(0, eventCount(agreementId, "VERSION_STARTED"));
	}

	/**
	 * The RowExclusiveLock on the versions table held by the backends that wait for an ungranted lock in this database:
	 * a writer that took a version row before the agreement row would hold one.
	 */
	private int versionRowLocksOfWaiters() {
		return count("select count(*) from pg_locks l where l.mode = 'RowExclusiveLock'"
				+ " and l.relation = 'brokerage_agreement_versions'::regclass and l.pid in (select w.pid from pg_locks w"
				+ " join pg_stat_activity a on a.pid = w.pid where not w.granted and a.datname = current_database())");
	}

	// ==================== the selection of the due expiries (plan E05S8-P6) ====================

	@Test
	void dueExpiryAgreementIds_endedYesterdayOrToday_onlyEndedSelected() {
		String endedYesterday = fixture("ACTIVE", "EFFECTIVE");
		movePeriod(endedYesterday, 1, today().minusMonths(3), today().minusDays(1));
		String endsToday = fixture("ACTIVE", "EFFECTIVE");
		movePeriod(endsToday, 1, today().minusMonths(3), today());
		String suspendedEnded = fixture("SUSPENDED", "EFFECTIVE");
		movePeriod(suspendedEnded, 1, today().minusMonths(3), today().minusDays(1));
		String activatedEnded = fixture("ACTIVE", "ACTIVATED");
		movePeriod(activatedEnded, 1, today().minusMonths(3), today().minusDays(1));

		List<String> due = tx(() -> agreements.dueExpiryAgreementIds(today(), 100));

		assertTrue(due.contains(endedYesterday), due.toString());
		assertTrue(due.contains(suspendedEnded), due.toString());
		assertTrue(due.contains(activatedEnded), due.toString());
		assertFalse(due.contains(endsToday), due.toString());
	}

	/** SV-326 (6): an agreement in effect with no version in effect is never selected; a stored final one neither. */
	@Test
	void dueExpiryAgreementIds_noVersionInEffect_neverSelected() {
		String noVersionInEffect = fixture("ACTIVE", "CANCELLED");
		movePeriod(noVersionInEffect, 1, today().minusMonths(3), today().minusDays(1));
		String storedExpired = fixture("EXPIRED", "EFFECTIVE");
		movePeriod(storedExpired, 1, today().minusMonths(3), today().minusDays(1));
		String drafting = fixture("DRAFTING", "DRAFT");
		movePeriod(drafting, 1, today().minusMonths(3), today().minusDays(1));

		List<String> due = tx(() -> agreements.dueExpiryAgreementIds(today(), 100));

		assertFalse(due.contains(noVersionInEffect), due.toString());
		assertFalse(due.contains(storedExpired), due.toString());
		assertFalse(due.contains(drafting), due.toString());
	}

	@Test
	void dueExpiryAgreementIds_limit_atMostLimitInIdOrder() {
		List<String> mine = new ArrayList<>();
		for (int index = 0; index < 3; index++) {
			String agreementId = fixture("ACTIVE", "EFFECTIVE");
			movePeriod(agreementId, 1, today().minusMonths(3), today().minusDays(1 + index));
			mine.add(agreementId);
		}
		List<String> sorted = mine.stream().sorted().toList();

		List<String> all = tx(() -> agreements.dueExpiryAgreementIds(today(), 100));
		List<String> two = tx(() -> agreements.dueExpiryAgreementIds(today(), 2));

		assertEquals(sorted, all.stream().filter(mine::contains).toList());
		assertEquals(all.stream().sorted().toList(), all);
		assertEquals(all.subList(0, 2), two);
	}

	/** A fixture agreement of a new unit and lead in that state, its version 1 in that state. */
	private String fixture(String agreementStatus, String versionStatus) {
		String agreementId = AgreementFixture.insert(jdbc, ids, ids.generateId(), ids.generateId(), agreementStatus,
				versionStatus);
		created.add(agreementId);
		return agreementId;
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}
}
