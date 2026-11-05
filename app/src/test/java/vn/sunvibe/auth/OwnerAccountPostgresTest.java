package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.application.usecase.role.OwnerLookup;
import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

/**
 * The owner surface of auth-service on a real PostgreSQL (property-create intake spec 5.1, D5-5, D5-10, A-12, SV-029): the
 * standing is read from the row and never from a managed entity, a notice lives and dies with the caller's transaction,
 * and two parallel grants never meet on the primary key. The annotations are those of
 * {@link vn.sunvibe.FlywayPostgresMigrationTest}, so both share one context. Needs Docker, like that test.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class OwnerAccountPostgresTest {

	private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-11-06T02:00:00Z"));
	private static final String ACTOR = "01ACTOR0000000000000000000";
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired
	private OwnerAccountUseCase owners;

	@Autowired
	private UserDomainRepository users;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	/** D5-5: one query on the canonical key, a verified sign-in-able non-staff account. */
	@Test
	void lookup_found_onRealRows() {
		String user = account("ACTIVE", "lookup.found", true);

		OwnerLookup found = owners.lookupConfirmable("LOOKUP.found" + mailSuffix(user));

		assertEquals(OwnerLookup.Outcome.FOUND, found.outcome());
		assertEquals(user, found.userIdOrNull());
	}

	@Test
	void lookup_unverifiedOnRealRows_notFound() {
		String user = account("ACTIVE", "lookup.unverified", false);

		assertEquals(OwnerLookup.notFound(), owners.lookupConfirmable("lookup.unverified" + mailSuffix(user)));
	}

	@Test
	void standing_readsCount() {
		String user = account("ACTIVE", "standing.count", true);
		jdbc.update("UPDATE users SET user_status = 'LOCK' WHERE id = ?", user);
		jdbc.update("UPDATE users SET user_status = 'ACTIVE' WHERE id = ?", user);

		assertEquals(new OwnerStanding(true, 1), owners.ownerStanding(user));
	}

	/** spec 5.1: a password check can leave a stale entity in the context; the standing never reads it. */
	@Test
	void standing_notFromAManagedEntity() {
		String user = account("ACTIVE", "standing.managed", true);

		OwnerStanding standing = new TransactionTemplate(transactionManager).execute(status -> {
			users.findById(UserId.create(user)).orElseThrow();
			jdbc.update("UPDATE users SET user_status = 'LOCK' WHERE id = ?", user);
			return owners.ownerStanding(user);
		});

		assertEquals(new OwnerStanding(false, 1), standing);
	}

	@Test
	void standing_deletedAtNotEligible() {
		String user = account("ACTIVE", "standing.deleted", true);

		jdbc.update("UPDATE users SET deleted_at = ? WHERE id = ?", NOW, user);

		assertEquals(new OwnerStanding(false, 1), owners.ownerStanding(user));
	}

	@Test
	void standing_staffRole_notEligible() {
		String user = account("ACTIVE", "standing.staff", true);
		grant(user, "SALE");

		assertFalse(owners.ownerStanding(user).eligible());
	}

	@Test
	void context_ageFromCreationDate() {
		String user = account("ACTIVE", "context.age", true);

		assertTrue(owners.accountContext(user).ageDays() >= 0);
		assertTrue(owners.accountContext(user).addressMasked().startsWith("c***@"));
	}

	@Test
	void notify_rollsBackWithCaller() {
		String user = account("ACTIVE", "notify.rollback", true);

		assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(
				status -> {
					owners.notifyOwner(user, OwnerNoticeKind.OWNER_CONFIRMED);
					throw new IllegalStateException("rolled back");
				}));

		assertEquals(0, notices(user));
	}

	@Test
	void notify_commitsWithCaller() {
		String user = account("ACTIVE", "notify.commit", true);

		new TransactionTemplate(transactionManager).executeWithoutResult(
				status -> owners.notifyOwner(user, OwnerNoticeKind.OWNER_WITHDRAWN));

		assertEquals(1, notices(user));
		assertEquals("OWNER_WITHDRAWN", jdbc.queryForObject("SELECT kind FROM auth_email_notifications WHERE user_id = ?",
				String.class, user));
	}

	/** SV-029: two parallel grants for one owner, both answer true and one row stands. */
	@Test
	void grant_twoParallel_noPrimaryKeyError() throws Exception {
		for (int round = 0; round < 5; round++) {
			String user = account("ACTIVE", "grant.parallel" + round, true);
			CountDownLatch start = new CountDownLatch(1);
			ExecutorService pool = Executors.newFixedThreadPool(2);
			try {
				List<Future<Boolean>> grants = List.of(pool.submit(() -> grantAfter(start, user)),
						pool.submit(() -> grantAfter(start, user)));
				start.countDown();

				assertTrue(grants.get(0).get(30, TimeUnit.SECONDS));
				assertTrue(grants.get(1).get(30, TimeUnit.SECONDS));
			} finally {
				pool.shutdownNow();
			}
			assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id"
					+ " WHERE ur.user_id = ? AND r.name = 'OWNER'", Integer.class, user));
		}
	}

	private boolean grantAfter(CountDownLatch start, String user) throws InterruptedException {
		start.await();
		return new TransactionTemplate(transactionManager).execute(status -> owners.ensureOwnerRole(user, ACTOR));
	}

	private int notices(String user) {
		return jdbc.queryForObject("SELECT count(*) FROM auth_email_notifications WHERE user_id = ?", Integer.class, user);
	}

	private static String mailSuffix(String user) {
		return "." + user.substring(user.length() - 6).toLowerCase() + "@example.com";
	}

	private void grant(String user, String role) {
		jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?", user, role);
	}

	private String account(String status, String localPart, boolean verified) {
		String id = String.format("01OWNERPG%017d", SEQUENCE.incrementAndGet());
		String address = localPart + mailSuffix(id);
		jdbc.update("INSERT INTO users (id, full_name, username, email, email_key, email_verified_at, password,"
				+ " kyc_status, user_status, created_date, last_update) VALUES (?, 'Owner test', ?, ?, ?, ?, 'hash',"
				+ " 'UNVERIFIED', ?, ?, ?)", id, id.substring(10), address, address, verified ? NOW : null, status, NOW, NOW);
		grant(id, "CUSTOMER");
		return id;
	}
}
