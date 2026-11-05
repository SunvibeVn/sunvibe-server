package vn.sunvibe.auth.core.application.usecase.external.email;

/**
 * The limit on auth mail (auth-email-access spec 5.6, PD-13). A send use case enters before its transaction, so at most
 * a few requests hold a connection through SMTP, and charges right before the real send, so a throttled request costs
 * no budget. Neither call ever waits. Every refusal is logged as {@code auth.mail.refused}.
 */
public interface MailSendGatePort {

    /** Takes a place among the concurrent sends. A true answer must be followed by {@link #leave()}. */
    boolean tryEnter();

    /** Takes one mail from the budget of this hour. */
    boolean tryCharge();

    /** Gives the place back. */
    void leave();
}
