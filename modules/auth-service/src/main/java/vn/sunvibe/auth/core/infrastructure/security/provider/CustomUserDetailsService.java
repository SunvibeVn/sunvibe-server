package vn.sunvibe.auth.core.infrastructure.security.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.service.user.LoginAccountFinder;
import vn.sunvibe.auth.core.application.usecase.user.LoginIdentifier;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;
import vn.sunvibe.auth.core.shared.constant.AuthAuthorities;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads accounts for Spring Security: the password check at login
 * ({@code DaoAuthenticationProvider}, by a typed login name) and the per-request
 * reload in the JWT filter (by user id).
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /** Spring's contract: unknown user -> this exception, turned into a plain bad-credentials failure. */
    private static final String NOT_FOUND = "Account not found.";

    private final UserDomainRepository userDomainRepository;
    private final LoginAccountFinder loginAccounts;

    /**
     * Loads by the name of {@link LoginIdentifier#authenticationName()}. Anything else is an unknown user, so
     * {@code DaoAuthenticationProvider} keeps its timing mitigation.
     */
    @Override
    public UserDetails loadUserByUsername(String loginName) {
        return LoginIdentifier.parse(loginName)
                .flatMap(loginAccounts::find)
                .map(CustomUserDetailsService::forPasswordCheck)
                .orElseThrow(() -> new UsernameNotFoundException(NOT_FOUND));
    }

    /**
     * Without a status, so the details report enabled and not locked: the provider then hashes the password for a locked
     * account too, and the login decides the status after it, so such an account answers no faster (auth-email-access
     * spec 5.5). {@link #loadUserById} keeps the strict flags.
     */
    private static CustomUserDetails forPasswordCheck(AppUser appUser) {
        CustomUserDetails details = toDetails(appUser);
        details.setUserStatus(null);
        return details;
    }

    /**
     * The account of a signed-in request.
     *
     * @throws UsernameNotFoundException if it does not exist or is soft-deleted
     */
    public CustomUserDetails loadUserById(String userId) {
        return userDomainRepository.findById(UserId.from(userId))
                .map(CustomUserDetailsService::toDetails)
                .orElseThrow(() -> new UsernameNotFoundException(NOT_FOUND));
    }

    private static CustomUserDetails toDetails(AppUser appUser) {
        CustomUserDetails customUserDetails = new CustomUserDetails();
        customUserDetails.setUserId(appUser.getId().getValue());
        customUserDetails.setPhone(appUser.usernameValue());
        customUserDetails.setEmail(appUser.getEmail() != null ? appUser.getEmail().getValue() : null);
        customUserDetails.setPassword(appUser.getPassword().getHashedValue());
        customUserDetails.setAuthorities(authoritiesOf(appUser));
        customUserDetails.setUserStatus(appUser.getUserStatus());
        customUserDetails.setCredentialVersion(appUser.getCredentialVersion());
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
