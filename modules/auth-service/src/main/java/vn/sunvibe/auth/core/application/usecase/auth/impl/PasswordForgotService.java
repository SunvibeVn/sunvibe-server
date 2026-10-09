package vn.sunvibe.auth.core.application.usecase.auth.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.command.auth.PasswordForgotCommand;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordForgotUseCase;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.domain.event.ForgotPasswordInitiatedEvent;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordForgotResponse;
import vn.sunvibe.common.generator.id.IdGenerator;

import java.time.LocalDateTime;

@Log4j2
@RequiredArgsConstructor
@Service
public class PasswordForgotService implements PasswordForgotUseCase {

    private final UserDomainRepository userDomainRepository;
    private final PasswordResetTokenDomainRepository passwordResetTokenDomainRepository;
    private final EmailServicePort emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final IdGenerator<Long> snowflakeIdGenerator;

    @Override
    @Transactional
    public PasswordForgotResponse execute(PasswordForgotCommand command) {
        log.info("Forgot password request for email: {}", command.email());

        // Find user by email
        AppUser user = userDomainRepository.findByEmail(command.email());

        // Generate OTP
        OtpCode otpCode = OtpCode.create();

        log.error(otpCode.getCode());

        // Generate reset token
        PasswordResetToken passwordResetToken = PasswordResetToken.create(snowflakeIdGenerator.generateId(), user.getId().getValue(), command.email(), otpCode.getCode());

        // Save reset token to DB
        passwordResetTokenDomainRepository.save(passwordResetToken);

        // Send email with OTP
//        try {
//            emailService.sendPasswordResetEmail(command.email(), otpCode.getCode(), passwordResetToken.getToken());
//        } catch (Exception e) {
//            log.error("Failed to send reset email: {}", e.getMessage());
//            throw new SendingMailFailedException("Failed to send reset email");
//        }

        // Publish event
        ForgotPasswordInitiatedEvent event = ForgotPasswordInitiatedEvent.builder().userId(user.getId().getValue()).email(command.email()).otpCode(otpCode.getCode()).resetToken(passwordResetToken.getToken()).initiatedAt(LocalDateTime.now()).build();
        eventPublisher.publishEvent(event);

        log.info("Password reset email sent to: {}", command.email());

        return PasswordForgotResponse.builder().message(String.format("Password reset email sent to %s.", command.email())).resetToken(passwordResetToken.getToken()).expiresAt(passwordResetToken.getExpiresAt()).otpExpirationMinutes(15).build();
    }
}
