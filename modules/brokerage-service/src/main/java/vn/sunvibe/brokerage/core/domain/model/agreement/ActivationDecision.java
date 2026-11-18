package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.Instant;

/**
 * A manager's decision on a version awaiting activation (brokerage-agreement US-3 AC-1 to AC-3, BR-11; E05-S6 spec
 * S6-D3, S6-D4, S6-D14): who decided and when; for an activation, the statement version made and the signed document;
 * for a refusal, its type and an optional note. A version holds at most one, and it never changes (BR-5).
 *
 * @param statementVersionOrNull the activation statement made ({@link ActivationStatement}); set exactly for
 *                               {@link ActivationDecisionType#ACTIVATED}
 * @param signedDocumentOrNull   the document as written on it; set exactly for {@link ActivationDecisionType#ACTIVATED}
 * @param refusalTypeOrNull      why the activation was refused; set exactly for {@link ActivationDecisionType#REFUSED}
 * @param refusalNoteOrNull      the manager's optional note, only for a refusal; private (brokerage-agreement BR-17)
 */
public record ActivationDecision(ActivationDecisionType type, String userId, Instant decidedAt,
                                 String statementVersionOrNull, SignedDocument signedDocumentOrNull,
                                 ActivationRefusalType refusalTypeOrNull, String refusalNoteOrNull) {

    /** Technical bound of the refusal note, in UTF-16 units as Bean Validation and the UI count them. */
    public static final int MAX_NOTE_LENGTH = 500;

    /**
     * The combinations the store allows (E05-S6 spec S6-D14): type, account and time set; an activation holds the
     * statement version and the document and no refusal part; a refusal holds its type and no statement or document.
     *
     * @throws IllegalArgumentException another combination: a data fault when loaded
     */
    public ActivationDecision {
        if (type == null || userId == null || decidedAt == null) {
            throw new IllegalArgumentException("An activation decision needs its type, account and time.");
        }
        if (type == ActivationDecisionType.ACTIVATED && (statementVersionOrNull == null || signedDocumentOrNull == null
                || refusalTypeOrNull != null || refusalNoteOrNull != null)) {
            throw new IllegalArgumentException("An activation holds the statement and the document and no refusal.");
        }
        if (type == ActivationDecisionType.REFUSED && (refusalTypeOrNull == null || statementVersionOrNull != null
                || signedDocumentOrNull != null)) {
            throw new IllegalArgumentException("A refusal holds its type and no statement or document.");
        }
    }

    /** brokerage-agreement BR-11: the manager activated after the statement of this version, against the document. */
    public static ActivationDecision activated(String userId, Instant at, String statementVersion,
                                               SignedDocument document) {
        return new ActivationDecision(ActivationDecisionType.ACTIVATED, userId, at, statementVersion, document, null,
                null);
    }

    /**
     * brokerage-agreement US-3 AC-3, E-7: the manager refused, with its type and an optional note. The note is trimmed;
     * absent or blank stores none; else it holds 1 to 500 UTF-16 units and no character that can hide the text (E05-S6
     * spec S6-D4, the rule of E05-S4 spec S4-D3).
     *
     * @throws AgreementContentException on {@code note}: too long, or a hidden character
     */
    public static ActivationDecision refused(String userId, Instant at, ActivationRefusalType type, String noteOrNull) {
        return new ActivationDecision(ActivationDecisionType.REFUSED, userId, at, null, null, type, note(noteOrNull));
    }

    private static String note(String noteOrNull) {
        if (noteOrNull == null) {
            return null;
        }
        String note = noteOrNull.strip();
        if (note.isEmpty()) {
            return null;
        }
        if (note.length() > MAX_NOTE_LENGTH || AgreementText.hidesText(note)) {
            throw new AgreementContentException("note");
        }
        return note;
    }

    /**
     * brokerage-agreement BR-17: the type, the refusal type and whether a note is held; never the document, the note,
     * the account or the statement.
     */
    @Override
    public String toString() {
        if (type == ActivationDecisionType.ACTIVATED) {
            return "ActivationDecision[" + type + "]";
        }
        return "ActivationDecision[" + type + ", " + refusalTypeOrNull + (refusalNoteOrNull == null ? "" : ", note")
                + "]";
    }
}
