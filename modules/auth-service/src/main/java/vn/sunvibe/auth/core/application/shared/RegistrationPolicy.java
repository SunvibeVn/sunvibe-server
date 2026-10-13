package vn.sunvibe.auth.core.application.shared;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Registration switches, from {@code app.security.registration.*}.
 */
@Getter
@Component
public class RegistrationPolicy {

    /**
     * When true, registration needs a registration token proving the phone number
     * ("verify first, register later"). When false the older flow still works: the
     * account is created PENDING and verified afterwards. Turn it on once clients send the token.
     */
    private final boolean requirePhoneVerification;

    public RegistrationPolicy(@Value("${app.security.registration.require-phone-verification:true}")
                              boolean requirePhoneVerification) {
        this.requirePhoneVerification = requirePhoneVerification;
    }
}
