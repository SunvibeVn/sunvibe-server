package vn.sunvibe.auth.core.domain.model.user.aggregate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.common.time.AppClock;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AppUser {

    private UserId id;

    private String fullName;

    private Username username;

    private Email email;

    private Password password;

    private ProfileImage profileImage;

    private IdCardNumber idCardNumber;

    private String idCardFrontUrl;

    private String idCardBackUrl;

    private KycStatus kycStatus;

    private Instant createdDate;

    private Instant lastUpdate;

    private Instant lastLogin;

    private Instant deletedAt;

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

    public AppUser(String fullName, Username username, Email email, Password password, ProfileImage profileImage, Instant createdDate, Instant lastUpdate, Set<String> userRoles, UserStatus status) {

        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.profileImage = profileImage;
        this.password = password;
        this.createdDate = createdDate;
        this.lastUpdate = lastUpdate;
        this.userRoles = userRoles;
        this.userStatus = status;
    }

    /*────────── CONSTRUCTOR ──────────*/

    public AppUser(UserId id, String fullName, Username username, Email email, Password password, ProfileImage profileImage, Instant createdDate, Instant lastUpdate, Set<String> userRoles, UserStatus userStatus) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.password = password;
        this.profileImage = profileImage;
        this.createdDate = createdDate;
        this.lastUpdate = lastUpdate;
        this.userRoles = userRoles;
        this.userStatus = userStatus;
    }

    public AppUser(UserId id, String fullName, Username username, Email email, Password password, Instant createdDate, Instant lastUpdate, UserStatus userStatus) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.password = password;
        this.createdDate = createdDate;
        this.lastUpdate = lastUpdate;
        this.userStatus = userStatus;
    }

    public AppUser(String fullName, Username username, Email email, Password password, Instant createdDate, Instant lastUpdate, UserStatus userStatus) {
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.password = password;
        this.createdDate = createdDate;
        this.lastUpdate = lastUpdate;
        this.userStatus = userStatus;
    }

    /*────────── FACTORY ──────────*/

    // Build from new data
    public static AppUser create(UserId id, String fullName, Username username, Email email, Password hashedPassword,
                                  ProfileImage profileImage, IdCardNumber idCardNumber, Set<String> userRoles, Instant nowUtc) {
        Instant now = nowUtc != null ? nowUtc : AppClock.currentUTC();
        // PENDING until the phone number is verified (see verifyPhone)
        AppUser appUser = new AppUser(id, fullName, username, email, hashedPassword, profileImage, now, now, userRoles, UserStatus.PENDING);
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

    /** The user proved control of the current e-mail. */
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

    public void changePassword(String newHashedPassword) {
        this.password = Password.create(newHashedPassword);
        touch();
    }

    public void updateProfileImage(ProfileImage newImage) {
        this.profileImage = newImage;
        touch();
    }

    public void addRole(String userRole) {
        this.userRoles.add(Objects.requireNonNull(userRole));
        touch();
    }

    public void removeRole(String userRole) {
        this.userRoles.remove(userRole);
        touch();
    }

    public void assignRole(String role) {
        RoleName roleName = RoleName.valueOf(role);
        if (roleName == RoleName.ADMIN || roleName == RoleName.SUPER_ADMIN) {
            throw new IllegalArgumentException("Cannot register with this role.");
        }
        this.userRoles.add(role);
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