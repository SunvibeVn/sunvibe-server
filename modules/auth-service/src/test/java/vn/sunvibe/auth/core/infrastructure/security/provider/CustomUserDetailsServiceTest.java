package vn.sunvibe.auth.core.infrastructure.security.provider;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;
import vn.sunvibe.auth.core.shared.constant.AuthAuthorities;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An unknown (or soft-deleted, hence invisible) username must surface as
 * Spring's {@link UsernameNotFoundException} so login answers with a normal
 * bad-credentials response instead of an internal authentication error.
 */
class CustomUserDetailsServiceTest {

    private static final String MESSAGE = "User not found with username: 0912345678";

    /** Hand-written fake — Mockito isn't on this module's classpath. */
    private static class NoUsersRepository implements UserDomainRepository {
        @Override
        public boolean existsByUsername(String userName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByEmail(String email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByIdCardNumber(IdCardNumber idCardNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AppUser save(AppUser user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AppUser findByUsername(String username) {
            throw new UserNotFoundException(MESSAGE);
        }

        @Override
        public AppUser findByEmail(String email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<AppUser> findById(vn.sunvibe.auth.core.domain.model.user.vo.UserId userId) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void loadUserByUsername_userWithoutEmail_hasNullEmail() {
        AppUser userWithoutEmail = AppUser.create(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                Username.create("0912345678"),
                null,
                Password.create("hashed-password"),
                ProfileImage.create(null),
                null,
                new HashSet<>(Set.of("TENANT")),
                Instant.now());
        NoUsersRepository repository = new NoUsersRepository() {
            @Override
            public AppUser findByUsername(String username) {
                return userWithoutEmail;
            }
        };

        CustomUserDetails details = (CustomUserDetails) new CustomUserDetailsService(repository).loadUserByUsername("0912345678");

        assertNull(details.getEmail());
        assertEquals("0912345678", details.getUsername());
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFound() {
        CustomUserDetailsService service = new CustomUserDetailsService(new NoUsersRepository());

        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("0912345678"));

        assertEquals(MESSAGE, exception.getMessage());
    }

    @Test
    void loadUserByUsername_addsAuthoritiesForVerifiedContacts() {
        AppUser user = AppUser.create(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A",
                Username.create("0912345678"), Email.create("a@example.com"), Password.create("hashed"),
                ProfileImage.create(null), null, new HashSet<>(Set.of("TENANT")), Instant.now());
        NoUsersRepository repository = new NoUsersRepository() {
            @Override
            public AppUser findByUsername(String username) {
                return user;
            }
        };
        CustomUserDetailsService service = new CustomUserDetailsService(repository);

        assertEquals(Set.of("ROLE_TENANT"), authorities(service));

        user.verifyPhone(Instant.now());
        user.verifyEmail(Instant.now());

        assertEquals(Set.of("ROLE_TENANT", AuthAuthorities.PHONE_VERIFIED, AuthAuthorities.EMAIL_VERIFIED), authorities(service));
    }

    private static Set<String> authorities(CustomUserDetailsService service) {
        return service.loadUserByUsername("0912345678").getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
    }
}
