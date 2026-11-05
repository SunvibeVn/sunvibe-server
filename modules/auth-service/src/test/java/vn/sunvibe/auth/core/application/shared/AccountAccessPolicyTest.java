package vn.sunvibe.auth.core.application.shared;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access 4.6: every row of the table, in every mode of the phone channel. */
class AccountAccessPolicyTest {

    private static AccountAccessPolicy in(PhoneChannelPolicy.Mode mode, Instant legacyUntil) {
        return new AccountAccessPolicy(new PhoneChannelPolicy(mode, legacyUntil, new RecordingAuditLog()));
    }

    private static final AccountAccessPolicy ON = in(PhoneChannelPolicy.Mode.ON, null);
    private static final AccountAccessPolicy OFF = in(PhoneChannelPolicy.Mode.OFF, null);
    private static final AccountAccessPolicy LEGACY = in(PhoneChannelPolicy.Mode.LEGACY_LOGIN, Instant.now().plus(Duration.ofDays(1)));
    private static final AccountAccessPolicy LEGACY_ENDED = in(PhoneChannelPolicy.Mode.LEGACY_LOGIN, Instant.now().minus(Duration.ofDays(1)));

    private static AppUser phoneUser() {
        return AuthTestUsers.withPhone(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A", "0912345678", null,
                Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(), Instant.now());
    }

    @Test
    void action_verifiedEmail_null() {
        AppUser user = phoneUser();
        user.bindVerifiedEmail(Email.create("a@example.com"), Instant.now());

        for (AccountAccessPolicy policy : List.of(ON, OFF, LEGACY, LEGACY_ENDED)) {
            assertNull(policy.requiredAction(user));
        }
    }

    @Test
    void action_on_phoneUnverified_verifyPhone() {
        assertEquals(RequiredAction.VERIFY_PHONE, ON.requiredAction(phoneUser()));
    }

    /** BR-22: a phone user is not asked for an email while the channel is open. */
    @Test
    void action_on_phoneVerified_null() {
        AppUser user = phoneUser();
        user.verifyPhone(Instant.now());

        assertNull(ON.requiredAction(user));
    }

    @Test
    void action_offOrLegacy_noEmail_bindEmail() {
        AppUser verifiedPhone = phoneUser();
        verifiedPhone.verifyPhone(Instant.now());

        for (AccountAccessPolicy policy : List.of(OFF, LEGACY, LEGACY_ENDED)) {
            assertEquals(RequiredAction.BIND_EMAIL, policy.requiredAction(phoneUser()));
            assertEquals(RequiredAction.BIND_EMAIL, policy.requiredAction(verifiedPhone));
        }
    }

    /** A stored address without a key proves nothing (spec 4.1). */
    @Test
    void action_keylessStoredEmail_countsAsNoEmail() {
        AppUser user = phoneUser();
        user.setEmail(Email.from("a@example.com"));
        user.setEmailVerifiedAt(Instant.now());

        assertEquals(RequiredAction.BIND_EMAIL, OFF.requiredAction(user));
    }
}
