package vn.sunvibe.auth.core.infrastructure.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.sunvibe.auth.core.application.usecase.external.email.AfterCommitMailPort;

/** {@link AfterCommitMailPort} over a transaction synchronization and {@link AuthMailExecutor}. */
@Component
@RequiredArgsConstructor
public class AfterCommitMailAdapter implements AfterCommitMailPort {

    private final AuthMailExecutor executor;

    @Override
    public void dispatch(Runnable send, Runnable onQueueFull) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(send, onQueueFull);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(send, onQueueFull);
            }
        });
    }

    private void submit(Runnable send, Runnable onQueueFull) {
        if (!executor.submit(send)) {
            onQueueFull.run();
        }
    }
}
