package vn.sunvibe.auth.core.infrastructure.security.provider;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import vn.sunvibe.auth.core.application.usecase.user.LoginAccountFinder;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;
import vn.sunvibe.auth.core.shared.constant.AuthAuthorities;
import vn.sunvibe.auth.core.testsupport.UnsupportedUserRepository;

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

    private static final String PHONE_LOGIN_NAME = "phone:+84912345678";

    private static CustomUserDetailsService serviceOver(UserDomainRepository repository) {
        return new CustomUserDetailsService(repository, new LoginAccountFinder(repository, AuthTestUsers.phoneOn()));
    }

    /** Hand-written fake — Mockito isn't on this module's classpath. */
    private static class NoUsersRepository extends UnsupportedUserRepository {
        @Override
        public java.util.Optional<AppUser> findByPhoneKey(String phoneKey) {
            return java.util.Optional.empty();
        }
    }

    @Test
    void loadUserByUsername_userWithoutEmail_hasNullEmail() {
        AppUser userWithoutEmail = AuthTestUsers.withPhone(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                "0912345678",
                null,
                Password.create("hashed-password"),
                ProfileImage.create(null),
                null,
                new HashSet<>(Set.of("TENANT")),
                Instant.now());
        NoUsersRepository repository = new NoUsersRepository() {
            @Override
            public java.util.Optional<AppUser> findByPhoneKey(String phoneKey) {
                return java.util.Optional.of(userWithoutEmail);
            }
        };

        CustomUserDetails details = (CustomUserDetails) serviceOver(repository).loadUserByUsername(PHONE_LOGIN_NAME);

        assertNull(details.getEmail());
        assertEquals("01ARZ3NDEKTSV4RRFFQ69G5FAV", details.getUsername(), "the name of the details is the user id");
        assertEquals("0912345678", details.getPhone());
    }

    /** auth-email-access PD-1: only a typed login name reaches a lookup. */
    @Test
    void loadUserByUsername_nameWithoutType_notFoundWithoutLookup() {
        CustomUserDetailsService service = serviceOver(new NoUsersRepository() {
            @Override
            public java.util.Optional<AppUser> findByPhoneKey(String phoneKey) {
                throw new AssertionError("no lookup for an untyped name");
            }
        });

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("0912345678"));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("other:0912345678"));
    }

    /** auth-email-access spec 5.5: the provider must not stop a locked account before its hash. */
    @Test
    void loadUserByUsername_lockedAccount_reportsNoStatus_loadUserByIdKeepsTheStrictFlags() {
        AppUser locked = AppUser.registerWithEmail(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A",
                Email.create("a@example.com"), Password.create("hashed"), new HashSet<>(Set.of("TENANT")), Instant.now());
        locked.setUserStatus(vn.sunvibe.auth.core.domain.model.user.enums.UserStatus.LOCK);
        CustomUserDetailsService service = serviceOver(new NoUsersRepository() {
            @Override
            public java.util.Optional<AppUser> findByEmailKey(String emailKey) {
                return java.util.Optional.of(locked);
            }

            @Override
            public java.util.Optional<AppUser> findById(UserId userId) {
                return java.util.Optional.of(locked);
            }
        });

        CustomUserDetails forPasswordCheck = (CustomUserDetails) service.loadUserByUsername("email:a@example.com");

        assertTrue(forPasswordCheck.isAccountNonLocked());
        assertTrue(forPasswordCheck.isEnabled());
        assertFalse(service.loadUserById("01ARZ3NDEKTSV4RRFFQ69G5FAV").isAccountNonLocked());
    }

    @Test
    void loadUserById_accountWithoutPhone_isLoaded() {
        AppUser emailOnly = AppUser.registerWithEmail(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A",
                Email.create("a@example.com"), Password.create("hashed"), new HashSet<>(Set.of("TENANT")), Instant.now());
        emailOnly.setCredentialVersion(2);
        CustomUserDetailsService service = serviceOver(new NoUsersRepository() {
            @Override
            public java.util.Optional<AppUser> findById(UserId userId) {
                return java.util.Optional.of(emailOnly);
            }
        });

        CustomUserDetails details = service.loadUserById("01ARZ3NDEKTSV4RRFFQ69G5FAV");

        assertNull(details.getPhone());
        assertEquals(2L, details.credentialVersion());
        assertEquals("01ARZ3NDEKTSV4RRFFQ69G5FAV", details.userId());
    }

    @Test
    void loadUserById_unknownId_throwsUsernameNotFound() {
        CustomUserDetailsService service = serviceOver(new NoUsersRepository() {
            @Override
            public java.util.Optional<AppUser> findById(UserId userId) {
                return java.util.Optional.empty();
            }
        });

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserById("01ARZ3NDEKTSV4RRFFQ69G5FAV"));
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFound() {
        CustomUserDetailsService service = serviceOver(new NoUsersRepository());

        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername(PHONE_LOGIN_NAME));

        assertFalse(exception.getMessage().contains("0912345678"), "the message quotes no identifier");
    }

    @Test
    void loadUserByUsername_addsAuthoritiesForVerifiedContacts() {
        AppUser user = AuthTestUsers.withPhone(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"), "Nguyen Van A",
                "0912345678", Email.create("a@example.com"), Password.create("hashed"),
                ProfileImage.create(null), null, new HashSet<>(Set.of("TENANT")), Instant.now());
        NoUsersRepository repository = new NoUsersRepository() {
            @Override
            public java.util.Optional<AppUser> findByPhoneKey(String phoneKey) {
                return java.util.Optional.of(user);
            }
        };
        CustomUserDetailsService service = serviceOver(repository);

        assertEquals(Set.of("ROLE_TENANT"), authorities(service));

        user.verifyPhone(Instant.now());
        user.bindVerifiedEmail(Email.create("a@example.com"), Instant.now());

        assertEquals(Set.of("ROLE_TENANT", AuthAuthorities.PHONE_VERIFIED, AuthAuthorities.EMAIL_VERIFIED), authorities(service));
    }

    private static Set<String> authorities(CustomUserDetailsService service) {
        return service.loadUserByUsername(PHONE_LOGIN_NAME).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
    }
}
