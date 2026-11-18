package vn.sunvibe.brokerage.core.testsupport;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * A transaction manager that runs the callback with no database, as auth's test double of that name does: a template
 * over it marks a transaction active while its callback runs, so a test can see what runs inside one and what outside.
 */
public class NoDatabaseTransactions extends AbstractPlatformTransactionManager {

    /** The transactions begun so far. */
    public int begun;

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        begun++;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
    }
}
