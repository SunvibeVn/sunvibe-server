package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserMapper;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserRoleJpaRepository;
import vn.sunvibe.auth.core.shared.constant.UserMessage;
import vn.sunvibe.common.crypto.CryptoService;

import java.util.Set;

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

        // Get role from DB by input
        Set<UserRoleEntity> userRoleEntities = userRoleJpaRepository.findByNameIn(appUser.getUserRoles());
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

    /** The only place the ID card number's blind index (lookup hash) is derived. */
    private String idCardNumberHash(IdCardNumber idCardNumber) {
        return cryptoService.blindIndex(idCardNumber.getValue());
    }

}
