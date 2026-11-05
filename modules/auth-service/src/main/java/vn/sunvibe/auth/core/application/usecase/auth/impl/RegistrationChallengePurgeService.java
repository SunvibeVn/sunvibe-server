package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationChallengePurgeUseCase;
import vn.sunvibe.auth.core.domain.repository.auth.PasswordResetTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationEmailChallengeRepository;
import vn.sunvibe.auth.core.domain.repository.auth.RegistrationPhoneChallengeRepository;
import vn.sunvibe.auth.core.domain.repository.auth.VerificationCodeRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.function.IntUnaryOperator;

/**
 * Deletes spent proofs: registration phone challenges a day after they expired, and the proofs of the email flows two days
 * after their last expiry (auth-email-access spec 5.7). Those three tables can be large, so each batch is one short
 * transaction and the run goes on until none is left.
 */
@Service
@RequiredArgsConstructor
public class RegistrationChallengePurgeService implements RegistrationChallengePurgeUseCase {

    /** Kept a little longer so recent failures stay traceable next to the audit log. */
    static final Duration RETENTION_AFTER_EXPIRY = Duration.ofDays(1);
    static final Duration PROOF_RETENTION_AFTER_EXPIRY = Duration.ofDays(2);
    static final int BATCH_SIZE = 1000;

    private final RegistrationPhoneChallengeRepository challengeRepository;
    private final RegistrationEmailChallengeRepository emailChallengeRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordResetTokenDomainRepository resetTokenRepository;
    private final TransactionTemplate transaction;

    @Override
    public int execute() {
        Instant now = AppClock.currentUTC();
        Instant proofCutoff = now.minus(PROOF_RETENTION_AFTER_EXPIRY);

        Integer phoneChallenges = transaction.execute(
                status -> challengeRepository.deleteExpiredBefore(now.minus(RETENTION_AFTER_EXPIRY)));
        return (phoneChallenges == null ? 0 : phoneChallenges)
                + inBatches(limit -> emailChallengeRepository.deleteExpiredBatch(proofCutoff, limit))
                + inBatches(limit -> verificationCodeRepository.deleteExpiredBatch(proofCutoff, limit))
                + inBatches(limit -> resetTokenRepository.deleteExpiredBatch(proofCutoff, limit));
    }

    private int inBatches(IntUnaryOperator deleteBatch) {
        int total = 0;
        int deleted;
        do {
            Integer batch = transaction.execute(status -> deleteBatch.applyAsInt(BATCH_SIZE));
            deleted = batch == null ? 0 : batch;
            total += deleted;
        } while (deleted == BATCH_SIZE);
        return total;
    }
}
