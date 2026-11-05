package vn.sunvibe.auth.core.application.shared;

/**
 * What a password check established: which account it was, and the credential version the checked hash belonged to.
 * The login compares that version under the row lock, so a password that changed during the check starts no session
 * (auth-email-access spec 5.4).
 */
public interface VerifiedCredentials {

    String userId();

    long credentialVersion();
}
