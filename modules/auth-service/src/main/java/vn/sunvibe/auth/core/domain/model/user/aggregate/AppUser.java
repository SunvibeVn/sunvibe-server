package vn.sunvibe.auth.core.domain.model.user.aggregate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate root of a user account: identity, credentials, contact
 * verification, login lockout and lifecycle status.
 * <p>
 * New accounts are built with {@link #createWithPhone} or {@link #registerWithEmail}. The no-args constructor and the
 * setters exist for the persistence mapper only; application code changes an
 * account through the business methods below so that invariants (e.g.
 * {@link #lastUpdate}) are kept.
 */
@NoArgsConstructor
@Getter
@Setter
public class AppUser {

    private UserId id;

    private String fullName;

    /** The phone in its national form, for display; null for an account without a phone. */
    private Username username;

    /** Optional; null until the user provides one. */
    private Email email;

    private Password password;

    private ProfileImage profileImage;

    /** Optional national ID card number (CMND/CCCD). */
    private IdCardNumber idCardNumber;

    private String idCardFrontUrl;

    private String idCardBackUrl;

    private KycStatus kycStatus;

    private Instant createdDate;

    private Instant lastUpdate;

    private Instant lastLogin;

    /** Set when the account is soft-deleted; its phone number and e-mail become free again. */
    private Instant deletedAt;

    /** Role names (see {@link RoleName}). */
    private Set<String> userRoles;

    private UserStatus userStatus;

    /** Consecutive wrong passwords since the last successful login or password reset. */
    private int failedLoginAttempts;

    /** Password login is refused until this instant; null when not temporarily locked. */
    private Instant lockedUntil;

    /** When the user proved control of the phone number (username); null until then. */
    private Instant phoneVerifiedAt;

    /** When the user proved control of the current e-mail; null until then or after it changes. */
    private Instant emailVerifiedAt;

    /** Canonical value of {@link #email}, the only comparison key of the address; null while it has none. */
    private String emailKey;

    /** E.164 value of {@link #username}, the only comparison key of the phone; null while it has none. */
    private String phoneKey;

    /** Raised by every change of a credential or login identifier; proofs and sessions of an older value are void. */
    private long credentialVersion;

    /** Consecutive wrong passwords at a reauthentication; apart from the login counter (auth-email-access D4-12). */
    private int reauthFailedAttempts;

    /** Reauthentication is refused until this instant; null when not locked. */
    private Instant reauthLockedUntil;

    private AppUser(UserId id, String fullName, Username username, Email email, Password password,
                    ProfileImage profileImage, Instant createdDate, Set<String> userRoles, UserStatus userStatus) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.password = password;
        this.profileImage = profileImage;
        this.createdDate = createdDate;
        this.lastUpdate = createdDate;
        this.userRoles = userRoles;
        this.userStatus = userStatus;
    }

    /*────────── FACTORY ──────────*/

    /**
     * A newly registered account: {@link UserStatus#PENDING} until the phone
     * number is verified (see {@link #verifyPhone}), KYC not started.
     *
     * @param phone        the number, in national form for display and as its canonical key
     * @param idCardNumber optional, may be null
     * @param userRoles    mutable set of role names
     * @param nowUtc       registration time; the current time when null
     */
    public static AppUser createWithPhone(UserId id, String fullName, PhoneNumber phone, Password hashedPassword,
                                          ProfileImage profileImage, IdCardNumber idCardNumber, Set<String> userRoles,
                                          Instant nowUtc) {
        Instant now = nowUtc != null ? nowUtc : AppClock.currentUTC();
        AppUser appUser = new AppUser(id, fullName, Username.from(phone.national()), null, hashedPassword, profileImage,
                now, userRoles, UserStatus.PENDING);
        appUser.phoneKey = phone.key();
        appUser.idCardNumber = idCardNumber;
        appUser.kycStatus = KycStatus.UNVERIFIED;
        return appUser;
    }

    /**
     * An account whose mailbox was proven before it existed (auth-email-access 4.4): ACTIVE and verified from the
     * start, with no phone and no fake value in its place.
     *
     * @param email     the canonical address, from {@link Email#create}
     * @param userRoles mutable set of role names
     */
    public static AppUser registerWithEmail(UserId id, String fullName, Email email, Password hashedPassword,
                                            Set<String> userRoles, Instant now) {
        AppUser appUser = new AppUser(id, fullName, null, email, hashedPassword, null, now, userRoles, UserStatus.ACTIVE);
        appUser.emailKey = email.getValue();
        appUser.emailVerifiedAt = now;
        appUser.kycStatus = KycStatus.UNVERIFIED;
        return appUser;
    }

    /*────────── BUSINESS BEHAVIOURS ──────────*/

    /** The stored phone, or null for an account without one. */
    public String usernameValue() {
        return username != null ? username.getValue() : null;
    }

    /**
     * Whether phone sign-in and SMS reset may serve this account (auth-email-access D4-9): a verified email with an
     * unverified phone says nothing about who holds that number, so it may not.
     */
    public boolean canUsePhone() {
        return username != null && (!isEmailVerified() || isPhoneVerified());
    }

    /**
     * The user proved control of the phone number. A PENDING account becomes
     * ACTIVE; other statuses (e.g. LOCK) are left alone.
     */
    public void verifyPhone(Instant now) {
        if (phoneVerifiedAt == null) {
            phoneVerifiedAt = now;
        }
        if (userStatus == UserStatus.PENDING) {
            userStatus = UserStatus.ACTIVE;
        }
        touch();
    }

    /**
     * The user proved a mailbox and re-entered the password: the only way an existing account gets a verified email
     * (auth-email-access D4-4). The id, the roles and every reference to the account stay; a PENDING account becomes
     * ACTIVE, other statuses are left alone; proofs and access tokens of the older version die.
     *
     * @param newEmail the canonical address, from {@link Email#create}
     */
    public void bindVerifiedEmail(Email newEmail, Instant now) {
        this.email = Objects.requireNonNull(newEmail);
        this.emailKey = newEmail.getValue();
        this.emailVerifiedAt = now;
        if (userStatus == UserStatus.PENDING) {
            userStatus = UserStatus.ACTIVE;
        }
        this.credentialVersion++;
        touch();
    }

    /**
     * An email sign-in or reset of a PENDING account with a verified email: it becomes ACTIVE, nothing else changes
     * (auth-email-access D4-10).
     *
     * @return true if the status changed
     */
    public boolean activateIfPendingWithVerifiedEmail() {
        if (userStatus != UserStatus.PENDING || !isEmailVerified()) {
            return false;
        }
        userStatus = UserStatus.ACTIVE;
        return true;
    }

    public boolean isPhoneVerified() {
        return phoneVerifiedAt != null;
    }

    /** A stored address without a key does not count: it can be neither signed in with nor reset by (auth-email-access 4.1). */
    public boolean isEmailVerified() {
        return emailKey != null && emailVerifiedAt != null;
    }

    /** Replaces the password hash (change or reset); proofs and sessions of the old one are void. */
    public void changePassword(String newHashedPassword) {
        this.password = Password.create(newHashedPassword);
        this.credentialVersion++;
        touch();
    }

    /**
     * Grants a role at self-registration: only {@link RoleName#SELF_REGISTRATION} (auth-roles BR-3).
     *
     * @throws IllegalArgumentException for an unknown role or any other role
     */
    public void assignRole(String role) {
        RoleName roleName = RoleName.valueOf(role);
        if (!RoleName.SELF_REGISTRATION.contains(roleName)) {
            throw new IllegalArgumentException("Cannot register with this role.");
        }
        this.userRoles.add(role);
    }

    /**
     * Adds a role granted by staff or by the system; who may grant what is checked by the caller.
     *
     * @return true if the role was added, false if the account already held it
     * @throws IllegalArgumentException for SUPER_ADMIN, which the application never grants
     */
    public boolean grantRole(RoleName role) {
        if (role == RoleName.SUPER_ADMIN) {
            throw new IllegalArgumentException("SUPER_ADMIN is never granted.");
        }
        boolean added = this.userRoles.add(role.name());
        if (added) {
            touch();
        }
        return added;
    }

    /**
     * Removes a role; the account may end with no roles.
     *
     * @return true if the role was removed, false if the account did not hold it
     */
    public boolean revokeRole(RoleName role) {
        boolean removed = this.userRoles.remove(role.name());
        if (removed) {
            touch();
        }
        return removed;
    }

    /** Whether the account holds a staff or admin role ({@link RoleName#STAFF}). */
    public boolean isStaff() {
        return RoleName.STAFF.stream().anyMatch(role -> userRoles.contains(role.name()));
    }

    /**
     * True while password login is refused after too many wrong passwords.
     * Unlike {@link UserStatus#LOCK} (set by an administrator), this lock lifts
     * by itself and does not end sessions that are already signed in.
     */
    public boolean isLoginTemporarilyLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Forgets failed attempts and lifts a temporary lock, e.g. after the owner reset the password. */
    public void clearLoginLockout() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Forgets wrong passwords at a reauthentication and lifts its lock, e.g. after the owner reset the password. */
    public void clearReauthLockout() {
        this.reauthFailedAttempts = 0;
        this.reauthLockedUntil = null;
    }

    /**
     * Statuses that stop an account from signing in or being issued credentials. Shared with the staff queries
     * (e.g. the owner lead assignee list), so a list and {@link #canAuthenticate()} cannot disagree.
     * An {@code EnumSet} view: {@code contains(null)} is false, as the old comparison chain was.
     */
    public static final Set<UserStatus> SIGN_IN_BLOCKED_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(UserStatus.LOCK, UserStatus.TERMINATED, UserStatus.DELETED));

    /**
     * Whether this account may sign in or be issued credentials.
     * A locked, terminated or deleted account may not; any other status may.
     */
    public boolean canAuthenticate() {
        return !SIGN_IN_BLOCKED_STATUSES.contains(userStatus);
    }

    /** Soft-deletes the account. */
    public void deactivate() {
        this.userStatus = UserStatus.DELETED;
        this.deletedAt = AppClock.currentUTC();
        touch();
    }

    /*────────── INTERNAL ──────────*/

    private void touch() {
        this.lastUpdate = AppClock.currentUTC();
    }

}
