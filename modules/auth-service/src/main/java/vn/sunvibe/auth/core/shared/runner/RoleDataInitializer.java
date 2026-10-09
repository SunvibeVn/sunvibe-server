package vn.sunvibe.auth.core.shared.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity.user.UserRoleEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.jpa.repository.user.UserRoleJpaRepository;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
public class RoleDataInitializer implements CommandLineRunner {

    private final UserRoleJpaRepository userRoleRepository;

    @Override
    public void run(String... args) {
        if (userRoleRepository.count() == 0) {
            List<String> defaultRoles = Arrays.asList(
                    "SUPER_ADMIN",
                    "ADMIN",
                    "MANAGER",
                    "LANDLORD",
                    "TENANT",
                    "LEADER",
                    "EMPLOYEE",
                    "USER"
            );

            defaultRoles.forEach(roleName -> {
                UserRoleEntity role = new UserRoleEntity();
                role.setName(roleName);
                role.setDescription(roleName + " role");
                userRoleRepository.save(role);
            });

            log.info("✅ Default roles have been inserted into database.");
        } else {
            log.warn("ℹ️ Roles already exist, skip seeding.");
        }
    }
}
