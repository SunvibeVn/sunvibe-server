package vn.sunvibe.auth.core.shared.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserRoleJpaRepository;

/**
 * Seeds the {@code ROLES} table with every {@link RoleName} on first start-up.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class RoleDataInitializer implements CommandLineRunner {

    private final UserRoleJpaRepository userRoleRepository;

    @Override
    public void run(String... args) {
        if (userRoleRepository.count() > 0) {
            log.info("Roles already exist, skip seeding.");
            return;
        }
        for (RoleName roleName : RoleName.values()) {
            UserRoleEntity role = new UserRoleEntity();
            role.setName(roleName.name());
            role.setDescription(roleName.name() + " role");
            userRoleRepository.save(role);
        }
        log.info("Default roles have been inserted into database.");
    }
}
