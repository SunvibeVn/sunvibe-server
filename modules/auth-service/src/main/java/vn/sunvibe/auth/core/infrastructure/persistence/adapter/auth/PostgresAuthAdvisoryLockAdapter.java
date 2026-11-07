package vn.sunvibe.auth.core.infrastructure.persistence.adapter.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.sunvibe.auth.core.application.shared.external.lock.AuthAdvisoryLockPort;

/** {@link AuthAdvisoryLockPort} over PostgreSQL transaction-level advisory locks. */
@Component
@RequiredArgsConstructor
public class PostgresAuthAdvisoryLockAdapter implements AuthAdvisoryLockPort {

    private static final String TRY_LOCK_SQL = "select pg_try_advisory_xact_lock(hashtextextended(?, 0))";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean tryLock(String key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("An advisory lock needs an active transaction.");
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(TRY_LOCK_SQL, Boolean.class, key));
    }
}
