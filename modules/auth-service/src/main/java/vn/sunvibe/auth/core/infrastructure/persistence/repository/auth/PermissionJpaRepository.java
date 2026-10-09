package vn.sunvibe.auth.core.infrastructure.persistence.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.PermissionEntity;

import java.util.UUID;

public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, UUID> {
}
