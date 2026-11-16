package vn.sunvibe.brokerage.core.testsupport;

import vn.sunvibe.brokerage.core.application.shared.AgreementSwitches;

/** The agreement switches fixed at construction: sending to owners on, unless built with {@link #off()}. */
public final class FixedAgreementSwitches implements AgreementSwitches {

    private final boolean ownerSendingEnabled;

    public FixedAgreementSwitches() {
        this(true);
    }

    private FixedAgreementSwitches(boolean ownerSendingEnabled) {
        this.ownerSendingEnabled = ownerSendingEnabled;
    }

    /** Sending to owners off. */
    public static FixedAgreementSwitches off() {
        return new FixedAgreementSwitches(false);
    }

    @Override
    public boolean ownerSendingEnabled() {
        return ownerSendingEnabled;
    }
}
