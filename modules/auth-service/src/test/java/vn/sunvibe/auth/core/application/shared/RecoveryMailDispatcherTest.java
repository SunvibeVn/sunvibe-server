package vn.sunvibe.auth.core.application.shared;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.testsupport.FakeMailGate;
import vn.sunvibe.auth.core.testsupport.FakeMailPool;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access spec 5.6: the answer left already, so whatever goes wrong is an audit event without the address. */
class RecoveryMailDispatcherTest {

    private static final String USER_ID = "user-1";

    private final FakeMailPool pool = new FakeMailPool();
    private final FakeMailGate gate = new FakeMailGate();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final RecoveryMailDispatcher dispatcher = new RecoveryMailDispatcher(pool, gate, audit);
    private int sends;

    private void dispatch() {
        dispatcher.dispatch(AuditEventType.PASSWORD_RESET_REQUESTED, USER_ID, () -> sends++);
    }

    private void assertSendFailedAudited() {
        assertEquals(List.of(AuditEventType.PASSWORD_RESET_REQUESTED), audit.types());
        assertEquals(RecoveryMailDispatcher.SEND_FAILED, audit.last().details());
        assertEquals(USER_ID, audit.last().userId());
        assertNull(audit.last().subject(), "no address");
    }

    @Test
    void dispatch_sends_chargedOnce_leavesTheGate() {
        dispatch();

        assertEquals(1, sends);
        assertEquals(1, gate.charged);
        assertEquals(gate.entered, gate.left);
        assertTrue(audit.types().isEmpty());
    }

    @Test
    void dispatch_waitsForThePool() {
        pool.holding = true;

        dispatch();

        assertEquals(0, sends);
        assertEquals(0, gate.entered, "the gate is entered when the mail leaves");
        pool.held.getFirst().run();
        assertEquals(1, sends);
    }

    @Test
    void dispatch_gateFull_nothingSent_audited() {
        gate.full = true;

        dispatch();

        assertEquals(0, sends);
        assertEquals(0, gate.left, "no place was taken");
        assertSendFailedAudited();
    }

    @Test
    void dispatch_overBudget_nothingSent_audited() {
        gate.overBudget = true;

        dispatch();

        assertEquals(0, sends);
        assertEquals(gate.entered, gate.left);
        assertSendFailedAudited();
    }

    @Test
    void dispatch_sendFails_audited() {
        dispatcher.dispatch(AuditEventType.PASSWORD_RESET_REQUESTED, USER_ID, () -> {
            throw new SendingMailFailedException("down");
        });

        assertEquals(gate.entered, gate.left);
        assertSendFailedAudited();
    }

    @Test
    void dispatch_queueFull_audited() {
        pool.queueFull = true;

        dispatch();

        assertEquals(0, sends);
        assertSendFailedAudited();
    }
}
