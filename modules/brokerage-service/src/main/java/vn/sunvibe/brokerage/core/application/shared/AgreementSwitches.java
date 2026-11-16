package vn.sunvibe.brokerage.core.application.shared;

/**
 * The agreement switches the commands read at each request (E05-S4 spec S4-D8). A port, so a test varies it at run time.
 */
public interface AgreementSwitches {

    /**
     * brokerage-agreement BR-19: sending a version to its owner is open on this server. Off until the owner closes the
     * legal evidence gap SV-180 for the environment; while off the send answers 409 AGREEMENT_SENDING_NOT_AVAILABLE.
     */
    boolean ownerSendingEnabled();
}
