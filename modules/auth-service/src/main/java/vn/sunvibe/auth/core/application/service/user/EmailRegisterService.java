package vn.sunvibe.auth.core.application.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.user.EmailRegisterCommand;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.shared.ContactMasker;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.user.EmailRegisterUseCase;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.domain.model.auth.RegistrationEmailChallenge;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationEmailChallengeRepository;
import vn.sunvibe.auth.core.domain.repository.user.EmailOccupancy;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Self-registration by a proven mailbox, in one transaction (auth-email-access spec 5.5): the token row is locked, so a
 * token works once; the account is created and flushed before the token is used up, so a race lost at the unique index
 * rolls everything back and the token stays.
 */
@Service
@RequiredArgsConstructor
public class EmailRegisterService implements EmailRegisterUseCase {

    static final String INVALID_TOKEN = "The registration has expired. Start again.";
    static final String NOT_AVAILABLE = "This email cannot be used now.";

    private final UserDomainRepository userRepository;
    private final RegistrationEmailChallengeRepository challengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdGenerator<String> ulidGenerator;
    private final AuditLogPort auditLog;
    private final LoginUseCase loginUseCase;

    @Override
    @Transactional
    public AuthResult execute(EmailRegisterCommand command) {
        Email email = Email.create(command.email());
        Instant now = AppClock.currentUTC();

        RegistrationEmailChallenge challenge = usableChallenge(command.registrationToken(), email.getValue(), now);
        if (userRepository.emailOccupancy(email.getValue(), null) != EmailOccupancy.FREE) {
            throw new VerificationException(VerificationException.Reason.EMAIL_NOT_AVAILABLE, NOT_AVAILABLE);
        }

        // Self-registered accounts get exactly TENANT and CUSTOMER (auth-roles BR-3); other roles are granted by staff
        AppUser appUser = userRepository.create(AppUser.registerWithEmail(UserId.create(ulidGenerator.generateId()),
                command.fullName(), email, Password.create(passwordEncoder.encode(command.password())),
                RoleName.SELF_REGISTRATION.stream().map(RoleName::name).collect(Collectors.toCollection(HashSet::new)), now));

        challenge.markRegistered(now);
        challengeRepository.save(challenge);

        auditLog.record(AuthAuditEvent.of(AuditEventType.USER_REGISTERED, appUser.getId().getValue(),
                ContactMasker.maskEmail(email.getValue())));
        ClientInfo clientInfo = command.clientInfo() != null ? command.clientInfo() : ClientInfo.unknown();
        return loginUseCase.loginInternal(appUser, clientInfo);
    }

    private RegistrationEmailChallenge usableChallenge(String registrationToken, String emailKey, Instant now) {
        Optional<RegistrationEmailChallenge> found = registrationToken == null || registrationToken.isBlank()
                ? Optional.empty()
                : challengeRepository.findByTokenHashForUpdate(RegistrationEmailChallenge.hashToken(registrationToken));
        return found.filter(challenge -> challenge.isTokenUsable(now) && challenge.getEmailKey().equals(emailKey))
                .orElseThrow(() -> new VerificationException(VerificationException.Reason.REGISTRATION_TOKEN_INVALID,
                        INVALID_TOKEN));
    }
}
