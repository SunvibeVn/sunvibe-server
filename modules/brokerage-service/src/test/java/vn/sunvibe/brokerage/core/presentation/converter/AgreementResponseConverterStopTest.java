package vn.sunvibe.brokerage.core.presentation.converter;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView.SuspensionView;
import vn.sunvibe.brokerage.core.application.query.AgreementDetailView.TerminationView;
import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.IntroducedBySunvibe;
import vn.sunvibe.brokerage.core.domain.model.agreement.StopAction;
import vn.sunvibe.brokerage.core.domain.model.agreement.SuspensionReasonType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementDetailResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementSuspensionResponse;
import vn.sunvibe.brokerage.core.presentation.dto.response.BrokerageAgreementTerminationResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The detail's stop parts as codes and plain values (brokerage-agreement E05-S8 spec S8-D11, section 4): the end, the
 * termination, the suspension records without a user id, whether older ones exist and the viewer's stop actions.
 */
class AgreementResponseConverterStopTest {

    private static final String AGREEMENT = "01JAGREEMENT00000000000001";
    private static final Instant CREATED = Instant.parse("2026-11-20T03:15:00Z");
    private static final Instant ENDED = Instant.parse("2026-12-02T03:00:00Z");

    private final AgreementResponseConverter converter = new AgreementResponseConverter();

    @Test
    void toDetail_stopParts_mapped() {
        AgreementDetailView view = view(AgreementStatus.TERMINATED, ENDED, new TerminationView(TerminationType.UNIT_LET,
                LocalDate.parse("2026-12-01"), null, IntroducedBySunvibe.NO, "Let to a friend.", ENDED),
                List.of(new SuspensionView(SuspensionReasonType.DISPUTE, "A dispute.", CREATED.plusSeconds(60),
                        CREATED.plusSeconds(120), "Settled.")), true,
                List.of(StopAction.TERMINATE_OWNER_WITHDREW, StopAction.TERMINATE_SUNVIBE));

        BrokerageAgreementDetailResponse detail = converter.toDetail(view);

        assertEquals(ENDED, detail.endedAt());
        assertEquals(new BrokerageAgreementTerminationResponse("UNIT_LET", LocalDate.parse("2026-12-01"), null, "NO",
                "Let to a friend.", ENDED), detail.termination());
        assertEquals(List.of(new BrokerageAgreementSuspensionResponse("DISPUTE", "A dispute.", CREATED.plusSeconds(60),
                CREATED.plusSeconds(120), "Settled.")), detail.suspensions());
        assertTrue(detail.moreSuspensions());
        assertEquals(List.of("TERMINATE_OWNER_WITHDREW", "TERMINATE_SUNVIBE"), detail.viewerStopActions());
    }

    @Test
    void toDetail_noStop_nullsEmptyListsAndFalse() {
        BrokerageAgreementDetailResponse detail = converter.toDetail(view(AgreementStatus.DRAFTING, null, null,
                List.of(), false, List.of()));

        assertNull(detail.endedAt());
        assertNull(detail.termination());
        assertEquals(List.of(), detail.suspensions());
        assertFalse(detail.moreSuspensions());
        assertEquals(List.of(), detail.viewerStopActions());
    }

    private static AgreementDetailView view(AgreementStatus status, Instant endedAtOrNull,
                                            TerminationView terminationOrNull, List<SuspensionView> suspensions,
                                            boolean more, List<StopAction> actions) {
        return new AgreementDetailView(AGREEMENT, status, TransactionType.LONG_TERM_RENT, "U1", "P101", "L1", CREATED,
                List.of(), 3L, false, new AdvertisingRightView("U1", AGREEMENT, false, false, List.of("ENDED"), null,
                null, false, false, null, null, null, null), false, null, endedAtOrNull, terminationOrNull,
                suspensions, more, actions);
    }
}
