package vn.sunvibe.auth.core.application.usecase.user.impl;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
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
import vn.sunvibe.shared.generator.id.IdGenerator;
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
    private static class FakeUserDomainRepository extends vn.sunvibe.auth.core.testsupport.UnsupportedUserRepository {
        private final Set<String> usernames = new HashSet<>();
        private final Set<String> phoneKeys = new HashSet<>();
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
        public boolean existsByPhoneKey(String phoneKey) {
            return phoneKeys.contains(phoneKey);
        }

        @Override
        public AppUser create(AppUser user) {
            usernames.add(user.getUsername().getValue());
            phoneKeys.add(user.getPhoneKey());
            if (user.getEmail() != null) {
                emails.add(user.getEmail().getValue());
            }
            if (user.getIdCardNumber() != null) {
                idCardNumbers.add(user.getIdCardNumber());
            }
            saved.put(user.getUsername().getValue(), user);
            return user;
        }

        AppUser findByUsername(String username) {
            return saved.get(username);
        }

    }

    private FakeUserDomainRepository repository;
    private UserRegisterService service;
    private RecordingAuditLog audit;
    private List<AppUser> codesSent;
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
            public VerificationSendResult sendCode() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void confirm(String code) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void sendCodeAfterRegistration(AppUser user) {
                codesSent.add(user);
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
                return AuthResult.of(appUser, "access-token", "refresh-token", null);
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
                    throw new VerificationException(VerificationException.Reason.REGISTRATION_TOKEN_INVALID, "invalid token");
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
                phoneVerification, new RegistrationPolicy(requirePhoneVerification), AuthTestUsers.phoneOn());
    }

    private UserRegisterService requiringPhoneVerification() {
        return serviceWith(true, encoder, ids, contact, login, phone);
    }

    private UserRegisterCommand commandWithToken(String username, String token) {
        return UserRegisterCommand.builder().fullName("Nguyen Van A").username(username).password("P@ssw0rd!")
                .registrationToken(token).build();
    }

    private UserRegisterCommand command(String username, String idCardNumber) {
        return UserRegisterCommand.builder()
                .fullName("Nguyen Van A")
                .username(username)
                .password("P@ssw0rd!")
                .idCardNumber(idCardNumber)
                .build();
    }

    @Test
    void execute_registersWithOnlyFullNameUsernameAndPassword() {
        service.execute(command("0912345678", null));

        AppUser saved = repository.findByUsername("0912345678");
        assertNotNull(saved);
        assertNull(saved.getEmail());
        assertNull(saved.getIdCardNumber());
        assertEquals(KycStatus.UNVERIFIED, saved.getKycStatus());
    }

    /** "Register first, verify later": the account is PENDING and an SMS code goes out at once. */
    @Test
    void execute_newAccountIsPendingAndGetsAPhoneCode() {
        service.execute(command("0912345678", null));

        AppUser saved = repository.findByUsername("0912345678");
        assertEquals(UserStatus.PENDING, saved.getUserStatus());
        assertFalse(saved.isPhoneVerified());
        assertEquals(1, codesSent.size());
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
        service.execute(command("0912345678", null));
        sessionsStarted.clear();

        assertThrows(AuthValidationException.class, () -> service.execute(command("0912345678", null)));
        assertTrue(sessionsStarted.isEmpty());
    }

    /** auth-roles US-1 AC-1, BR-3 (W6-3): exactly TENANT and CUSTOMER. */
    @Test
    void execute_assignsExactlyTenantAndCustomer() {
        service.execute(command("0912345678", null));

        assertEquals(Set.of(RoleName.TENANT.name(), RoleName.CUSTOMER.name()),
                repository.findByUsername("0912345678").getUserRoles());
    }

    @Test
    void execute_treatsBlankIdCardNumberAsAbsent() {
        service.execute(command("0912345678", ""));

        assertNull(repository.findByUsername("0912345678").getIdCardNumber());
    }

    @Test
    void execute_allowsSeveralUsersWithoutIdCardNumber() {
        service.execute(command("0912345678", null));
        service.execute(command("0987654321", null));

        assertNotNull(repository.findByUsername("0987654321"));
    }

    @Test
    void execute_storesOptionalIdCardNumberWhenProvided() {
        service.execute(command("0912345678", "123456789012"));

        assertEquals("123456789012", repository.findByUsername("0912345678").getIdCardNumber().getValue());
    }

    /** auth-email-access S-35: any written form of a number reaches the one key. */
    @Test
    void on_reRegisterByVariant_refused() {
        service.execute(command("0912345678", null));

        AuthValidationException refused = assertThrows(AuthValidationException.class,
                () -> service.execute(command("+84 912 345 678", null)));

        assertEquals(AuthFieldName.USER_NAME, refused.getField());
    }

    /** auth-email-access S-2: a use case called directly refuses what the DTO would have. */
    @Test
    void execute_refusedPhone_throws_noRow() {
        assertThrows(AuthValidationException.class, () -> service.execute(command("0123456789", null)));

        assertNull(repository.findByUsername("0123456789"));
    }

    @Test
    void execute_rejectsDuplicateIdCardNumber() {
        service.execute(command("0912345678", "123456789012"));

        AuthValidationException exception = assertThrows(AuthValidationException.class,
                () -> service.execute(command("0987654321", "123456789012")));
        assertEquals(AuthFieldName.ID_CARD_NUMBER, exception.getField());
    }

    @Test
    void execute_rejectsInvalidIdCardNumberFormat() {
        AuthValidationException exception = assertThrows(AuthValidationException.class,
                () -> service.execute(command("0912345678", "not-a-number")));
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
        service.execute(command("0912345678", null));

        assertThrows(AuthValidationException.class,
                () -> requiringPhoneVerification().execute(commandWithToken("0912345678", "the-token")));

        assertTrue(consumedTokens.isEmpty());
    }
}
