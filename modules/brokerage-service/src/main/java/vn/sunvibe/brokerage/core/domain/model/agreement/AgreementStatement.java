package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * The statement the owner confirms a version with, held by the server with its version (brokerage-agreement BR-10: the
 * owner reads every term and a statement naming the version number, and the statement version is recorded; E05-S5 spec
 * S5-D3). It states BR-10's three points in English and nothing else: no claim that the agreement is legally valid or
 * verified (BR-4, BR-19). The text is a proposal a lawyer reads before any real owner (brief Q-1); a changed text takes a
 * new {@link #VERSION}.
 */
public final class AgreementStatement {

    /** The version of the text below, recorded with every confirmation. */
    public static final String VERSION = "brokerage-agreement-2026-11";

    private static final String TERMS = ": I have read every term above. Sunvibe may advertise this unit on Sunvibe's"
            + " own channels under these terms and for this period. I declare that I have the right to let this unit and"
            + " to allow it to be advertised. The written agreement that Sunvibe and I sign is the binding document.";

    private AgreementStatement() {
    }

    /** The statement of one version, naming its number and its unit's code ("this unit" without a code). */
    public static String text(int versionNo, String unitCodeOrNull) {
        String unit = unitCodeOrNull == null ? "this unit" : "unit " + unitCodeOrNull;
        return "Version " + versionNo + " of the brokerage agreement for " + unit + TERMS;
    }
}
