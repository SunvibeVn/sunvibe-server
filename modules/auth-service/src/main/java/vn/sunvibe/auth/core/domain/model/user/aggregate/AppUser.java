package vn.sunvibe.auth.core.domain.model.user.aggregate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    private Instant createdDate;

    private Instant lastUpdate;

    private Instant lastLogin;

    private Set<String> userRoles;

    private UserStatus userStatus;

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
    public static AppUser create(UserId id, String fullName, Username username, Email email, Password hashedPassword, ProfileImage profileImage, Set<String> userRoles, Instant nowUtc) {
        return new AppUser(id, fullName, username, email, hashedPassword, profileImage, nowUtc != null ? nowUtc : AppClock.currentUTC(), nowUtc != null ? nowUtc : AppClock.currentUTC(), userRoles, UserStatus.ACTIVE);
    }

    // Build from database
    public static AppUser from(UserId id, String fullName, Username username, Email email, Password hashedPassword, ProfileImage profileImage, Set<String> userRoles, Instant nowUtc) {

        return new AppUser(id, fullName, username, email, hashedPassword, profileImage, nowUtc != null ? nowUtc : AppClock.currentUTC(), nowUtc != null ? nowUtc : AppClock.currentUTC(), userRoles, UserStatus.ACTIVE);
    }

    /*────────── BUSINESS BEHAVIOURS ──────────*/

    public void changeEmail(Email newEmail) {
        this.email = Objects.requireNonNull(newEmail);
        touch();
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

    public void recordLoginSuccess(Instant when) {
        this.lastLogin = when != null ? when : AppClock.currentUTC();
    }

    public void deactivate() {
        this.userStatus = UserStatus.DELETED;
        touch();
    }

    /*────────── INTERNAL ──────────*/

    private void touch() {
        this.lastUpdate = AppClock.currentUTC();
    }

}