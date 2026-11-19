package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.Instant;

/**
 * One suspension of an agreement (brokerage-agreement BR-15, BR-16; E05-S8 spec S8-D6): who suspended it, when and why;
 * once lifted, who lifted it, when and why. Kept beside the aggregate, which holds the state SUSPENDED: an agreement has
 * at most one open record, and a record is open only while the agreement is SUSPENDED, or in TERMINATED or EXPIRED
 * reached from SUSPENDED. Private (brokerage-agreement BR-17): the note and the release reason reach only the staff
 * detail, never a text of this record.
 *
 * @param noteOrNull          the manager's optional note, trimmed, at most 500 UTF-16 units
 * @param releasedByOrNull    the manager who lifted it; set exactly with the release time and reason
 * @param releaseReasonOrNull why it was lifted, trimmed, 1 to 500 UTF-16 units
 */
public record AgreementSuspension(String id, String agreementId, SuspensionReasonType reasonType, String noteOrNull,
                                  String suspendedBy, Instant suspendedAt, String releasedByOrNull,
                                  Instant releasedAtOrNull, String releaseReasonOrNull) {

    /** Technical bound of the note and the release reason, in UTF-16 units as Bean Validation and the UI count them. */
    public static final int MAX_TEXT_LENGTH = 500;

    /**
     * The combinations the store allows (E05-S8 spec S8-D6, S8-D9): the identity, the type, who and when set; the three
     * release fields all set or all null, the release not before the suspension; each text 1 to 500 units with no
     * character that can hide it (the rule of E05-S4 spec S4-D3).
     *
     * @throws IllegalArgumentException another combination: a data fault when loaded
     */
    public AgreementSuspension {
        if (id == null || agreementId == null || reasonType == null || suspendedBy == null || suspendedAt == null) {
            throw new IllegalArgumentException("A suspension needs its identity, type, author and time.");
        }
        boolean released = releasedByOrNull != null || releasedAtOrNull != null || releaseReasonOrNull != null;
        if (released && (releasedByOrNull == null || releasedAtOrNull == null || releaseReasonOrNull == null)) {
            throw new IllegalArgumentException("A lifted suspension holds who lifted it, when and why.");
        }
        if (released && releasedAtOrNull.isBefore(suspendedAt)) {
            throw new IllegalArgumentException("A suspension is not lifted before it began.");
        }
        if ((noteOrNull != null && !validText(noteOrNull))
                || (releaseReasonOrNull != null && !validText(releaseReasonOrNull))) {
            throw new IllegalArgumentException("A suspension text breaks its rule.");
        }
    }

    /**
     * A new open record (E05-S8 spec S8-D6): the note is trimmed, absent or blank stores none, else it holds at most 500
     * units and no character that can hide the text.
     *
     * @throws AgreementContentException on {@code note}
     */
    public static AgreementSuspension open(String id, String agreementId, SuspensionReasonType reasonType,
                                           String rawNoteOrNull, String suspendedBy, Instant suspendedAt) {
        return new AgreementSuspension(id, agreementId, reasonType, note(rawNoteOrNull), suspendedBy, suspendedAt, null,
                null, null);
    }

    /**
     * The reason a manager gives for lifting the suspension (brokerage-agreement BR-15 "Gỡ tạm ngưng cần lý do"; E05-S8
     * spec S8-D6): trimmed, 1 to 500 units, no character that can hide the text.
     *
     * @throws AgreementContentException on {@code reason}
     */
    public static String releaseReason(String rawOrNull) {
        String reason = rawOrNull == null ? "" : rawOrNull.strip();
        if (!validText(reason)) {
            throw new AgreementContentException("reason");
        }
        return reason;
    }

    /**
     * This record closed by a manager at {@code releasedAt} with a reason already checked by {@link #releaseReason}.
     *
     * @throws IllegalStateException when it is already closed
     */
    public AgreementSuspension released(String releasedBy, Instant releasedAt, String reason) {
        if (!isOpen()) {
            throw new IllegalStateException("The suspension was lifted already.");
        }
        return new AgreementSuspension(id, agreementId, reasonType, noteOrNull, suspendedBy, suspendedAt, releasedBy,
                releasedAt, reason);
    }

    /** Whether the suspension was not lifted. */
    public boolean isOpen() {
        return releasedAtOrNull == null;
    }

    private static String note(String rawOrNull) {
        if (rawOrNull == null) {
            return null;
        }
        String note = rawOrNull.strip();
        if (note.isEmpty()) {
            return null;
        }
        if (!validText(note)) {
            throw new AgreementContentException("note");
        }
        return note;
    }

    private static boolean validText(String text) {
        return !text.isBlank() && text.length() <= MAX_TEXT_LENGTH && !AgreementText.hidesText(text);
    }

    /** brokerage-agreement BR-17: the identity, the type and whether it is open; never the note or the reason. */
    @Override
    public String toString() {
        return "AgreementSuspension[" + id + ", " + reasonType + (isOpen() ? ", open" : ", lifted") + "]";
    }
}
