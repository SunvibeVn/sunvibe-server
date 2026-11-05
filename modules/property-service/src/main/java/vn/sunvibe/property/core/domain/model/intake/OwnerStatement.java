package vn.sunvibe.property.core.domain.model.intake;

/** The statement an owner confirms (property-create intake A-10): a proof stores its version, so the text never changes. */
public final class OwnerStatement {

    public static final String VERSION = "owner-link-2026-11";
    public static final String TEXT = "I confirm that this Sunvibe account is mine and that I declare myself the owner of the "
            + "parts listed above. Sunvibe may keep this link in its internal inventory. This is not a check of ownership, a "
            + "permission to advertise, or a brokerage agreement.";

    private OwnerStatement() {
    }
}
