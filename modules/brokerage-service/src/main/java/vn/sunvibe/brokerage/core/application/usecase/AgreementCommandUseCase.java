package vn.sunvibe.brokerage.core.application.usecase;

import vn.sunvibe.brokerage.core.application.command.AgreementConfirmCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRejectCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementSendCommand;

/** Use Case - the commands of a brokerage agreement. */
public interface AgreementCommandUseCase {

    /**
     * The lead's assigned Sale drafts an agreement for one unit of the lead's intake basis (brokerage-agreement US-1
     * AC-1 to AC-4): DRAFTING with its version 1 DRAFT and one history row. In one transaction, the first failing step
     * answers: the transaction type and the period (400); the lead, locked and visible to the actor (404); the actor is
     * its current assignee with SALE or SALE_LEADER (403); the unit's eligibility on the lead's basis (409 with the
     * groups); no unfinished agreement of the unit and type (409).
     *
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException a refused step
     * @throws vn.sunvibe.brokerage.core.domain.exception.AgreementPeriodException        the period breaks BR-6
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException the lead is not visible
     * @throws vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException        a concurrent create took the
     *                                                                                    unit first
     */
    AgreementCreateResult create(AgreementCreateCommand command, StaffViewer actor);

    /**
     * The basis lead's assigned Sale replaces the content of the agreement's draft (brokerage-agreement US-1 AC-3, AC-5)
     * and one history row records it. In one transaction, the first failing step answers (E05-S3 spec 3.1): the
     * transaction type and the period (400); the agreement (404); its basis lead, locked and visible to the actor
     * (404); the actor is its current assignee with SALE or SALE_LEADER (403); the draft (409); the data version (409);
     * the unit's eligibility on the draft's own basis (409 with the groups). A refusal writes nothing.
     *
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException  a refused step
     * @throws vn.sunvibe.brokerage.core.domain.exception.AgreementPeriodException         the period breaks BR-6
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException  the agreement or its lead is
     *                                                                                     not visible
     */
    void editDraft(AgreementDraftCommand command, StaffViewer actor);

    /**
     * The basis lead's assigned Sale sends the agreement's draft to the owner (brokerage-agreement US-1 AC-5; E05-S4 spec
     * 3.2, S4-D6): the version AWAITING_OWNER with its sending time and its content locked, the agreement
     * AWAITING_CONFIRMATION, one history row, and one neutral notice to the owner's account. In one transaction, the
     * first failing step answers: the agreement (404); its basis lead, locked and visible to the actor (404); the actor
     * is its current assignee with SALE or SALE_LEADER (403); the draft (409 AGREEMENT_NOT_DRAFT); the data version (409
     * CONCURRENT_MODIFICATION); every part of the version present (409 AGREEMENT_VERSION_INCOMPLETE with the missing
     * parts); the unit's eligibility on the draft's own basis (409 with the groups); sending to owners open on this
     * server (409 AGREEMENT_SENDING_NOT_AVAILABLE). A refusal writes nothing.
     *
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException  a refused step
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException  the agreement or its lead is
     *                                                                                     not visible
     * @throws org.springframework.dao.ConcurrencyFailureException                         the agreement changed between
     *                                                                                     its read and the write
     */
    void sendDraft(AgreementSendCommand command, StaffViewer actor);

    /**
     * The owner confirms one or several versions sent to them with their current password (brokerage-agreement US-2
     * AC-2, AC-4, AC-5; E05-S5 spec S5-D4 to S5-D6): each version OWNER_CONFIRMED with its own answer and history row,
     * all or nothing. The first failing step answers: the caller is an eligible owner and every item names a version of
     * theirs that was sent (404, one answer for every case); the statement version is current (409); the password (400 on
     * currentPassword, 423 when locked), checked outside any transaction so its count stays; then in one transaction each
     * version still waits for the owner within its 7 days (409, an entry per failing item), the E05-S1 check on each
     * version's basis (409, entries per item), and the writes. A refusal writes nothing.
     *
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException  a refused step
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException  not the caller's sent version
     * @throws org.springframework.dao.ConcurrencyFailureException                         an agreement changed between
     *                                                                                     its read and the write
     */
    void confirmByOwner(AgreementConfirmCommand command, String ownerUserId);

    /**
     * The owner declines the version waiting for them, with an optional private reason (brokerage-agreement US-2 AC-3,
     * BR-17; E05-S5 spec S5-D7): the version OWNER_REJECTED, the agreement drafting again, one history row; no password.
     * In one transaction, the first failing step answers: the reason's rule (400 on reason); the caller is an eligible
     * owner and the version is theirs and was sent (404, one answer for every case); the version still waits for them
     * within its 7 days (409). A refusal writes nothing.
     *
     * @throws vn.sunvibe.brokerage.core.domain.exception.AgreementContentException       the reason breaks its rule
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException  a refused step
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException  not the caller's sent version
     */
    void rejectByOwner(AgreementRejectCommand command, String ownerUserId);
}
