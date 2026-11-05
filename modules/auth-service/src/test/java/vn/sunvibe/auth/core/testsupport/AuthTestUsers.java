package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.time.Instant;
import java.util.Set;

/** Accounts for unit tests: a phone account, with an unverified email when a test needs one on the row. */
public final class AuthTestUsers {

    private AuthTestUsers() {
    }

    /** The channel as the test profile has it: fully on. */
    public static PhoneChannelPolicy phoneOn() {
        return new PhoneChannelPolicy(PhoneChannelPolicy.Mode.ON, null, new RecordingAuditLog());
    }

    public static AppUser withPhone(UserId id, String fullName, String phone, Email email, Password password,
                                    ProfileImage profileImage, IdCardNumber idCardNumber, Set<String> roles, Instant now) {
        AppUser user = AppUser.createWithPhone(id, fullName, PhoneNumber.create(phone), password, profileImage,
                idCardNumber, roles, now);
        user.setEmail(email);
        return user;
    }
}
