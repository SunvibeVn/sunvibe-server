package vn.sunvibe.auth.core.domain.repository.user;

/**
 * Who holds a canonical email among live rows (auth-email-access spec 4.8, I-3). Only ever answered after the mailbox
 * was proven, so a stranger cannot use it to learn who has an account.
 */
public enum EmailOccupancy {
    /** No live row holds the address. */
    FREE,
    /** It is the verified address of an account. */
    VERIFIED,
    /** A live row holds it unverified, or stores an equivalent address without a key. */
    OTHER
}
