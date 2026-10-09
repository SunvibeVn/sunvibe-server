package vn.sunvibe.auth.core.infrastructure.security.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserDomainRepository userDomainRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
//        log.info("CustomUserDetailsService.loadUserByUsername--001");
        System.out.println("CustomUserDetailsService.loadUserByUsername");
        AppUser appUser;
        try {
            appUser = userDomainRepository.findByUsername(username);
        } catch (UserNotFoundException e) {
            // Spring's contract: unknown user -> UsernameNotFoundException, which
            // DaoAuthenticationProvider turns into a plain bad-credentials failure.
            throw new UsernameNotFoundException(e.getMessage(), e);
        }
        Set<SimpleGrantedAuthority> authorities = appUser.getUserRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).collect(Collectors.toSet());

//        log.info("CustomUserDetailsService.loadUserByUsername--002");
        return mapUserToCustomUserDetails(appUser, authorities);
    }

    private CustomUserDetails mapUserToCustomUserDetails(AppUser user, Set<SimpleGrantedAuthority> authorities) {
        CustomUserDetails customUserDetails = new CustomUserDetails();
        customUserDetails.setUserId(user.getId().getValue());
        customUserDetails.setUsername(user.getUsername().getValue());
        customUserDetails.setEmail(user.getEmail() != null ? user.getEmail().getValue() : null);
        customUserDetails.setPassword(user.getPassword().getHashedValue());
        customUserDetails.setAuthorities(authorities);
        customUserDetails.setUserStatus(user.getUserStatus());

//        log.info("CustomUserDetailsService.mapUserToCustomUserDetails");
        return customUserDetails;
    }

}