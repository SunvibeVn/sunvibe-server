package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.exception.user.IdentifierConflictException;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.AccountStanding;
import vn.sunvibe.auth.core.domain.repository.user.EmailOccupancy;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.StaffAccountRow;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.adapter.AuthConstraintViolations;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserMapper;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserRoleJpaRepository;
import vn.sunvibe.shared.crypto.CryptoService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JPA implementation of {@link UserDomainRepository}. Roles are attached by
 * name from the {@code ROLES} table; the ID card number is stored encrypted
 * together with a blind index for duplicate checks.
 */
@Service
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserDomainRepository {

    private static final Set<String> EMAIL_CONSTRAINTS = Set.of("uq_users_email_key", "idx_users_email");
    private static final Set<String> PHONE_CONSTRAINTS = Set.of("uq_users_phone_key", "idx_users_username");

    @PersistenceContext
    private EntityManager entityManager;

    private final UserJpaRepository userJpaRepository;
    private final UserRoleJpaRepository userRoleJpaRepository;
    private final UserMapper userMapper;
    private final CryptoService cryptoService;

    @Override
    public boolean existsByUsername(String username) {
        return userJpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByIdCardNumber(IdCardNumber idCardNumber) {
        return userJpaRepository.existsByIdCardNumberHash(idCardNumberHash(idCardNumber));
    }

    @Override
    public AppUser create(AppUser appUser) {
        UserEntity userEntity = toEntity(appUser);
        userEntity.setUserRoles(rolesOfNewAccount(appUser.getUserRoles()));
        return write(userEntity);
    }

    @Override
    public AppUser save(LockedUser locked) {
        UserEntity userEntity = toEntity(locked.user());
        // Roles of an existing account are never rewritten from the aggregate: only addRole and removeRole change them
        userEntity.setUserRoles(userJpaRepository.findById(userEntity.getId())
                .map(stored -> (Set<UserRoleEntity>) new HashSet<>(stored.getUserRoles()))
                .orElseThrow(() -> new IllegalStateException("A locked account has no row")));
        return write(userEntity);
    }

    /** Lock first: the reload below must see the row as it is after any commit this lock waited for. */
    @Override
    public Optional<LockedUser> findByIdForSecurity(UserId userId) {
        if (userJpaRepository.lockForSecurity(userId.getValue()).isEmpty()) {
            return Optional.empty();
        }
        // find alone would answer from the persistence context, which open-in-view keeps for the whole request
        UserEntity userEntity = entityManager.find(UserEntity.class, userId.getValue());
        entityManager.refresh(userEntity);
        return Optional.of(new LockedUser(userMapper.toDomain(userEntity)));
    }

    @Override
    public void lockForSessionRefresh(UserId userId) {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate();
        userJpaRepository.lockForShare(userId.getValue());
    }

    /** Native on purpose: sign_in_losses has no entity field, and a managed entity may hold an old status. */
    @Override
    public Optional<AccountStanding> findStanding(UserId userId) {
        List<?> rows = entityManager.createNativeQuery("select user_status, deleted_at, sign_in_losses, created_date"
                        + " from users where id = :id")
                .setParameter("id", userId.getValue())
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) rows.getFirst();
        return Optional.of(new AccountStanding((String) row[0], toInstant(row[1]), ((Number) row[2]).intValue(),
                toInstant(row[3])));
    }

    @Override
    public Set<String> findRoleNames(UserId userId) {
        List<?> rows = entityManager.createNativeQuery("select r.name from user_roles ur join roles r on r.id = ur.role_id"
                        + " where ur.user_id = :id")
                .setParameter("id", userId.getValue())
                .getResultList();
        Set<String> names = new HashSet<>();
        rows.forEach(name -> names.add((String) name));
        return names;
    }

    private static Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case OffsetDateTime offset -> offset.toInstant();
            case Timestamp timestamp -> timestamp.toInstant();
            default -> throw new IllegalStateException("Unexpected time type: " + value.getClass().getName());
        };
    }

    private UserEntity toEntity(AppUser appUser) {
        UserEntity userEntity = userMapper.toEntity(appUser);
        IdCardNumber idCardNumber = appUser.getIdCardNumber();
        userEntity.setIdCardNumberHash(idCardNumber != null ? idCardNumberHash(idCardNumber) : null);
        return userEntity;
    }

    /** Flushed here, so a lost race for an identifier is translated now and not at commit, where it would be a 500. */
    private AppUser write(UserEntity userEntity) {
        try {
            return userMapper.toDomain(userJpaRepository.saveAndFlush(userEntity));
        } catch (DataIntegrityViolationException e) {
            throw identifierConflict(e).orElseThrow(() -> e);
        }
    }

    private static Optional<RuntimeException> identifierConflict(DataIntegrityViolationException e) {
        return AuthConstraintViolations.constraintName(e).flatMap(constraint -> {
            if (EMAIL_CONSTRAINTS.contains(constraint)) {
                return Optional.of(new IdentifierConflictException(IdentifierConflictException.Kind.EMAIL));
            }
            if (PHONE_CONSTRAINTS.contains(constraint)) {
                return Optional.of(new IdentifierConflictException(IdentifierConflictException.Kind.PHONE));
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<AppUser> findByEmailKey(String emailKey) {
        return userJpaRepository.findByEmailKey(emailKey).map(userMapper::toDomain);
    }

    @Override
    public EmailOccupancy emailOccupancy(String emailKey, UserId selfOrNull) {
        List<Boolean> holders = userJpaRepository.findEmailHolders(emailKey, selfOrNull != null ? selfOrNull.getValue() : "");
        if (holders.isEmpty()) {
            return EmailOccupancy.FREE;
        }
        return holders.contains(Boolean.TRUE) ? EmailOccupancy.VERIFIED : EmailOccupancy.OTHER;
    }

    @Override
    public boolean existsByPhoneKey(String phoneKey) {
        return userJpaRepository.existsByPhoneKey(phoneKey);
    }

    @Override
    public Optional<AppUser> findByPhoneKey(String phoneKey) {
        return userJpaRepository.findByPhoneKey(phoneKey).map(userMapper::toDomain);
    }

    @Override
    public Optional<AppUser> findKeylessByUsername(String username) {
        return userJpaRepository.findKeylessByUsername(username).map(userMapper::toDomain);
    }

    @Override
    public Optional<AppUser> findById(UserId userId) {
        return userJpaRepository.findActiveById(userId.getValue()).map(userMapper::toDomain);
    }

    @Override
    public boolean addRole(UserId userId, RoleName role) {
        return userRoleJpaRepository.insertUserRole(userId.getValue(), roleId(role)) == 1;
    }

    @Override
    public boolean removeRole(UserId userId, RoleName role) {
        return userRoleJpaRepository.deleteUserRole(userId.getValue(), roleId(role)) == 1;
    }

    private UUID roleId(RoleName role) {
        return userRoleJpaRepository.findByName(role.name())
                .map(UserRoleEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Role missing from the roles table: " + role));
    }

    private Set<UserRoleEntity> rolesOfNewAccount(Set<String> names) {
        Set<UserRoleEntity> found = userRoleJpaRepository.findByNameIn(names);
        if (found.size() != names.size()) {
            throw new IllegalStateException("A role is missing from the roles table");
        }
        return found;
    }

    /** The only place the ID card number's blind index (lookup hash) is derived. */
    private String idCardNumberHash(IdCardNumber idCardNumber) {
        return cryptoService.blindIndex(idCardNumber.getValue());
    }

    @Override
    public List<StaffAccountRow> findSignInAbleByAnyRole(Set<String> roleNames, int limit) {
        if (roleNames.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<StaffAccountRow> rows = userJpaRepository
                .findSignInAbleStaffRows(roleNames, AppUser.SIGN_IN_BLOCKED_STATUSES)
                .stream().sorted(StaffAccountRow.nameOrder()).toList();
        // Keep the first `limit` accounts in the same order the service sorts with, so the cut agrees with it.
        Set<String> kept = new LinkedHashSet<>();
        List<StaffAccountRow> result = new ArrayList<>();
        for (StaffAccountRow row : rows) {
            if (kept.contains(row.userId()) || kept.size() < limit) {
                kept.add(row.userId());
                result.add(row);
            }
        }
        return result;
    }

    @Override
    public Map<String, String> findFullNamesByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (UserJpaRepository.UserName row : userJpaRepository.findNamesByIds(new LinkedHashSet<>(ids))) {
            names.put(row.id(), row.fullName());
        }
        return names;
    }

}
