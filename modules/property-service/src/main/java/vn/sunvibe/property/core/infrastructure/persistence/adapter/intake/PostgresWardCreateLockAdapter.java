package vn.sunvibe.property.core.infrastructure.persistence.adapter.intake;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.sunvibe.property.core.application.shared.lock.WardCreateLockPort;

/**
 * PostgreSQL implementation of {@link WardCreateLockPort}: a transaction-level advisory lock on (namespace, hash of the ward), so
 * the commit or rollback of the caller releases it. Without a transaction the database would release it at once and protect
 * nothing, so that is refused.
 */
@Component
@RequiredArgsConstructor
public class PostgresWardCreateLockAdapter implements WardCreateLockPort {

    /** Keeps these locks apart from every other advisory lock of the database. */
    private static final int NAMESPACE = 2021;
    private static final String TRY_LOCK_SQL = "select pg_try_advisory_xact_lock(?, hashtext(?))";
    private static final String BOUND_WAIT_SQL = "set local lock_timeout = '3s'";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean tryLock(String wardCode) {
        requireTransaction();
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, NAMESPACE, wardCode));
    }

    @Override
    public void boundOwnerRowWait() {
        requireTransaction();
        jdbcTemplate.execute(BOUND_WAIT_SQL);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A ward lock needs an active transaction.");
        }
    }
}
