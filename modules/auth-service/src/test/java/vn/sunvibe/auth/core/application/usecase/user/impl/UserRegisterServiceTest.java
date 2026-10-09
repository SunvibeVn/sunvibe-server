package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.common.generator.id.IdGenerator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserRegisterServiceTest {

    /**
     * Minimal in-memory fake — Mockito isn't on this module's classpath.
     * Remembers the {@link IdCardNumber} of every saved user, so a later
     * {@code existsByIdCardNumber} lookup for an equal value matches. (The
     * real adapter's hashing is covered by {@code UserRepositoryAdapterEncryptionTest}.)
     */
    private static class FakeUserDomainRepository implements UserDomainRepository {
        private final Set<String> usernames = new HashSet<>();
        private final Set<String> emails = new HashSet<>();
        private final Set<IdCardNumber> idCardNumbers = new HashSet<>();
        private final Map<String, AppUser> saved = new HashMap<>();

        @Override
        public boolean existsByUsername(String userName) {
            return usernames.contains(userName);
        }

        @Override
        public boolean existsByEmail(String email) {
            return emails.contains(email);
        }

        @Override
        public boolean existsByIdCardNumber(IdCardNumber idCardNumber) {
            return idCardNumbers.contains(idCardNumber);
        }

        @Override
        public AppUser save(AppUser user) {
            usernames.add(user.getUsername().getValue());
            emails.add(user.getEmail().getValue());
            idCardNumbers.add(user.getIdCardNumber());
            saved.put(user.getUsername().getValue(), user);
            return user;
        }

        @Override
        public AppUser findByUsername(String username) {
            return saved.get(username);
        }

        @Override
        public AppUser findByEmail(String email) {
            throw new UnsupportedOperationException();
        }
    }

    private FakeUserDomainRepository repository;
    private UserRegisterService service;

    @BeforeEach
    void setUp() {
        repository = new FakeUserDomainRepository();
        PasswordEncoder passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "hashed:" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encode(rawPassword).equals(encodedPassword);
            }
        };
        IdGenerator<String> ulidGenerator = () -> "01ARZ3NDEKTSV4RRFFQ69G5FAV";
        service = new UserRegisterService(repository, passwordEncoder, ulidGenerator);
    }

    private UserRegisterCommand command(String username, String email, String idCardNumber) {
        return UserRegisterCommand.builder()
                .fullName("Nguyen Van A")
                .username(username)
                .email(email)
                .password("P@ssw0rd!")
                .idCardNumber(idCardNumber)
                .roles(null)
                .build();
    }

    @Test
    void execute_registersUserWithRequiredIdCardNumber() {
        service.execute(command("0912345678", "a@example.com", "123456789012"), "fingerprint");

        AppUser saved = repository.findByUsername("0912345678");
        assertNotNull(saved);
        assertEquals("123456789012", saved.getIdCardNumber().getValue());
    }

    @Test
    void execute_rejectsDuplicateIdCardNumber() {
        service.execute(command("0912345678", "a@example.com", "123456789012"), "fingerprint");

        AuthValidationException exception = assertThrows(AuthValidationException.class,
                () -> service.execute(command("0987654321", "b@example.com", "123456789012"), "fingerprint"));
        assertEquals(AuthFieldName.ID_CARD_NUMBER, exception.getField());
    }

    @Test
    void execute_rejectsInvalidIdCardNumberFormat() {
        assertThrows(AuthValidationException.class,
                () -> service.execute(command("0912345678", "a@example.com", "not-a-number"), "fingerprint"));
    }

    @Test
    void execute_rejectsNullIdCardNumberAsFieldError() {
        AuthValidationException exception = assertThrows(AuthValidationException.class,
                () -> service.execute(command("0912345678", "a@example.com", null), "fingerprint"));
        assertEquals(AuthFieldName.ID_CARD_NUMBER, exception.getField());
    }
}
