package vn.sunvibe.brokerage.core.testsupport;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.sunvibe.brokerage.core.application.shared.external.account.AccountPasswordPort;
import vn.sunvibe.brokerage.core.application.shared.external.account.PasswordCheck;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers a set outcome (OK unless set) and records each user id checked and whether a transaction was active at the
 * call; never the password.
 */
public final class RecordingAccountPasswordPort implements AccountPasswordPort {

    /** One check: the account, and whether the caller held a transaction then. */
    public record Check(String userId, boolean inTransaction) {
    }

    private final List<Check> checks = new ArrayList<>();
    private PasswordCheck.Outcome outcome = PasswordCheck.Outcome.OK;

    public RecordingAccountPasswordPort answer(PasswordCheck.Outcome answer) {
        this.outcome = answer;
        return this;
    }

    @Override
    public PasswordCheck check(String userId, String password) {
        checks.add(new Check(userId, TransactionSynchronizationManager.isActualTransactionActive()));
        return new PasswordCheck(outcome);
    }

    public List<Check> checks() {
        return List.copyOf(checks);
    }
}
