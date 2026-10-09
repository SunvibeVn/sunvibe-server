package vn.sunvibe.auth.core.infrastructure.security.provider;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

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
        public boolean existsByIdCardNumberHash(String idCardNumberHash) {
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
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFound() {
        CustomUserDetailsService service = new CustomUserDetailsService(new NoUsersRepository());

        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("0912345678"));

        assertEquals(MESSAGE, exception.getMessage());
    }
}
