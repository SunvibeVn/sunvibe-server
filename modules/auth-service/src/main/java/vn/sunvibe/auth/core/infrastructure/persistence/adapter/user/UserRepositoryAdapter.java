package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserMapper;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserRoleJpaRepository;
import vn.sunvibe.auth.core.shared.constant.UserMessage;
import vn.sunvibe.common.crypto.CryptoService;

import java.util.HashSet;
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
    public AppUser save(AppUser appUser) {
        UserEntity userEntity = userMapper.toEntity(appUser);
        IdCardNumber idCardNumber = appUser.getIdCardNumber();
        userEntity.setIdCardNumberHash(idCardNumber != null ? idCardNumberHash(idCardNumber) : null);

        // Roles of an existing account are never rewritten from the aggregate (it may be stale): only addRole and
        // removeRole change them. A new account gets its roles, each of which must exist in the roles table.
        Set<UserRoleEntity> userRoleEntities = userJpaRepository.findById(userEntity.getId())
                .map(stored -> (Set<UserRoleEntity>) new HashSet<>(stored.getUserRoles()))
                .orElseGet(() -> rolesOfNewAccount(appUser.getUserRoles()));
        userEntity.setUserRoles(userRoleEntities);

        return userMapper.toDomain(userJpaRepository.save(userEntity));
    }

    @Override
    public AppUser findByUsername(String username) {
        UserEntity userEntity = userJpaRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException(String.format(UserMessage.USER_NOT_FOUND_WITH_USERNAME, username)));
        return userMapper.toDomain(userEntity);
    }

    @Override
    public AppUser findByEmail(String email) {
        UserEntity userEntity = userJpaRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException(String.format(UserMessage.USER_NOT_FOUND_WITH_EMAIL, email)));
        return userMapper.toDomain(userEntity);
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

}
