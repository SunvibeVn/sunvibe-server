package vn.sunvibe.auth.core.application.query.user;

import lombok.Builder;
import vn.sunvibe.auth.core.application.shared.RequiredAction;

/**
 * The signed-in user's own profile.
 *
 * @param email         null when the account has no e-mail
 * @param kycStatus     name of the {@code KycStatus}
 * @param accountStatus name of the {@code UserStatus}
 * @param requiredAction what the account must do next; null when nothing
 */
@Builder
public record UserProfileResult(String fullName, String username, String email, String profileImageUrl,
                                String kycStatus, String accountStatus, boolean phoneVerified,
                                boolean emailVerified, RequiredAction requiredAction) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "UserProfileResult[hidden]";
    }
}
