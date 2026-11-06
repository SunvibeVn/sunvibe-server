package vn.sunvibe.auth.core.application.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy;
import vn.sunvibe.auth.core.application.usecase.user.LoginIdentifier;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

import java.util.Optional;

/**
 * Which account a sign-in reaches. The login and the password check ask the same place, so they cannot disagree. An
 * account not eligible for the channel counts as not found: no lock check and no counter (auth-email-access spec 5.5).
 */
@Component
@RequiredArgsConstructor
public class LoginAccountFinder {

    private final UserDomainRepository users;
    private final PhoneChannelPolicy phoneChannel;

    /**
     * What a typed phone means. While {@code legacy-login} is open, a value the policy refuses, or a number no key
     * matches, is looked up as the exact stored string (auth-email-access D4-7); with phone fully on it is refused.
     *
     * @throws AuthValidationException if the policy refuses the value outside {@code legacy-login}
     */
    public LoginIdentifier phoneIdentifier(String raw) {
        PhoneNumber phone;
        try {
            phone = PhoneNumber.create(raw);
        } catch (AuthValidationException refused) {
            if (phoneChannel.isLegacyLoginOpen()) {
                return new LoginIdentifier.LegacyLogin(raw);
            }
            throw refused;
        }
        if (phoneChannel.isLegacyLoginOpen() && users.findByPhoneKey(phone.key()).isEmpty()) {
            return new LoginIdentifier.LegacyLogin(raw);
        }
        return new LoginIdentifier.PhoneLogin(phone.key(), raw);
    }

    public Optional<AppUser> find(LoginIdentifier identifier) {
        return switch (identifier) {
            case LoginIdentifier.EmailLogin email -> users.findByEmailKey(email.key()).filter(AppUser::isEmailVerified);
            case LoginIdentifier.PhoneLogin phone -> users.findByPhoneKey(phone.key()).filter(this::mayUsePhone);
            case LoginIdentifier.LegacyLogin legacy -> legacy.hasOldShape() && phoneChannel.isLegacyLoginOpen()
                    ? users.findKeylessByUsername(legacy.raw()).filter(this::mayUsePhone)
                    : Optional.empty();
        };
    }

    /** Whether the identifier still reaches this row, read under its lock after the password check. */
    public boolean stillReaches(LoginIdentifier identifier, AppUser user) {
        return switch (identifier) {
            case LoginIdentifier.EmailLogin email -> email.key().equals(user.getEmailKey()) && user.isEmailVerified();
            case LoginIdentifier.PhoneLogin phone -> phone.key().equals(user.getPhoneKey()) && mayUsePhone(user);
            case LoginIdentifier.LegacyLogin legacy -> legacy.raw().equals(user.usernameValue())
                    && user.getPhoneKey() == null && mayUsePhone(user);
        };
    }

    /** The legacy window serves accounts without a verified email only; phone fully on follows auth-email-access D4-9. */
    private boolean mayUsePhone(AppUser user) {
        return phoneChannel.isLegacyLoginOpen() ? !user.isEmailVerified() : user.canUsePhone();
    }
}
