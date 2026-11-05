package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserLoginAttemptRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepository;

import java.time.Instant;

/**
 * JPA implementation of {@link UserLoginAttemptRepository}, using atomic updates.
 */
@Service
@RequiredArgsConstructor
public class UserLoginAttemptRepositoryAdapter implements UserLoginAttemptRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public int incrementFailedLoginAttempts(UserId userId) {
        // UPDATE first: it takes the row lock, so the following read sees this caller's own increment
        userJpaRepository.incrementFailedLoginAttempts(userId.getValue());
        return userJpaRepository.findFailedLoginAttempts(userId.getValue());
    }

    @Override
    public int incrementReauthFailures(UserId userId) {
        // UPDATE first: it takes the row lock, so the following read sees this caller's own increment
        userJpaRepository.incrementReauthFailures(userId.getValue());
        return userJpaRepository.findReauthFailures(userId.getValue());
    }

    @Override
    public void lockReauthUntil(UserId userId, Instant until) {
        userJpaRepository.lockReauthUntil(userId.getValue(), until);
    }

    @Override
    public void clearReauthFailures(UserId userId) {
        userJpaRepository.clearReauthFailures(userId.getValue());
    }

    @Override
    public void recordLoginSuccess(UserId userId, Instant when) {
        userJpaRepository.recordLoginSuccess(userId.getValue(), when);
    }

    @Override
    public void activateIfPending(UserId userId) {
        userJpaRepository.activateIfPending(userId.getValue(), UserStatus.ACTIVE, UserStatus.PENDING);
    }

    @Override
    public void lockLoginUntil(UserId userId, Instant until) {
        userJpaRepository.lockLoginUntil(userId.getValue(), until);
    }
}
