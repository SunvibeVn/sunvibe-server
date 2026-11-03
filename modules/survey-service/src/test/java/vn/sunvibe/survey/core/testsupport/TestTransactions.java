package vn.sunvibe.survey.core.testsupport;

import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * A transaction manager without a resource that records begin, commit and rollback, with the propagation and isolation
 * of each begin; synchronizations run as with a real one, so after-completion writes happen at commit or rollback.
 */
public final class TestTransactions extends AbstractPlatformTransactionManager {

    public final List<String> calls = new ArrayList<>();

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        String propagation = definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
                ? "REQUIRES_NEW" : "REQUIRED";
        String isolation = definition.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED
                ? ":READ_COMMITTED" : definition.getIsolationLevel() == TransactionDefinition.ISOLATION_DEFAULT
                        ? "" : ":" + definition.getIsolationLevel();
        calls.add("begin:" + propagation + isolation);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        calls.add("commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        calls.add("rollback");
    }
}
