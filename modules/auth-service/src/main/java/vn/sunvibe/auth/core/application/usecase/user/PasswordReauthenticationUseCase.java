package vn.sunvibe.auth.core.application.usecase.user;

/**
 * Asks a signed-in user for the password again, before a change an open session alone must not make (auth-email-access
 * spec 4.9, D4-12). A wrong password counts on a counter of its own with the policy of the login lock: a stranger's login
 * lock cannot stop it, and a stolen access token cannot guess without limit. Published for other modules: property-create
 * US-2 AC-1 needs the same step through an ADR-009 port.
 */
public interface PasswordReauthenticationUseCase {

    /**
     * Runs in its own short transaction, so it must be called before any transaction of the caller: it holds no row lock
     * through bcrypt and leaves none behind. Never throws.
     */
    ReauthenticationResult check(String userId, String password);
}
