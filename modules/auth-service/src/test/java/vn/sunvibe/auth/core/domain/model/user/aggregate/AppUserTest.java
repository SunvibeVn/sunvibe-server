package vn.sunvibe.auth.core.domain.model.user.aggregate;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;

import java.time.Instant;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class AppUserTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static AppUser emailUser() {
        return AppUser.registerWithEmail(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A",
                Email.create("A.User@Example.com"), Password.create("hashed-password"),
                new HashSet<>(java.util.Set.of("TENANT", "CUSTOMER")), NOW);
    }

    /** auth-email-access 4.4: the mailbox was proven before the account, so it starts ACTIVE, verified, without a phone. */
    @Test
    void user_registerWithEmail_activeVerifiedNoPhone() {
        AppUser user = emailUser();

        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
        assertEquals(KycStatus.UNVERIFIED, user.getKycStatus());
        assertEquals("a.user@example.com", user.getEmail().getValue());
        assertEquals("a.user@example.com", user.getEmailKey());
        assertEquals(NOW, user.getEmailVerifiedAt());
        assertTrue(user.isEmailVerified());
        assertNull(user.getUsername());
        assertNull(user.getPhoneKey());
        assertNull(user.usernameValue());
        assertFalse(user.isPhoneVerified());
        assertEquals(NOW, user.getCreatedDate());
        assertEquals(0L, user.getCredentialVersion());
    }

    /** auth-email-access 4.1: a legacy row without a key is not verified for sign-in, reset or the authority. */
    @Test
    void user_emailVerified_falseWithoutKey() {
        AppUser user = emailUser();
        user.setEmailKey(null);

        assertFalse(user.isEmailVerified());
    }

    /** auth-email-access D4-9: a verified email keeps whoever holds an unproven number out. */
    @Test
    void canUsePhone_verifiedEmailUnverifiedPhone_false() {
        AppUser user = newUser();
        user.bindVerifiedEmail(Email.create("a@example.com"), NOW);

        assertFalse(user.canUsePhone());

        user.verifyPhone(NOW);

        assertTrue(user.canUsePhone());
    }

    @Test
    void canUsePhone_noVerifiedEmail_true() {
        assertTrue(newUser().canUsePhone());
    }

    @Test
    void canUsePhone_noPhone_false() {
        assertFalse(emailUser().canUsePhone());
    }

    /** auth-email-access I-5. */
    @Test
    void changePassword_raisesTheCredentialVersion() {
        AppUser user = newUser();

        user.changePassword("new-hash");

        assertEquals(1L, user.getCredentialVersion());
    }

    private AppUser newUser() {
        return AuthTestUsers.withPhone(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                "0912345678",
                Email.create("a@example.com"),
                Password.create("hashed-password"),
                ProfileImage.create(null),
                IdCardNumber.create("123456789012"),
                new HashSet<>(),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void create_defaultsKycStatusToUnverified() {
        assertEquals(KycStatus.UNVERIFIED, newUser().getKycStatus());
    }

    @Test
    void create_leavesIdCardImagesAndDeletedAtNull() {
        AppUser user = newUser();
        assertNull(user.getIdCardFrontUrl());
        assertNull(user.getIdCardBackUrl());
        assertNull(user.getDeletedAt());
    }

    @Test
    void create_storesTheGivenIdCardNumber() {
        assertEquals("123456789012", newUser().getIdCardNumber().getValue());
    }

    @Test
    void deactivate_setsStatusDeletedAndDeletedAtTimestamp() {
        AppUser user = newUser();
        user.deactivate();
        assertEquals(UserStatus.DELETED, user.getUserStatus());
        assertNotNull(user.getDeletedAt());
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void canAuthenticate_onlyBlockedStatusesAreRejected(UserStatus status) {
        AppUser user = newUser();
        user.setUserStatus(status);

        boolean expected = switch (status) {
            case ACTIVE, PENDING -> true;
            case LOCK, TERMINATED, DELETED -> false;
        };
        assertEquals(expected, user.canAuthenticate());
    }

    @Test
    void create_startsPendingWithNothingVerified() {
        AppUser user = newUser();

        assertEquals(UserStatus.PENDING, user.getUserStatus());
        assertFalse(user.isPhoneVerified());
        assertFalse(user.isEmailVerified());
        assertTrue(user.canAuthenticate(), "a PENDING user can sign in to verify the phone");
    }

    @Test
    void verifyPhone_activatesAPendingAccount() {
        AppUser user = newUser();
        Instant now = Instant.parse("2026-01-02T00:00:00Z");

        user.verifyPhone(now);

        assertTrue(user.isPhoneVerified());
        assertEquals(now, user.getPhoneVerifiedAt());
        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
    }

    @Test
    void verifyPhone_doesNotUnlockALockedAccount() {
        AppUser user = newUser();
        user.setUserStatus(UserStatus.LOCK);

        user.verifyPhone(Instant.now());

        assertEquals(UserStatus.LOCK, user.getUserStatus());
    }

    /** auth-email-access D4-4, S-24: the id and the roles stay, older proofs and tokens die. */
    @Test
    void bindVerifiedEmail_writesAddressKeyAndVersion() {
        AppUser user = newUser();
        long version = user.getCredentialVersion();

        user.bindVerifiedEmail(Email.create("new@example.com"), NOW);

        assertEquals("new@example.com", user.getEmail().getValue());
        assertEquals("new@example.com", user.getEmailKey());
        assertEquals(NOW, user.getEmailVerifiedAt());
        assertEquals(version + 1, user.getCredentialVersion());
    }

    @Test
    void bindVerifiedEmail_pendingBecomesActive() {
        AppUser user = newUser();
        user.setUserStatus(UserStatus.PENDING);

        user.bindVerifiedEmail(Email.create("new@example.com"), NOW);

        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
    }

    /** auth-email-access S-22: a code never moves LOCK or TERMINATED. */
    @Test
    void bindVerifiedEmail_leavesLockAlone() {
        AppUser user = newUser();
        user.setUserStatus(UserStatus.LOCK);

        user.bindVerifiedEmail(Email.create("new@example.com"), NOW);

        assertEquals(UserStatus.LOCK, user.getUserStatus());
    }
}
