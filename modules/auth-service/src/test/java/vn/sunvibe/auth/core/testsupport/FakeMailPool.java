package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.application.usecase.external.email.AfterCommitMailPort;

import java.util.ArrayList;
import java.util.List;

/** Runs a mail at once, or keeps it while {@link #holding}, as a commit that has not happened yet would. */
public class FakeMailPool implements AfterCommitMailPort {

    public boolean holding;
    public boolean queueFull;
    public final List<Runnable> held = new ArrayList<>();

    @Override
    public void dispatch(Runnable send, Runnable onQueueFull) {
        if (queueFull) {
            onQueueFull.run();
        } else if (holding) {
            held.add(send);
        } else {
            send.run();
        }
    }
}
