package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.shared.PasswordResetPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordForgotUseCase;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.domain.event.ForgotPasswordInitiatedEvent;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Sends a password-reset OTP by e-mail.
 * <p>
 * The answer is the same whether or not the address belongs to an account, so
 * this endpoint cannot be used to find out who is registered. Codes are not
 * re-sent within {@link PasswordResetPolicy#getResendCooldown()}, and issuing a
 * new code burns the previous ones.
 */
@Log4j2
@RequiredArgsConstructor
@Service
public class PasswordForgotService implements PasswordForgotUseCase {

    public static final String GENERIC_MESSAGE = "If an account exists for this email, a password reset code has been sent.";

    private final UserDomainRepository userDomainRepository;
    private final PasswordResetTokenDomainRepository passwordResetTokenDomainRepository;
    private final EmailServicePort emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final IdGenerator<Long> snowflakeIdGenerator;
    private final PasswordResetPolicy passwordResetPolicy;

    @Override
    @Transactional
    public PasswordForgotResponse execute(PasswordForgotCommand command) {
        Instant now = AppClock.currentUTC();
        // Stored e-mails are lower-case (see Email VO)
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        PasswordForgotResponse response = PasswordForgotResponse.builder()
                .message(GENERIC_MESSAGE)
                .expiresAt(now.plus(PasswordResetToken.LIFETIME))
                .otpExpirationMinutes(PasswordResetToken.LIFETIME.toMinutes())
                .build();

        Optional<AppUser> account = findAccount(email).filter(AppUser::canAuthenticate);
        if (account.isEmpty()) {
            log.info("Password reset requested for an unknown or blocked account; nothing sent");
            return response;
        }
        AppUser user = account.get();

        Optional<PasswordResetToken> latest = passwordResetTokenDomainRepository.findLatestByEmail(email);
        if (latest.isPresent() && latest.get().isValid()
                && now.isBefore(latest.get().getCreatedAt().plus(passwordResetPolicy.getResendCooldown()))) {
            log.info("Password reset code for user {} not re-sent: requested again within the cooldown",
                    user.getId().getValue());
            return response;
        }

        // Only the newest code may work
        passwordResetTokenDomainRepository.invalidateActiveTokens(email, now);

        OtpCode otpCode = OtpCode.create();
        PasswordResetToken passwordResetToken = PasswordResetToken.create(snowflakeIdGenerator.generateId(),
                user.getId().getValue(), email, otpCode.getCode());
        passwordResetTokenDomainRepository.save(passwordResetToken);

        emailService.sendPasswordResetEmail(email, otpCode.getCode(), passwordResetToken.getToken());

        eventPublisher.publishEvent(ForgotPasswordInitiatedEvent.builder()
                .userId(user.getId().getValue())
                .email(email)
                .initiatedAt(LocalDateTime.now())
                .build());

        log.info("Password reset code sent for user {}", user.getId().getValue());
        return response;
    }

    private Optional<AppUser> findAccount(String email) {
        try {
            return Optional.of(userDomainRepository.findByEmail(email));
        } catch (UserNotFoundException e) {
            return Optional.empty();
        }
    }
}
