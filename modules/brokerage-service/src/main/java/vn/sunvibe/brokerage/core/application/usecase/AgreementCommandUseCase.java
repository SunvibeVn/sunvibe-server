package vn.sunvibe.brokerage.core.application.usecase;

import vn.sunvibe.brokerage.core.application.command.AgreementActivateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementConfirmCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementCreateCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementOpenDraftCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRefuseActivationCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementRejectCommand;
import vn.sunvibe.brokerage.core.application.command.AgreementSendCommand;

/** Use Case - the commands of a brokerage agreement. */
public interface AgreementCommandUseCase {

    /** The most due lapses one run of {@link #storeDueLapses()} stores; the next run takes the rest (spec S5-D8). */
    int MAX_LAPSES_PER_RUN = 100;

    /** The most due starts one run of {@link #storeDueStarts()} stores; the next run takes the rest (spec S6-D6). */
    int MAX_STARTS_PER_RUN = 100;

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

    /**
     * The basis lead's assigned Sale opens the next draft version after the owner's rejection or a lapse
     * (brokerage-agreement US-2 AC-3, E-6; E05-S5 spec S5-D10): version n + 1, a DRAFT copy of the latest version's
     * content, and one history row. In one transaction, the first failing step answers: the agreement (404); its basis
     * lead, locked and visible to the actor (404); the actor is its current assignee with SALE or SALE_LEADER (403); the
     * agreement, with a due lapse applied, is DRAFTING with no pending version (409 AGREEMENT_NEW_DRAFT_NOT_ALLOWED); the
     * data version (409 CONCURRENT_MODIFICATION); the E05-S1 check on the latest version's basis (409). A lapse due and
     * not stored yet is stored first, with its VERSION_LAPSED row at its deadline. A refusal writes nothing.
     *
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException  a refused step
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException  the agreement or its lead is
     *                                                                                     not visible
     * @throws org.springframework.dao.ConcurrencyFailureException                         the agreement changed between
     *                                                                                     its read and the write
     */
    void openDraft(AgreementOpenDraftCommand command, StaffViewer actor);

    /**
     * An independent manager activates the version the owner confirmed (brokerage-agreement US-3 AC-1, AC-2, AC-4, AC-5;
     * BR-6, BR-11, BR-16; E05-S6 spec S6-D1): the version ACTIVATED (its start ahead) or EFFECTIVE with its activation
     * record, the agreement ACTIVE, one history row, the outbox events of E05 (now, at the start when ahead, at the end,
     * each within the outbox's horizon) and one neutral notice to the owner. In one transaction, the first failing step
     * answers: the signed document's values (400 on the field); the role is the controller's method rule (403); the
     * agreement (404); its basis lead, locked and visible to the actor (404); the actor is neither the lead's current
     * assignee nor an author of versions 1 to n (403 AGREEMENT_ACTIVATOR_NOT_INDEPENDENT); the agreement, with its due
     * transitions applied, awaits confirmation with that version confirmed and within its 14 days (409
     * AGREEMENT_NOT_AWAITING_ACTIVATION); the data version (409 CONCURRENT_MODIFICATION); the statement version (409
     * AGREEMENT_STATEMENT_OUTDATED); the end of the version's end date lies ahead (409 AGREEMENT_VERSION_PERIOD_ENDED);
     * the E05-S1 check on the version's basis (409 with the groups). A refusal writes nothing.
     *
     * @throws vn.sunvibe.brokerage.core.domain.exception.AgreementContentException       a document value breaks its rule
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException  a refused step
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException  the agreement or its lead is
     *                                                                                     not visible
     * @throws org.springframework.dao.ConcurrencyFailureException                         the agreement changed between
     *                                                                                     its read and the write
     */
    void activate(AgreementActivateCommand command, StaffViewer actor);

    /**
     * An independent manager refuses to activate the version the owner confirmed, with a type and an optional private
     * note (brokerage-agreement US-3 AC-3, E-7, BR-17; E05-S6 spec S6-D4): the version ACTIVATION_REFUSED with its
     * refusal record, the agreement drafting again, one history row; no event and no notice. In one transaction, the
     * first failing step answers: the note's rule (400 on note); the role is the controller's method rule (403); the
     * agreement (404); its basis lead, locked and visible to the actor (404); the actor is independent (403
     * AGREEMENT_ACTIVATOR_NOT_INDEPENDENT); the version awaits activation within its 14 days (409
     * AGREEMENT_NOT_AWAITING_ACTIVATION); the data version (409 CONCURRENT_MODIFICATION). A refusal writes nothing.
     *
     * @throws vn.sunvibe.brokerage.core.domain.exception.AgreementContentException       the note breaks its rule
     * @throws vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException  a refused step
     * @throws vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException  the agreement or its lead is
     *                                                                                     not visible
     * @throws org.springframework.dao.ConcurrencyFailureException                         the agreement changed between
     *                                                                                     its read and the write
     */
    void refuseActivation(AgreementRefuseActivationCommand command, StaffViewer actor);

    /**
     * Stores the lapses that are due, in two branches of one run (brokerage-agreement BR-10, BR-11, US-2 AC-3, US-3
     * AC-4; E05-S5 spec S5-D8, E05-S6 spec S6-D5): at most {@link #MAX_LAPSES_PER_RUN} agreements whose version waits
     * for the owner and was sent 7 days ago or more, oldest sending first; then at most as many whose version the owner
     * confirmed 14 days ago or more without an activation, oldest confirmation first. Each in a transaction of its own:
     * the version LAPSED and the agreement drafting again through the guarded update, then one VERSION_LAPSED history
     * row at the deadline of the lapse that fell due, without an actor. An agreement changed since the selection (a lost
     * row-version race) is skipped silently; any other runtime failure of one agreement rolls it back and is logged as
     * BROKERAGE_TRANSITION_FAILED with its id and the exception's class name, and the run goes on.
     * <p>
     * Its only caller is AgreementLapseJob; no controller reaches it, so it takes no viewer.
     *
     * @return the number of lapses stored
     */
    int storeDueLapses();

    /**
     * Stores the starts that are due (brokerage-agreement BR-5, BR-6, US-3 AC-5; E05-S6 spec S6-D6): at most
     * {@link #MAX_STARTS_PER_RUN} agreements whose ACTIVATED version holds its activation record and starts today or
     * earlier in Vietnam, oldest start first; each in a transaction of its own, the version EFFECTIVE through the guarded
     * update, then one VERSION_STARTED history row at the start of its start date, without an actor. The failure rule of
     * {@link #storeDueLapses()} applies. No event: the one due at the start was appended at the activation.
     * <p>
     * Its only caller is AgreementStartJob; no controller reaches it, so it takes no viewer.
     *
     * @return the number of starts stored
     */
    int storeDueStarts();
}
