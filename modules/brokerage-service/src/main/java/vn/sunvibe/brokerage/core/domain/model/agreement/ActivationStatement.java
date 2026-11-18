package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * The statement an independent manager makes when activating a version, held by the server with its version
 * (brokerage-agreement BR-11: the manager states that the signed document matches every term of the confirmed version
 * and that the owner signing it is the confirmed owner as one individual, BR-4; E05-S6 spec S6-D3). The activation
 * records its {@link #VERSION}. The text is a proposal a lawyer reads before any real owner (brief Q-1); a changed text
 * takes a new {@link #VERSION}.
 */
public final class ActivationStatement {

    /** The version of the text below, recorded with every activation. */
    public static final String VERSION = "brokerage-activation-2026-11";

    private ActivationStatement() {
    }

    /** The statement for one version, naming its number twice. */
    public static String text(int versionNo) {
        return "I compared the signed document with every term of version " + versionNo + " above: the period, the"
                + " advertising permission, the photo consent, the rental terms and the brokerage fee policy match it."
                + " The owner who signed it is the individual who confirmed version " + versionNo + ", signing for"
                + " themselves: no company, representative or co-owner signed for them. The document's number, date and"
                + " Sunvibe's signatory below are as written on it.";
    }
}
