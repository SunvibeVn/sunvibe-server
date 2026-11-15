package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerAccountPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The owner standing of each user (not eligible unless set); each read is recorded into the shared call list as
 * {@code "account <user>"}.
 */
public final class RecordingOwnerAccountPort implements OwnerAccountPort {

    private final List<String> calls;
    private final Map<String, Boolean> answers = new HashMap<>();

    public RecordingOwnerAccountPort(List<String> calls) {
        this.calls = calls;
    }

    public RecordingOwnerAccountPort answer(String userId, boolean eligible) {
        answers.put(userId, eligible);
        return this;
    }

    @Override
    public boolean eligibleOwner(String userId) {
        calls.add("account " + userId);
        return answers.getOrDefault(userId, false);
    }
}
