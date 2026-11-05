package vn.sunvibe.auth.core.infrastructure.security.userdetails;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.sunvibe.auth.core.application.shared.VerifiedCredentials;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;

import java.util.Collection;

/**
 * Spring Security view of an account. Locked ({@link UserStatus#LOCK}) and
 * disabled (terminated or deleted) accounts are refused by the authentication
 * provider and by the JWT filter. Its name for Spring is the user id; the phone
 * is only there so the filter can compare the subject of an older token.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomUserDetails implements UserDetails, VerifiedCredentials {

    private String userId;
    /** The stored username; null for an account without a phone. */
    private String phone;
    private String password;
    private String email;
    private Collection<? extends GrantedAuthority> authorities;
    private UserStatus userStatus;
    private long credentialVersion;

    @Override
    public String getUsername() {
        return userId;
    }

    @Override
    public String userId() {
        return userId;
    }

    @Override
    public long credentialVersion() {
        return credentialVersion;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return userStatus != UserStatus.LOCK;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return userStatus != UserStatus.DELETED && userStatus != UserStatus.TERMINATED;
    }

}
