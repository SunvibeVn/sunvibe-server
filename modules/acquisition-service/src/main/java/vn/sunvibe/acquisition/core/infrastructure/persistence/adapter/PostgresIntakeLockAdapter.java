package vn.sunvibe.acquisition.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.sunvibe.acquisition.core.application.shared.lock.IntakeLockPort;

/**
 * PostgreSQL implementation of {@link IntakeLockPort}: transaction-level advisory locks on a hash of the text, so
 * the lock is released by the commit or rollback of the caller. Requires an active transaction: without one the
 * database would release the lock at once and protect nothing, so it is refused with an exception.
 */
@Component
@RequiredArgsConstructor
public class PostgresIntakeLockAdapter implements IntakeLockPort {

    private static final String LOCK_SQL = "select pg_advisory_xact_lock(hashtext(?))";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void lockIdempotencyKey(String key) {
        lock("idem:" + key);
    }

    @Override
    public void lockPhone(String phoneNormalized) {
        lock("phone:" + phoneNormalized);
    }

    private void lock(String text) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("An intake lock needs an active transaction.");
        }
        // The function returns void, so there is no value to read
        jdbcTemplate.query(LOCK_SQL, (RowCallbackHandler) resultSet -> { }, text);
    }
}
