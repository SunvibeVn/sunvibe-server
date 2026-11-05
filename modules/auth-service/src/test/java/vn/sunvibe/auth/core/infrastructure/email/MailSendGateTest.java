package vn.sunvibe.auth.core.infrastructure.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access spec 5.6, R-7: four sends at a time, a budget per hour, and a refusal that never waits. */
@ExtendWith(OutputCaptureExtension.class)
class MailSendGateTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-05T10:15:00Z"));

    @Test
    void gate_bulkheadFull_refusesAtOnce(CapturedOutput output) {
        MailSendGate gate = new MailSendGate(300, now::get);
        for (int i = 0; i < 4; i++) {
            assertTrue(gate.tryEnter());
        }

        assertFalse(gate.tryEnter());

        assertTrue(output.getOut().contains("auth.mail.refused reason=bulkhead"));
    }

    @Test
    void gate_leave_opensAPlace() {
        MailSendGate gate = new MailSendGate(300, now::get);
        for (int i = 0; i < 4; i++) {
            gate.tryEnter();
        }

        gate.leave();

        assertTrue(gate.tryEnter());
    }

    @Test
    void gate_overBudget_refuses(CapturedOutput output) {
        MailSendGate gate = new MailSendGate(2, now::get);
        assertTrue(gate.tryCharge());
        assertTrue(gate.tryCharge());

        assertFalse(gate.tryCharge());

        assertTrue(output.getOut().contains("auth.mail.refused reason=budget"));
    }

    @Test
    void gate_nextHour_budgetIsBack() {
        MailSendGate gate = new MailSendGate(1, now::get);
        gate.tryCharge();
        assertFalse(gate.tryCharge());

        now.set(now.get().plus(Duration.ofHours(1)));

        assertTrue(gate.tryCharge());
    }

    /** Entering charges nothing: a throttled request costs no budget. */
    @Test
    void gate_enterWithoutCharge_budgetUntouched() {
        MailSendGate gate = new MailSendGate(1, now::get);
        assertTrue(gate.tryEnter());
        gate.leave();

        assertTrue(gate.tryCharge());
    }
}
