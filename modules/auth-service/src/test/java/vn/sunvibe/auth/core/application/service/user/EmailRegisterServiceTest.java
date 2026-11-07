package vn.sunvibe.auth.core.application.service.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.command.user.EmailRegisterCommand;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.exception.user.IdentifierConflictException;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationEmailChallenge;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.EmailOccupancy;
import vn.sunvibe.auth.core.testsupport.InMemoryRegistrationEmailChallengeRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.UnsupportedUserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access S-10, S-11, 5.5: the proof makes the account, once, for that address only. */
class EmailRegisterServiceTest {

    private static final String KEY = "new.user@example.com";
    private static final String TOKEN = "the-registration-token";
    private static final String USER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    private static final PasswordEncoder ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encode(rawPassword).equals(encodedPassword);
        }
    };

    private final InMemoryRegistrationEmailChallengeRepository challenges = new InMemoryRegistrationEmailChallengeRepository();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final List<AppUser> created = new ArrayList<>();
    private final List<AppUser> sessions = new ArrayList<>();
    private EmailOccupancy occupancy = EmailOccupancy.FREE;
    private boolean conflictOnCreate;
    private EmailRegisterService service;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        RegistrationEmailChallenge challenge = RegistrationEmailChallenge.issue(1L, KEY, "123456", now, Duration.ofMinutes(10));
        challenge.verify(TOKEN, now, Duration.ofMinutes(15));
        challenges.save(challenge);
        service = new EmailRegisterService(new UnsupportedUserRepository() {
            @Override
            public EmailOccupancy emailOccupancy(String key, UserId selfOrNull) {
                return occupancy;
            }

            @Override
            public AppUser create(AppUser user) {
                if (conflictOnCreate) {
                    throw new IdentifierConflictException(IdentifierConflictException.Kind.EMAIL);
                }
                created.add(user);
                return user;
            }
        }, challenges, ENCODER, () -> USER_ID, audit, new LoginUseCase() {
            @Override
            public AuthResult execute(LoginCommand loginCommand, ClientInfo clientInfo) {
                throw new AssertionError("The new session must not go through the password login");
            }

            @Override
            public AuthResult loginInternal(AppUser appUser, ClientInfo clientInfo) {
                sessions.add(appUser);
                return AuthResult.of(appUser, "access", "refresh", null);
            }
        });
    }

    private static EmailRegisterCommand command(String email, String token) {
        return new EmailRegisterCommand(email, token, "Nguyen Van A", "Sunvibe@2027", ClientInfo.unknown());
    }

    @Test
    void complete_createsActiveVerifiedNoPhone() {
        AuthResult result = service.execute(command("New.User@Example.com", TOKEN));

        AppUser user = created.getFirst();
        assertEquals(UserStatus.ACTIVE, user.getUserStatus());
        assertTrue(user.isEmailVerified());
        assertEquals(KEY, user.getEmailKey());
        assertNull(user.getUsername());
        assertNull(user.getPhoneKey());
        assertEquals("hashed:Sunvibe@2027", user.getPassword().getHashedValue());
        assertEquals(List.of(user), sessions);
        assertEquals("refresh", result.refreshToken());
        assertEquals(AuditEventType.USER_REGISTERED, audit.last().type());
    }

    /** auth-roles BR-3: self-registration grants exactly these two. */
    @Test
    void complete_rolesExactlyTenantCustomer() {
        service.execute(command(KEY, TOKEN));

        assertEquals(Set.of("TENANT", "CUSTOMER"), created.getFirst().getUserRoles());
    }

    @Test
    void complete_consumesTheToken() {
        service.execute(command(KEY, TOKEN));

        assertNotNull(challenges.all().getFirst().getRegisteredAt());
    }

    @Test
    void complete_tokenOtherEmail_refused() {
        assertEquals(VerificationException.Reason.REGISTRATION_TOKEN_INVALID, refused(command("other@example.com", TOKEN)));

        assertTrue(created.isEmpty());
    }

    @Test
    void complete_sameTokenTwice_secondRefused() {
        service.execute(command(KEY, TOKEN));

        assertEquals(VerificationException.Reason.REGISTRATION_TOKEN_INVALID, refused(command(KEY, TOKEN)));

        assertEquals(1, created.size());
    }

    @Test
    void complete_unknownToken_refused() {
        assertEquals(VerificationException.Reason.REGISTRATION_TOKEN_INVALID, refused(command(KEY, "no-such-token")));
    }

    @Test
    void complete_expiredToken_refused() {
        challenges.age(Duration.ofMinutes(16));

        assertEquals(VerificationException.Reason.REGISTRATION_TOKEN_INVALID, refused(command(KEY, TOKEN)));
    }

    /** I-3: an address taken after the proof, or a keyless equivalent, stops the account; the token stays. */
    @Test
    void complete_addressTakenMeanwhile_409_tokenNotConsumed() {
        occupancy = EmailOccupancy.OTHER;

        assertEquals(VerificationException.Reason.EMAIL_NOT_AVAILABLE, refused(command(KEY, TOKEN)));

        assertTrue(created.isEmpty());
        assertNull(challenges.all().getFirst().getRegisteredAt());
        assertTrue(sessions.isEmpty());
    }

    /** E-17: the race is lost at the unique index; the transaction rolls back and no session starts. */
    @Test
    void complete_conflict_409_noCookie_tokenNotConsumed() {
        conflictOnCreate = true;

        assertThrows(IdentifierConflictException.class, () -> service.execute(command(KEY, TOKEN)));

        assertTrue(sessions.isEmpty());
        assertNull(challenges.all().getFirst().getRegisteredAt());
    }

    @Test
    void useCase_refusedValue_throws_noRow() {
        assertThrows(AuthValidationException.class, () -> service.execute(command("not an email", TOKEN)));

        assertTrue(created.isEmpty());
    }

    private VerificationException.Reason refused(EmailRegisterCommand command) {
        return assertThrows(VerificationException.class, () -> service.execute(command)).getReason();
    }
}
