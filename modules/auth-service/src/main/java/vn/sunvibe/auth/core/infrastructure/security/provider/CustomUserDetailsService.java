package vn.sunvibe.auth.core.infrastructure.security.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;
import vn.sunvibe.auth.core.shared.constant.AuthAuthorities;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads accounts for Spring Security: the password check at login
 * ({@code DaoAuthenticationProvider}) and the per-request reload in the JWT filter.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserDomainRepository userDomainRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser appUser;
        try {
            appUser = userDomainRepository.findByUsername(username);
        } catch (UserNotFoundException e) {
            // Spring's contract: unknown user -> UsernameNotFoundException, which
            // DaoAuthenticationProvider turns into a plain bad-credentials failure.
            throw new UsernameNotFoundException(e.getMessage(), e);
        }

        CustomUserDetails customUserDetails = new CustomUserDetails();
        customUserDetails.setUserId(appUser.getId().getValue());
        customUserDetails.setUsername(appUser.getUsername().getValue());
        customUserDetails.setEmail(appUser.getEmail() != null ? appUser.getEmail().getValue() : null);
        customUserDetails.setPassword(appUser.getPassword().getHashedValue());
        customUserDetails.setAuthorities(authoritiesOf(appUser));
        customUserDetails.setUserStatus(appUser.getUserStatus());
        return customUserDetails;
    }

    /**
     * The user's roles as {@code ROLE_<name>}, plus the verified contacts as
     * authorities so any module can require them:
     * {@code @PreAuthorize("hasAuthority('PHONE_VERIFIED')")}.
     */
    static Set<SimpleGrantedAuthority> authoritiesOf(AppUser appUser) {
        Set<SimpleGrantedAuthority> authorities = appUser.getUserRoles() == null ? new HashSet<>()
                : appUser.getUserRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toCollection(HashSet::new));
        if (appUser.isPhoneVerified()) {
            authorities.add(new SimpleGrantedAuthority(AuthAuthorities.PHONE_VERIFIED));
        }
        if (appUser.isEmailVerified()) {
            authorities.add(new SimpleGrantedAuthority(AuthAuthorities.EMAIL_VERIFIED));
        }
        return authorities;
    }

}
