package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
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
            if (user.getEmail() != null) {
                emails.add(user.getEmail().getValue());
            }
            if (user.getIdCardNumber() != null) {
                idCardNumbers.add(user.getIdCardNumber());
            }
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

        @Override
        public Optional<AppUser> findById(UserId userId) {
            throw new UnsupportedOperationException();
        }
    }

    private FakeUserDomainRepository repository;
    private UserRegisterService service;
    private RecordingAuditLog audit;

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
        audit = new RecordingAuditLog();
        service = new UserRegisterService(repository, passwordEncoder, ulidGenerator, audit);
    }

    private UserRegisterCommand command(String username, String email, String idCardNumber) {
        return UserRegisterCommand.builder()
                .fullName("Nguyen Van A")
                .username(username)
                .email(email)
                .password("P@ssw0rd!")
                .idCardNumber(idCardNumber)
                .build();
    }

    @Test
    void execute_registersWithOnlyFullNameUsernameAndPassword() {
        service.execute(command("0912345678", null, null));

        AppUser saved = repository.findByUsername("0912345678");
        assertNotNull(saved);
        assertNull(saved.getEmail());
        assertNull(saved.getIdCardNumber());
        assertEquals(KycStatus.UNVERIFIED, saved.getKycStatus());
    }

    @Test
    void execute_alwaysAssignsTenantRole() {
        service.execute(command("0912345678", null, null));

        assertEquals(Set.of(RoleName.TENANT.name()), repository.findByUsername("0912345678").getUserRoles());
    }

    @Test
    void execute_treatsBlankEmailAndIdCardNumberAsAbsent() {
        service.execute(command("0912345678", "  ", ""));

        AppUser saved = repository.findByUsername("0912345678");
        assertNull(saved.getEmail());
        assertNull(saved.getIdCardNumber());
    }

    @Test
    void execute_allowsSeveralUsersWithoutIdCardNumber() {
        service.execute(command("0912345678", null, null));
        service.execute(command("0987654321", null, null));

        assertNotNull(repository.findByUsername("0987654321"));
    }

    @Test
    void execute_storesOptionalEmailAndIdCardNumberWhenProvided() {
        service.execute(command("0912345678", "A@Example.com", "123456789012"));

        AppUser saved = repository.findByUsername("0912345678");
        assertEquals("a@example.com", saved.getEmail().getValue());
        assertEquals("123456789012", saved.getIdCardNumber().getValue());
    }

    @Test
    void execute_rejectsDuplicateEmail() {
        service.execute(command("0912345678", "a@example.com", null));

        AuthValidationException exception = assertThrows(AuthValidationException.class,
                () -> service.execute(command("0987654321", "a@example.com", null)));
        assertEquals(AuthFieldName.EMAIL, exception.getField());
    }

    @Test
    void execute_rejectsInvalidEmailFormat() {
        AuthValidationException exception = assertThrows(AuthValidationException.class,
                () -> service.execute(command("0912345678", "not-an-email", null)));
        assertEquals(AuthFieldName.EMAIL, exception.getField());
    }

    @Test
    void execute_rejectsDuplicateIdCardNumber() {
        service.execute(command("0912345678", null, "123456789012"));

        AuthValidationException exception = assertThrows(AuthValidationException.class,
                () -> service.execute(command("0987654321", null, "123456789012")));
        assertEquals(AuthFieldName.ID_CARD_NUMBER, exception.getField());
    }

    @Test
    void execute_rejectsInvalidIdCardNumberFormat() {
        AuthValidationException exception = assertThrows(AuthValidationException.class,
                () -> service.execute(command("0912345678", null, "not-a-number")));
        assertEquals(AuthFieldName.ID_CARD_NUMBER, exception.getField());
    }
}
