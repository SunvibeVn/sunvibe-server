package vn.sunvibe.auth.core.testsupport;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Runs a transaction template over the fakes: nothing rolls back, and it counts how many transactions began. */
public class NoDatabaseTransactions extends AbstractPlatformTransactionManager {

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
