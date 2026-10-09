package vn.sunvibe.auth.core.infrastructure.persistence.jpa.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity.auth.PermissionEntity;

import java.util.UUID;

public interface JpaPermissionRepository extends JpaRepository<PermissionEntity, UUID> {
}
