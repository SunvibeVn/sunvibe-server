package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.query.auth.RegistrationTokenResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.application.shared.RegistrationPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.ContactVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationPhoneVerificationUseCase;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

        @Override
        public boolean addRole(UserId userId, vn.sunvibe.auth.core.domain.model.user.enums.RoleName role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeRole(UserId userId, vn.sunvibe.auth.core.domain.model.user.enums.RoleName role) {
            throw new UnsupportedOperationException();
        }
    }

    private FakeUserDomainRepository repository;
    private UserRegisterService service;
    private RecordingAuditLog audit;
    private List<VerificationChannel> codesSent;
    private List<ClientInfo> sessionsStarted;
    private List<String> consumedTokens;
    private boolean tokenIsValid;

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
        codesSent = new ArrayList<>();
        ContactVerificationUseCase contactVerification = new ContactVerificationUseCase() {
            @Override
            public VerificationSendResult sendCode(VerificationChannel channel) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void confirm(VerificationChannel channel, String code) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void sendCodeAfterRegistration(AppUser user, VerificationChannel channel) {
                codesSent.add(channel);
            }
        };
        sessionsStarted = new ArrayList<>();
        LoginUseCase sessions = new LoginUseCase() {
            @Override
            public AuthResult execute(LoginCommand loginCommand, ClientInfo clientInfo) {
                throw new AssertionError("The first session must not go through the password login");
            }

            @Override
            public AuthResult loginInternal(AppUser appUser, ClientInfo clientInfo) {
                sessionsStarted.add(clientInfo);
                return AuthResult.of(appUser, "access-token", "refresh-token");
            }
        };
        consumedTokens = new ArrayList<>();
        tokenIsValid = true;
        RegistrationPhoneVerificationUseCase phoneVerification = new RegistrationPhoneVerificationUseCase() {
            @Override
            public VerificationSendResult sendCode(String phoneNumber) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RegistrationTokenResult verifyCode(String phoneNumber, String code) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void consumeRegistrationToken(String registrationToken, String phoneNumber) {
                if (!tokenIsValid) {
                    throw new VerificationException("invalid token");
                }
                consumedTokens.add(registrationToken + "@" + phoneNumber);
            }
        };
        // Most tests cover the older "register first, verify later" flow, which stays available behind the switch
        service = serviceWith(false, passwordEncoder, ulidGenerator, contactVerification, sessions, phoneVerification);
    }

    private PasswordEncoder encoder;
    private IdGenerator<String> ids;
    private ContactVerificationUseCase contact;
    private LoginUseCase login;
    private RegistrationPhoneVerificationUseCase phone;

    private UserRegisterService serviceWith(boolean requirePhoneVerification, PasswordEncoder passwordEncoder,
                                            IdGenerator<String> ulidGenerator, ContactVerificationUseCase contactVerification,
                                            LoginUseCase sessions, RegistrationPhoneVerificationUseCase phoneVerification) {
        encoder = passwordEncoder;
        ids = ulidGenerator;
        contact = contactVerification;
        login = sessions;
        phone = phoneVerification;
        return new UserRegisterService(repository, passwordEncoder, ulidGenerator, audit, contactVerification, sessions,
                phoneVerification, new RegistrationPolicy(requirePhoneVerification));
    }

    private UserRegisterService requiringPhoneVerification() {
        return serviceWith(true, encoder, ids, contact, login, phone);
    }

    private UserRegisterCommand commandWithToken(String username, String token) {
        return UserRegisterCommand.builder().fullName("Nguyen Van A").username(username).password("P@ssw0rd!")
                .registrationToken(token).build();
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

    /** "Register first, verify later": the account is PENDING and an SMS code goes out at once. */
    @Test
    void execute_newAccountIsPendingAndGetsAPhoneCode() {
        service.execute(command("0912345678", null, null));

        AppUser saved = repository.findByUsername("0912345678");
        assertEquals(UserStatus.PENDING, saved.getUserStatus());
        assertFalse(saved.isPhoneVerified());
        assertEquals(List.of(VerificationChannel.PHONE), codesSent);
    }

    @Test
    void execute_withEmailAlsoSendsAnEmailCode() {
        service.execute(command("0912345678", "a@example.com", null));

        assertEquals(List.of(VerificationChannel.PHONE, VerificationChannel.EMAIL), codesSent);
        assertFalse(repository.findByUsername("0912345678").isEmailVerified());
    }

    @Test
    void execute_signsTheNewUserInOnTheRegisteringDevice() {
        ClientInfo device = new ClientInfo("Mozilla/5.0", "203.0.113.7");

        AuthResult result = service.execute(UserRegisterCommand.builder().fullName("Nguyen Van A")
                .username("0912345678").password("P@ssw0rd!").clientInfo(device).build());

        assertEquals(List.of(device), sessionsStarted);
        assertEquals("0912345678", result.username());
        assertEquals("refresh-token", result.refreshToken());
    }

    @Test
    void execute_rejectedRegistrationStartsNoSession() {
        service.execute(command("0912345678", null, null));
        sessionsStarted.clear();

        assertThrows(AuthValidationException.class, () -> service.execute(command("0912345678", null, null)));
        assertTrue(sessionsStarted.isEmpty());
    }

    /** auth-roles US-1 AC-1, BR-3 (W6-3): exactly TENANT and CUSTOMER. */
    @Test
    void execute_assignsExactlyTenantAndCustomer() {
        service.execute(command("0912345678", null, null));

        assertEquals(Set.of(RoleName.TENANT.name(), RoleName.CUSTOMER.name()),
                repository.findByUsername("0912345678").getUserRoles());
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

    /** "Verify first, register later": the token proves the phone, so the account starts ACTIVE and no code is sent. */
    @Test
    void execute_withARegistrationTokenCreatesAnActiveAccountWithAVerifiedPhone() {
        AuthResult result = requiringPhoneVerification().execute(commandWithToken("0912345678", "the-token"));

        AppUser saved = repository.findByUsername("0912345678");
        assertEquals(UserStatus.ACTIVE, saved.getUserStatus());
        assertTrue(saved.isPhoneVerified());
        assertTrue(codesSent.isEmpty(), "the phone was proven already: no SMS code");
        assertEquals(List.of("the-token@0912345678"), consumedTokens);
        assertEquals("0912345678", result.username());
        assertEquals(1, sessionsStarted.size());
    }

    @Test
    void execute_withATokenAndAnEmailStillSendsTheEmailCode() {
        UserRegisterCommand command = UserRegisterCommand.builder().fullName("Nguyen Van A").username("0912345678")
                .email("a@example.com").password("P@ssw0rd!").registrationToken("the-token").build();

        requiringPhoneVerification().execute(command);

        assertEquals(List.of(VerificationChannel.EMAIL), codesSent);
    }

    @Test
    void execute_withoutATokenIsRefusedWhenPhoneVerificationIsRequired() {
        tokenIsValid = false;

        assertThrows(VerificationException.class,
                () -> requiringPhoneVerification().execute(commandWithToken("0912345678", null)));

        assertNull(repository.findByUsername("0912345678"));
        assertTrue(sessionsStarted.isEmpty());
    }

    @Test
    void execute_aRejectedTokenCreatesNoAccount() {
        tokenIsValid = false;

        assertThrows(VerificationException.class,
                () -> requiringPhoneVerification().execute(commandWithToken("0912345678", "stale-token")));

        assertNull(repository.findByUsername("0912345678"));
        assertTrue(sessionsStarted.isEmpty());
    }

    @Test
    void execute_aTokenIsHonouredEvenWhileThePhoneVerificationSwitchIsOff() {
        service.execute(commandWithToken("0912345678", "the-token"));

        assertEquals(UserStatus.ACTIVE, repository.findByUsername("0912345678").getUserStatus());
        assertEquals(List.of("the-token@0912345678"), consumedTokens);
    }

    @Test
    void execute_existingPhoneIsRefusedBeforeTheTokenIsUsed() {
        service.execute(command("0912345678", null, null));

        assertThrows(AuthValidationException.class,
                () -> requiringPhoneVerification().execute(commandWithToken("0912345678", "the-token")));

        assertTrue(consumedTokens.isEmpty());
    }
}
