package vn.sunvibe.auth.core.application.service.user;

import vn.sunvibe.auth.core.application.shared.AccountAccessPolicy;
import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.auth.core.application.query.user.UserProfileResult;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.testsupport.SignedIn;
import vn.sunvibe.auth.core.testsupport.UnsupportedUserRepository;

import java.time.Instant;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class UserGetInfoServiceTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void execute_includesKycStatus() {
        UserProfileResult profile = profileOf(Email.create("a@example.com"));

        assertEquals(KycStatus.UNVERIFIED.name(), profile.kycStatus());
    }

    @Test
    void execute_userWithoutEmail_returnsNullEmail() {
        UserProfileResult profile = profileOf(null);

        assertNull(profile.email());
        assertEquals("0912345678", profile.username());
    }

    private UserProfileResult profileOf(Email email) {
        AppUser appUser = AuthTestUsers.withPhone(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                "0912345678",
                email,
                Password.create("hashed-password"),
                ProfileImage.create(null),
                null,
                new HashSet<>(),
                Instant.now());

        UserDomainRepository repository = new UnsupportedUserRepository() {
            @Override
            public java.util.Optional<AppUser> findById(UserId userId) { return java.util.Optional.of(appUser); }
        };

        SignedIn.as("01ARZ3NDEKTSV4RRFFQ69G5FAV");

        return new UserGetInfoService(repository, new AccountAccessPolicy(AuthTestUsers.phoneOn())).execute();
    }
}
