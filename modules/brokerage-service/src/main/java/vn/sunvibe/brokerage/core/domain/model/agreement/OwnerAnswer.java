package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.Instant;

/**
 * The owner's answer to a version sent to them (brokerage-agreement US-2 AC-2, AC-3; E05-S5 spec S5-D7, S5-D11): who
 * answered and when, the statement version they confirmed, or the reason they gave for declining. brokerage-agreement
 * BR-5: an answer never changes once given; the version holds at most one.
 *
 * @param statementVersionOrNull the confirmation statement read (brokerage-agreement BR-10); set exactly for
 *                               {@link OwnerAnswerType#CONFIRMED}
 * @param rejectionReasonOrNull  the owner's optional reason, only for {@link OwnerAnswerType#REJECTED}; private
 *                               (brokerage-agreement BR-17)
 */
public record OwnerAnswer(OwnerAnswerType type, String userId, Instant answeredAt, String statementVersionOrNull,
                          String rejectionReasonOrNull) {

    /** Technical bound of the rejection reason, in UTF-16 units as Bean Validation and the UI count them. */
    public static final int MAX_REASON_LENGTH = 500;

    /**
     * The combinations the store allows (E05-S5 spec S5-D11): type, account and time set; a confirmation holds a
     * statement version and no reason; a rejection holds no statement version.
     *
     * @throws IllegalArgumentException another combination: a data fault when loaded
     */
    public OwnerAnswer {
        if (type == null || userId == null || answeredAt == null) {
            throw new IllegalArgumentException("An owner answer needs its type, account and time.");
        }
        if (type == OwnerAnswerType.CONFIRMED && (statementVersionOrNull == null || rejectionReasonOrNull != null)) {
            throw new IllegalArgumentException("A confirmation holds the statement version and no reason.");
        }
        if (type == OwnerAnswerType.REJECTED && statementVersionOrNull != null) {
            throw new IllegalArgumentException("A rejection holds no statement version.");
        }
    }

    /** brokerage-agreement BR-10: the owner confirmed after reading the statement of this version. */
    public static OwnerAnswer confirmed(String userId, String statementVersion, Instant at) {
        return new OwnerAnswer(OwnerAnswerType.CONFIRMED, userId, at, statementVersion, null);
    }

    /**
     * brokerage-agreement US-2 AC-3: the owner declined, with an optional reason. The reason is trimmed; absent or
     * blank stores none; else it holds 1 to 500 UTF-16 units and no character that can hide the text (E05-S5 spec S5-D7,
     * the rule of the fee policy's free reason).
     *
     * @throws AgreementContentException on {@code reason}: too long, or a hidden character
     */
    public static OwnerAnswer rejected(String userId, String reasonOrNull, Instant at) {
        return new OwnerAnswer(OwnerAnswerType.REJECTED, userId, at, null, reason(reasonOrNull));
    }

    private static String reason(String reasonOrNull) {
        if (reasonOrNull == null) {
            return null;
        }
        String reason = reasonOrNull.strip();
        if (reason.isEmpty()) {
            return null;
        }
        if (reason.length() > MAX_REASON_LENGTH || AgreementText.hidesText(reason)) {
            throw new AgreementContentException("reason");
        }
        return reason;
    }

    /** brokerage-agreement BR-17: the type and whether a reason is held, never the reason, the account or the statement. */
    @Override
    public String toString() {
        return "OwnerAnswer[" + type + (rejectionReasonOrNull == null ? "" : ", reason") + "]";
    }
}
