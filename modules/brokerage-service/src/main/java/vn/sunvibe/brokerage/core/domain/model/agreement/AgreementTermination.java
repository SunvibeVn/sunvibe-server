package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What staff recorded when they terminated an agreement (brokerage-agreement BR-14, BR-16; E05-S8 spec S8-D5): the type
 * and the fields that type takes, and the moment of the termination. Private (brokerage-agreement BR-17): the date, the
 * channel, the answer on the tenant and the note reach only the staff detail, never a text of this record.
 *
 * @param reportedOnOrNull the date the owner said it (OWNER_WITHDREW) or reported it (UNIT_LET); null otherwise
 * @param channelOrNull    how the owner's withdrawal came; set exactly for OWNER_WITHDREW
 * @param introducedOrNull whether Sunvibe introduced the tenant; set exactly for UNIT_LET
 * @param noteOrNull       trimmed, at most 500 UTF-16 units; required for SUNVIBE_TERMINATED, optional otherwise
 */
public record AgreementTermination(TerminationType type, LocalDate reportedOnOrNull, WithdrawalChannel channelOrNull,
                                   IntroducedBySunvibe introducedOrNull, String noteOrNull, Instant terminatedAt) {

    /** Technical bound of the note, in UTF-16 units as Bean Validation and the UI count them. */
    public static final int MAX_NOTE_LENGTH = 500;
    /** The earliest reported date accepted, against date arithmetic on absurd values. */
    public static final LocalDate EARLIEST_REPORTED_ON = AgreementPeriod.EARLIEST;

    /**
     * The combinations of E05-S8 spec S8-D5's table: OWNER_WITHDREW holds its date and channel, UNIT_LET its date and the
     * answer on the tenant, SUNVIBE_TERMINATED its note and no date; no type holds a field of another; the note holds 1
     * to 500 units and no character that can hide the text (the rule of E05-S4 spec S4-D3).
     *
     * @throws IllegalArgumentException another combination: a data fault when loaded
     */
    public AgreementTermination {
        if (type == null || terminatedAt == null) {
            throw new IllegalArgumentException("A termination needs its type and its moment.");
        }
        boolean fits = switch (type) {
            case OWNER_WITHDREW -> reportedOnOrNull != null && channelOrNull != null && introducedOrNull == null;
            case UNIT_LET -> reportedOnOrNull != null && introducedOrNull != null && channelOrNull == null;
            case SUNVIBE_TERMINATED -> noteOrNull != null && reportedOnOrNull == null && channelOrNull == null
                    && introducedOrNull == null;
        };
        if (!fits) {
            throw new IllegalArgumentException("A termination holds the fields of its type only.");
        }
        if (noteOrNull != null && !validText(noteOrNull)) {
            throw new IllegalArgumentException("A termination note breaks its rule.");
        }
    }

    /**
     * The termination as staff entered it (E05-S8 spec S8-D5, plan E05S8-P5), checked in the order of spec section 3.3:
     * a field of another type, then a missing required field, then a bad value. The reported date lies from 2000-01-01
     * to {@code todayInVietnam}; the note is trimmed, absent or blank stores none (required for SUNVIBE_TERMINATED), and
     * holds at most 500 units and no character that can hide the text.
     *
     * @throws AgreementContentException on {@code reportedOn}, {@code channel}, {@code introducedBySunvibe} or
     *                                   {@code note}
     */
    public static AgreementTermination of(TerminationType type, LocalDate reportedOnOrNull,
                                          WithdrawalChannel channelOrNull, IntroducedBySunvibe introducedOrNull,
                                          String rawNoteOrNull, LocalDate todayInVietnam, Instant terminatedAt) {
        if (type == null) {
            throw new AgreementContentException("type");
        }
        boolean takesDate = type != TerminationType.SUNVIBE_TERMINATED;
        boolean takesChannel = type == TerminationType.OWNER_WITHDREW;
        boolean takesIntroduced = type == TerminationType.UNIT_LET;
        // brokerage-agreement BR-14 (E05-S8 spec S8-D5): each type takes its own fields and none of another type
        if (!takesDate && reportedOnOrNull != null) {
            throw new AgreementContentException("reportedOn");
        }
        if (!takesChannel && channelOrNull != null) {
            throw new AgreementContentException("channel");
        }
        if (!takesIntroduced && introducedOrNull != null) {
            throw new AgreementContentException("introducedBySunvibe");
        }
        if (takesDate && reportedOnOrNull == null) {
            throw new AgreementContentException("reportedOn");
        }
        if (takesChannel && channelOrNull == null) {
            throw new AgreementContentException("channel");
        }
        if (takesIntroduced && introducedOrNull == null) {
            throw new AgreementContentException("introducedBySunvibe");
        }
        // brokerage-agreement BR-14: a reported date is a calendar date that has come, in Vietnam
        if (reportedOnOrNull != null && (reportedOnOrNull.isBefore(EARLIEST_REPORTED_ON)
                || reportedOnOrNull.isAfter(todayInVietnam))) {
            throw new AgreementContentException("reportedOn");
        }
        String note = note(rawNoteOrNull);
        // brokerage-agreement BR-16: Sunvibe's decision is recorded with its reason in words
        if (type == TerminationType.SUNVIBE_TERMINATED && note == null) {
            throw new AgreementContentException("note");
        }
        return new AgreementTermination(type, reportedOnOrNull, channelOrNull, introducedOrNull, note, terminatedAt);
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
        return !text.isBlank() && text.length() <= MAX_NOTE_LENGTH && !AgreementText.hidesText(text);
    }

    /** brokerage-agreement BR-17: the type only; never the date, the channel, the answer on the tenant or the note. */
    @Override
    public String toString() {
        return "AgreementTermination[" + type + "]";
    }
}
