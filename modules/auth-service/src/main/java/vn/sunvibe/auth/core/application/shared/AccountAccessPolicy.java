package vn.sunvibe.auth.core.application.shared;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

/**
 * Says what an account must do next, so the client does not derive it from flags (auth-email-access 4.6). A verified email
 * is enough in every mode; with the phone channel open a phone user is not asked for an email
 * (auth-email-access BR-22).
 */
@Component
@RequiredArgsConstructor
public class AccountAccessPolicy {

    private final PhoneChannelPolicy phoneChannel;

    /** @return the action, or null when there is none */
    public RequiredAction requiredAction(AppUser user) {
        if (user.isEmailVerified()) {
            return null;
        }
        if (phoneChannel.isOn()) {
            return user.isPhoneVerified() ? null : RequiredAction.VERIFY_PHONE;
        }
        return RequiredAction.BIND_EMAIL;
    }
}
