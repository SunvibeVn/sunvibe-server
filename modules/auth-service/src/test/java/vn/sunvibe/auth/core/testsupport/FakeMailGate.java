package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.application.usecase.external.email.MailSendGatePort;

/** The mail gate as a test wants it, and what was asked of it. */
public class FakeMailGate implements MailSendGatePort {

    public boolean full;
    public boolean overBudget;
    public int entered;
    public int left;
    public int charged;

    @Override
    public boolean tryEnter() {
        if (!full) {
            entered++;
        }
        return !full;
    }

    @Override
    public boolean tryCharge() {
        if (!overBudget) {
            charged++;
        }
        return !overBudget;
    }

    @Override
    public void leave() {
        left++;
    }
}
