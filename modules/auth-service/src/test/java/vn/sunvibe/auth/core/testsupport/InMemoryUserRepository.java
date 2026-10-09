package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserLoginAttemptRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written fake of the user ports, keyed by username. Lookups throw
 * {@link UserNotFoundException} like the real adapter.
 */
public class InMemoryUserRepository implements UserDomainRepository, UserLoginAttemptRepository {

    private final Map<String, AppUser> users = new LinkedHashMap<>();
    public int saveCount;

    public InMemoryUserRepository with(AppUser user) {
        users.put(user.getUsername().getValue(), user);
        return this;
    }

    @Override
    public boolean existsByUsername(String userName) {
        return users.containsKey(userName);
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.values().stream().anyMatch(u -> u.getEmail() != null && u.getEmail().getValue().equals(email));
    }

    @Override
    public boolean existsByIdCardNumber(IdCardNumber idCardNumber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AppUser save(AppUser user) {
        saveCount++;
        users.put(user.getUsername().getValue(), user);
        return user;
    }

    @Override
    public AppUser findByUsername(String username) {
        AppUser user = users.get(username);
        if (user == null) {
            throw new UserNotFoundException("User not found with username: " + username);
        }
        return user;
    }

    @Override
    public AppUser findByEmail(String email) {
        return users.values().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().getValue().equals(email))
                .findFirst()
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    @Override
    public Optional<AppUser> findById(UserId userId) {
        return users.values().stream().filter(u -> u.getId().equals(userId)).findFirst();
    }

    @Override
    public int incrementFailedLoginAttempts(UserId userId) {
        AppUser user = findById(userId).orElseThrow();
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        return user.getFailedLoginAttempts();
    }

    @Override
    public void lockLoginUntil(UserId userId, Instant until) {
        findById(userId).orElseThrow().setLockedUntil(until);
    }
}
