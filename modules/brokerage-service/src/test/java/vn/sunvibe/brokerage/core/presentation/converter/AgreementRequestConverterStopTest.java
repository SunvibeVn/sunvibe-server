package vn.sunvibe.brokerage.core.presentation.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import vn.sunvibe.brokerage.core.application.command.AgreementCancelCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementReleaseSuspensionCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementSuspendCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementTerminateCommand;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Entry;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException.Reason;
import vn.sunvibe.brokerage.core.domain.model.agreement.IntroducedBySunvibe;
import vn.sunvibe.brokerage.core.domain.model.agreement.SuspensionReasonType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;
import vn.sunvibe.brokerage.core.domain.model.agreement.WithdrawalChannel;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementCancellationRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementSuspensionReleaseRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementSuspensionRequest;
import vn.sunvibe.brokerage.core.presentation.dto.request.BrokerageAgreementTerminationRequest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The stop requests to their commands (brokerage-agreement E05-S8 spec section 3, 4, S8-D5; plan E05S8-P5, P17): the
 * closed codes refused with their allowed values, the termination's fields by type (a field of another type, then a
 * missing required one; null counts as absent), the texts passed as sent for the domain to trim and check.
 */
class AgreementRequestConverterStopTest {

    private static final String AGREEMENT = "01JAGREEMENT00000000000001";
    private static final LocalDate DAY = LocalDate.parse("2026-12-01");
    private static final List<String> REASONS = List.of("COMPLAINT", "SUSPECTED_FRAUD", "DISPUTE", "NEEDS_CHECK");
    private static final List<String> TYPES = List.of("OWNER_WITHDREW", "UNIT_LET", "SUNVIBE_TERMINATED");
    private static final List<String> CHANNELS = List.of("PHONE_CALL", "IN_PERSON", "MESSAGE", "EMAIL", "OTHER");
    private static final List<String> INTRODUCED = List.of("YES", "NO", "UNKNOWN");

    private final AgreementRequestConverter converter = new AgreementRequestConverter();

    @Test
    void toSuspendCommand_unknownOrMissingReason_allowedValues() {
        for (String bad : new String[]{"complaint", "OTHER", "", null}) {
            AgreementRefusedException refused = refused(() -> converter.toSuspendCommand(AGREEMENT,
                    new BrokerageAgreementSuspensionRequest(bad, null)));

            assertEquals(Reason.VALIDATION_FAILED, refused.reason(), String.valueOf(bad));
            assertEquals(List.of(new Entry("reasonType", REASONS)), refused.entries(), String.valueOf(bad));
        }
        assertEquals(new AgreementSuspendCommand(AGREEMENT, SuspensionReasonType.SUSPECTED_FRAUD, "  A note. "),
                converter.toSuspendCommand(AGREEMENT, new BrokerageAgreementSuspensionRequest("SUSPECTED_FRAUD",
                        "  A note. ")));
    }

    @Test
    void toTerminateCommand_unknownOrMissingType_allowedValues() {
        for (String bad : new String[]{"WITHDREW", "", null}) {
            AgreementRefusedException refused = refused(() -> converter.toTerminateCommand(AGREEMENT,
                    new BrokerageAgreementTerminationRequest(bad, DAY, "EMAIL", null, null)));

            assertEquals(List.of(new Entry("type", TYPES)), refused.entries(), String.valueOf(bad));
        }
    }

    /** S8-D5: each type with one field of another type answers that field's entry. */
    @Test
    void toTerminateCommand_fieldOfAnotherType_entryNamesIt() {
        assertEquals(List.of(new Entry("introducedBySunvibe", null)), refused(() -> converter.toTerminateCommand(
                AGREEMENT, new BrokerageAgreementTerminationRequest("OWNER_WITHDREW", DAY, "EMAIL", "YES", null)))
                .entries());
        assertEquals(List.of(new Entry("channel", null)), refused(() -> converter.toTerminateCommand(AGREEMENT,
                new BrokerageAgreementTerminationRequest("UNIT_LET", DAY, "EMAIL", "YES", null))).entries());
        assertEquals(List.of(new Entry("reportedOn", null)), refused(() -> converter.toTerminateCommand(AGREEMENT,
                new BrokerageAgreementTerminationRequest("SUNVIBE_TERMINATED", DAY, null, null, "A reason."))).entries());
    }

    @Test
    void toTerminateCommand_missingRequiredField_entryNamesIt() {
        assertEquals(List.of(new Entry("reportedOn", null)), refused(() -> converter.toTerminateCommand(AGREEMENT,
                new BrokerageAgreementTerminationRequest("OWNER_WITHDREW", null, "EMAIL", null, null))).entries());
        assertEquals(List.of(new Entry("channel", null)), refused(() -> converter.toTerminateCommand(AGREEMENT,
                new BrokerageAgreementTerminationRequest("OWNER_WITHDREW", DAY, null, null, null))).entries());
        assertEquals(List.of(new Entry("reportedOn", null)), refused(() -> converter.toTerminateCommand(AGREEMENT,
                new BrokerageAgreementTerminationRequest("UNIT_LET", null, null, "NO", null))).entries());
        assertEquals(List.of(new Entry("introducedBySunvibe", null)), refused(() -> converter.toTerminateCommand(
                AGREEMENT, new BrokerageAgreementTerminationRequest("UNIT_LET", DAY, null, null, null))).entries());
        assertEquals(List.of(new Entry("note", null)), refused(() -> converter.toTerminateCommand(AGREEMENT,
                new BrokerageAgreementTerminationRequest("SUNVIBE_TERMINATED", null, null, null, null))).entries());
    }

    @Test
    void toTerminateCommand_unknownChannelOrIntroduced_allowedValues() {
        assertEquals(List.of(new Entry("channel", CHANNELS)), refused(() -> converter.toTerminateCommand(AGREEMENT,
                new BrokerageAgreementTerminationRequest("OWNER_WITHDREW", DAY, "LETTER", null, null))).entries());
        assertEquals(List.of(new Entry("introducedBySunvibe", INTRODUCED)), refused(() -> converter.toTerminateCommand(
                AGREEMENT, new BrokerageAgreementTerminationRequest("UNIT_LET", DAY, null, "MAYBE", null))).entries());
    }

    @Test
    void toTerminateCommand_eachType_itsCommand() {
        assertEquals(new AgreementTerminateCommand(AGREEMENT, TerminationType.OWNER_WITHDREW, DAY,
                        WithdrawalChannel.IN_PERSON, null, " Note "),
                converter.toTerminateCommand(AGREEMENT, new BrokerageAgreementTerminationRequest("OWNER_WITHDREW", DAY,
                        "IN_PERSON", null, " Note ")));
        assertEquals(new AgreementTerminateCommand(AGREEMENT, TerminationType.UNIT_LET, DAY, null,
                        IntroducedBySunvibe.UNKNOWN, null),
                converter.toTerminateCommand(AGREEMENT, new BrokerageAgreementTerminationRequest("UNIT_LET", DAY, null,
                        "UNKNOWN", null)));
        assertEquals(new AgreementTerminateCommand(AGREEMENT, TerminationType.SUNVIBE_TERMINATED, null, null, null,
                        "A dispute."),
                converter.toTerminateCommand(AGREEMENT, new BrokerageAgreementTerminationRequest("SUNVIBE_TERMINATED",
                        null, null, null, "A dispute.")));
    }

    @Test
    void toReleaseSuspensionCommand_values() {
        assertEquals(new AgreementReleaseSuspensionCommand(AGREEMENT, 7L, " Checked. "),
                converter.toReleaseSuspensionCommand(AGREEMENT, new BrokerageAgreementSuspensionReleaseRequest(7L,
                        " Checked. ")));
    }

    @Test
    void toCancelCommand_values() {
        assertEquals(new AgreementCancelCommand(AGREEMENT, 4L), converter.toCancelCommand(AGREEMENT,
                new BrokerageAgreementCancellationRequest(4L)));
    }

    /** brokerage-agreement BR-17: the requests' texts stay out of every log line. */
    @Test
    void stopRequests_toString_withoutTexts() {
        String suspension = new BrokerageAgreementSuspensionRequest("COMPLAINT", "Secret note").toString();
        String release = new BrokerageAgreementSuspensionReleaseRequest(3L, "Secret reason").toString();
        String termination = new BrokerageAgreementTerminationRequest("OWNER_WITHDREW", DAY, "PHONE_CALL", null,
                "Secret note").toString();

        assertFalse(suspension.contains("Secret"), suspension);
        assertFalse(release.contains("Secret"), release);
        assertFalse(termination.contains("Secret"), termination);
        assertFalse(termination.contains("2026-12-01"), termination);
        assertFalse(termination.contains("PHONE_CALL"), termination);
    }

    private static AgreementRefusedException refused(Executable call) {
        return assertThrows(AgreementRefusedException.class, call);
    }
}
