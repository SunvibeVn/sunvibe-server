package vn.sunvibe.brokerage.core.application.query;

import vn.sunvibe.brokerage.core.application.usecase.AdvertisingRightView;
import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationDecision;
import vn.sunvibe.brokerage.core.domain.model.agreement.AdvertisingScope;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.IntroducedBySunvibe;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.StopAction;
import vn.sunvibe.brokerage.core.domain.model.agreement.SuspensionReasonType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;
import vn.sunvibe.brokerage.core.domain.model.agreement.WithdrawalChannel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The detail of a brokerage agreement for staff who may see its basis lead (E05-S2 spec 4; E05-S3 spec S3-D14). It holds
 * no owner, intake, property, creator or drafter id (brokerage-agreement BR-17: an answer carries only what the reader
 * needs).
 *
 * @param unitCodeOrNull       the unit's code as read from the basis at creation; null when the basis had none
 * @param basisLeadId          the basis lead of the latest version
 * @param versions             by version number
 * @param dataVersion          the agreement's row version: the data version a change sends back (BR-5)
 * @param viewerIsAssignedSale      whether the viewer is the one who drafts on the basis lead (BR-16); a hint, the
 *                                  commands decide
 * @param advertising               whether Sunvibe may advertise the unit now under this agreement, computed at the read
 *                                  (BR-12, US-4 AC-2; E05-S6 spec S6-D8)
 * @param viewerMayDecideActivation the viewer holds a manager role and is independent of the version awaiting
 *                                  activation (BR-11, BR-16; spec S6-D2); a hint, the commands decide
 * @param activationStatementOrNull the activation statement of the version awaiting activation now; null otherwise
 * @param endedAtOrNull             when the agreement ended (E05-S8 spec S8-D11); null while it has not
 * @param terminationOrNull         what staff recorded when they terminated it; null unless TERMINATED; private (BR-17)
 * @param suspensions               its suspension records, newest first, at most 20; private reasons (BR-17)
 * @param moreSuspensions           older records exist; their events stay in the paged history
 * @param viewerStopActions         the stops this viewer may take on the effective state, in the order of
 *                                  {@link StopAction}; a hint from AgreementStopPolicy, the commands decide
 */
public record AgreementDetailView(String agreementId, AgreementStatus status, TransactionType transactionType,
                                  String unitId, String unitCodeOrNull, String basisLeadId, Instant createdAt,
                                  List<VersionView> versions, long dataVersion, boolean viewerIsAssignedSale,
                                  AdvertisingRightView advertising, boolean viewerMayDecideActivation,
                                  ActivationStatementView activationStatementOrNull, Instant endedAtOrNull,
                                  TerminationView terminationOrNull, List<SuspensionView> suspensions,
                                  boolean moreSuspensions, List<StopAction> viewerStopActions) {

    public AgreementDetailView {
        versions = List.copyOf(versions);
        suspensions = List.copyOf(suspensions);
        viewerStopActions = List.copyOf(viewerStopActions);
    }

    /**
     * The termination as staff recorded it (brokerage-agreement BR-14; E05-S8 spec S8-D5, S8-D11). Private (BR-17): no
     * value of it in a text.
     */
    public record TerminationView(TerminationType type, LocalDate reportedOnOrNull, WithdrawalChannel channelOrNull,
                                  IntroducedBySunvibe introducedOrNull, String noteOrNull, Instant terminatedAt) {

        @Override
        public String toString() {
            return "TerminationView[" + type + "]";
        }
    }

    /**
     * One suspension of the agreement (brokerage-agreement BR-15; E05-S8 spec S8-D6, S8-D11): no user id. Private
     * (BR-17): no note or reason in a text.
     */
    public record SuspensionView(SuspensionReasonType reasonType, String noteOrNull, Instant suspendedAt,
                                 Instant releasedAtOrNull, String releaseReasonOrNull) {

        @Override
        public String toString() {
            return "SuspensionView[" + reasonType + ", " + suspendedAt + "]";
        }
    }

    /** The statement a manager makes to activate a version (E05-S6 spec S6-D3): its version and its text. */
    public record ActivationStatementView(String version, String text) {
    }

    /**
     * One version: its number, state, calendar dates and content (brokerage-agreement BR-7 to BR-9), when it was sent to
     * the owner (E05-S4 spec S4-D10), the owner's answer (E05-S5 spec S5-D13), its activation deadline and a manager's
     * decision (E05-S6 spec S6-D21). The staff detail and the owner's read share it; the owner's read never fills the
     * activation decision, which is staff data.
     *
     * @param status                     the effective state: a version past its deadline reads LAPSED (spec S5-D8)
     * @param sentAtOrNull               null for a version never sent
     * @param answerByOrNull             the owner's deadline, 7 days after the sending; null for a version never sent
     * @param ownerAnsweredAtOrNull      when the owner confirmed or declined it; null otherwise
     * @param ownerRejectionReasonOrNull the owner's private reason for declining (brokerage-agreement BR-17)
     * @param activateByOrNull           14 days after the owner's confirmation, whatever the status now; null when the
     *                                   owner did not confirm it
     * @param activationOrNull           a manager's activation decision; private (BR-17), staff only
     */
    public record VersionView(int versionNo, VersionStatus status, LocalDate startDate, LocalDate endDate,
                              Instant createdAt, AdvertisingScope advertisingScope, PhotoConsent photoConsent,
                              VersionTerms terms, BrokerageFee fee, Instant sentAtOrNull, Instant answerByOrNull,
                              Instant ownerAnsweredAtOrNull, String ownerRejectionReasonOrNull,
                              Instant activateByOrNull, ActivationDecision activationOrNull) {

        @Override
        public String toString() {
            return "VersionView[" + versionNo + ", " + status + "]";
        }
    }
}
