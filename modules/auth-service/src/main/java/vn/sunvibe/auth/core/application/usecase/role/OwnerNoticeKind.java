package vn.sunvibe.auth.core.application.usecase.role;

/** The notices an account gets about an owner link (property-create intake D5-7, A-12): fixed text, no code or address. */
public enum OwnerNoticeKind {
    /** The account confirmed an owner link. */
    OWNER_CONFIRMED,
    /** The account withdrew an owner link. */
    OWNER_WITHDRAWN,
    /** A manager could not confirm the link by phone. Neutral on purpose: it is never a withdrawal. */
    OWNER_CHECK_REFUSED
}
