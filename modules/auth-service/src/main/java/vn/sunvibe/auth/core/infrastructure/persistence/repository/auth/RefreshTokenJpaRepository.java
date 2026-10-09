package vn.sunvibe.auth.core.infrastructure.persistence.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.RefreshTokenEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(byte[] tokenHash);

    List<RefreshTokenEntity> findAllByFamilyId(UUID familyId);

}
