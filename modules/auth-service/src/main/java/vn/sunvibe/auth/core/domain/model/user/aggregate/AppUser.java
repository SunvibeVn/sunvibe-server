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
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate root of a user account: identity, credentials, contact
 * verification, login lockout and lifecycle status.
 * <p>
 * New accounts are built with {@link #create}. The no-args constructor and the
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

    /** The phone number the user signs in with. */
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
     * @param email        optional, may be null
     * @param idCardNumber optional, may be null
     * @param userRoles    mutable set of role names
     * @param nowUtc       registration time; the current time when null
     */
    public static AppUser create(UserId id, String fullName, Username username, Email email, Password hashedPassword,
                                 ProfileImage profileImage, IdCardNumber idCardNumber, Set<String> userRoles, Instant nowUtc) {
        Instant now = nowUtc != null ? nowUtc : AppClock.currentUTC();
        AppUser appUser = new AppUser(id, fullName, username, email, hashedPassword, profileImage, now, userRoles,
                UserStatus.PENDING);
        appUser.idCardNumber = idCardNumber;
        appUser.kycStatus = KycStatus.UNVERIFIED;
        return appUser;
    }

    /*────────── BUSINESS BEHAVIOURS ──────────*/

    /** A new e-mail is unverified until the user confirms the code sent to it. */
    public void changeEmail(Email newEmail) {
        this.email = Objects.requireNonNull(newEmail);
        this.emailVerifiedAt = null;
        touch();
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
     * The user proved control of the current e-mail.
     *
     * @throws IllegalStateException if the account has no e-mail
     */
    public void verifyEmail(Instant now) {
        if (email == null) {
            throw new IllegalStateException("No e-mail to verify.");
        }
        emailVerifiedAt = now;
        touch();
    }

    public boolean isPhoneVerified() {
        return phoneVerifiedAt != null;
    }

    public boolean isEmailVerified() {
        return email != null && emailVerifiedAt != null;
    }

    /** Replaces the password hash (change or reset). */
    public void changePassword(String newHashedPassword) {
        this.password = Password.create(newHashedPassword);
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

    /** A successful password login: records it and forgets earlier failed attempts. */
    public void recordLoginSuccess(Instant when) {
        this.lastLogin = when != null ? when : AppClock.currentUTC();
        clearLoginLockout();
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

    /**
     * Whether this account may sign in or be issued credentials.
     * A locked, terminated or deleted account may not; any other status may.
     */
    public boolean canAuthenticate() {
        return userStatus != UserStatus.LOCK
                && userStatus != UserStatus.TERMINATED
                && userStatus != UserStatus.DELETED;
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
