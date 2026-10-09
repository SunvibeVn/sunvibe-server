package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.usecase.auth.RefreshTokenGenerateUseCase;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenGenerateService implements RefreshTokenGenerateUseCase {

    /*────────── DI ──────────*/
    private final RefreshTokenDomainRepository refreshTokenDomainRepository;
    private final IdGenerator<Long> snowflakeIdGenerator;

    @Override
    public String generateRefreshToken(String userId, String rawFingerprint) {

        try {
            Instant now = AppClock.currentUTC();
            // new family for new login
            UUID familyId = UUID.randomUUID();
            // Refresh token will expire in 30 days
            Instant expiresAt = now.plus(Duration.ofDays(30));
            String fingerprint = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(rawFingerprint.getBytes(StandardCharsets.UTF_8)));

            // Create plain token (return to client)
            String refreshTokenPlain = UUID.randomUUID().toString();

            // Hash for saving in DB
            byte[] hash = hashToken(refreshTokenPlain);

            RefreshToken domain = RefreshToken.builder()
                    .id(snowflakeIdGenerator.generateId())
                    .userId(userId)
                    .familyId(familyId)
                    .tokenHash(hash)
                    .fingerprint(fingerprint)
                    .issuedAt(now)
                    .expiresAt(expiresAt)
                    .status(TokenStatus.ACTIVE)
                    .build();

            // Save in database
            refreshTokenDomainRepository.saveRefreshToken(domain);

            return refreshTokenPlain;
        } catch (NoSuchAlgorithmException e) {
            throw new AuthenticationFailedException("Error creating refresh token");
        }
    }

    // Hàm rotate token
//    public String rotate(String refreshPlain, String fingerprint) {
//        byte[] hash = hashToken(refreshPlain);
//        RefreshToken domain = tokenRepositoryPort.findRefreshTokenByTokenHash(hash);
//
//        // Check expired
//        if (domain.isExpired(CURRENT_TIME)) {
//            throw new RuntimeException("Refresh token expired");
//        }
//
//        // Check reuse
//        if (domain.getStatus() == TokenStatus.USED || domain.getStatus() == TokenStatus.REVOKED) {
//            // -> revoke toàn bộ family
//            repository.findAllByFamilyId(token.getFamilyId())
//                    .forEach(t -> {
//                        t.setStatus(TokenStatus.REVOKED);
//                        t.setRevokedAt(AppClock.currentUTC());
//                        repository.save(t);
//                    });
//            throw new RuntimeException("Refresh token reuse detected, family revoked");
//        }
//
//        // Mark old token as USED
//        token.markUsed();
//        entity = entity.toEntity(token);
//        entity.setUsedAt(AppClock.currentUTC());
//        repository.save(entity);
//
//        // Tạo token mới trong cùng family
//        String newPlain = UUID.randomUUID() + "." + UUID.randomUUID();
//        byte[] newHash = hashToken(newPlain);
//
//        RefreshToken newToken = RefreshToken.builder()
//                .id(UUID.randomUUID())
//                .userId(token.getUserId())
//                .familyId(token.getFamilyId())
//                .tokenHash(newHash)
//                .fingerprint(fingerprint)
//                .issuedAt(AppClock.currentUTC())
//                .expiresAt(AppClock.currentUTC().plus(Duration.ofDays(30)))
//                .status(TokenStatus.ACTIVE)
//                .build();
//
//        repository.save(new RefreshTokenEntity().toEntity(newToken));
//
//        return newPlain;
//    }

//    // Hàm revoke tất cả family (dùng khi logout)
//    public void revokeFamily(UUID familyId) {
//        List<RefreshTokenEntity> tokens = repository.findAllByFamilyId(familyId);
//        tokens.forEach(t -> {
//            t.setStatus(TokenStatus.REVOKED);
//            t.setRevokedAt(AppClock.currentUTC());
//            tokenRepositoryPort.saveRefreshToken(t);
//        });
//    }

    // Hash token
    private byte[] hashToken(String refreshPlain) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(refreshPlain.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
