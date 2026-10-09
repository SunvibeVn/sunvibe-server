package vn.sunvibe.auth.core.application.usecase.auth.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.usecase.auth.RefreshTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.PasswordResetUseCase;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.OtpCode;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.PasswordResetToken;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;
import vn.sunvibe.auth.core.presentation.dto.request.auth.PasswordResetRequest;
import vn.sunvibe.auth.core.presentation.dto.response.auth.PasswordResetResponse;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Log4j2
public class PasswordResetService implements PasswordResetUseCase {

    private final PasswordResetTokenDomainRepository passwordResetTokenDomainRepository;
    private final UserDomainRepository userDomainRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailServicePort emailService;
    private final JWTProvider jwtTokenProvider;
    private final RefreshTokenGenerateUseCase refreshTokenGenerateUseCase;

    @Override
    @Transactional
    public PasswordResetResponse verifyOtpAndResetPassword(PasswordResetRequest request) {
        log.info("Verifying OTP for reset token: {}", request.getResetToken());

        // Find reset token
        PasswordResetToken passwordResetToken = passwordResetTokenDomainRepository.findByToken(request.getResetToken())
                .orElseThrow(() -> new PasswordResetTokenException("Invalid reset token"));

        // Check token validity
        if (!passwordResetToken.isValid()) {
            throw new PasswordResetTokenException("Reset token has expired or already used");
        }

        // Validate OTP
        OtpCode otpCode = OtpCode.restore(
                passwordResetToken.getOtpCode(),
                passwordResetToken.getCreatedAt(),
                passwordResetToken.getExpiresAt(),
                false
        );

        if (!otpCode.matches(request.getOtpCode())) {
            throw new InvalidOtpException("Invalid OTP code");
        }

        // Find user
        AppUser user = userDomainRepository.findByEmail(passwordResetToken.getEmail());

        // A locked/disabled account must not regain access through the reset flow
        if (!user.canAuthenticate()) {
            throw new AuthenticationFailedException("Account is locked or disabled.");
        }

        // Change password
        String encryptedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(Password.create(encryptedPassword));

        // Save user
        userDomainRepository.save(user);

        // Mark reset token as used
        passwordResetTokenDomainRepository.markAsUsed(request.getResetToken());

        // Generate new tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername().getValue());
        String refreshToken = refreshTokenGenerateUseCase.generateRefreshToken(user.getId().getValue(), "");

        // Send confirmation email
        emailService.sendPasswordResetConfirmationEmail(user.getEmail().getValue());

        log.info("Password reset successfully for user: {}", user.getId().getValue());

        return PasswordResetResponse.builder()
                .message("Password reset successfully")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .passwordChangedAt(LocalDateTime.now())
                .build();
    }
}
