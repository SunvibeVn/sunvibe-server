package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.external.account.OwnerNoticePort;

import java.util.ArrayList;
import java.util.List;

/** Records the user id of each agreement notice, in order. */
public final class RecordingOwnerNoticePort implements OwnerNoticePort {

    private final List<String> agreementPending = new ArrayList<>();

    @Override
    public void agreementPending(String ownerUserId) {
        agreementPending.add(ownerUserId);
    }

    /** The user ids of the AGREEMENT_PENDING notices asked for so far. */
    public List<String> agreementPending() {
        return List.copyOf(agreementPending);
    }
}
