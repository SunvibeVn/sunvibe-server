package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.presentation.dto.response.user.UserProfileInfo;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserGetInfoServiceTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void execute_includesKycStatus() {
        UserProfileInfo profile = profileOf(Email.create("a@example.com"));

        assertEquals(KycStatus.UNVERIFIED.name(), profile.kycStatus());
    }

    @Test
    void execute_userWithoutEmail_returnsNullEmail() {
        UserProfileInfo profile = profileOf(null);

        assertNull(profile.email());
        assertEquals("0912345678", profile.username());
    }

    private UserProfileInfo profileOf(Email email) {
        AppUser appUser = AppUser.create(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                Username.create("0912345678"),
                email,
                Password.create("hashed-password"),
                ProfileImage.create(null),
                null,
                new HashSet<>(),
                Instant.now());

        UserDomainRepository repository = new UserDomainRepository() {
            @Override
            public boolean existsByUsername(String userName) { return false; }
            @Override
            public boolean existsByEmail(String email) { return false; }
            @Override
            public boolean existsByIdCardNumber(IdCardNumber idCardNumber) { return false; }
            @Override
            public AppUser save(AppUser user) { return user; }
            @Override
            public AppUser findByUsername(String username) { return appUser; }
            @Override
            public AppUser findByEmail(String email) { throw new UnsupportedOperationException(); }
            @Override
            public java.util.Optional<AppUser> findById(UserId userId) { throw new UnsupportedOperationException(); }
        };

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("0912345678", null, List.of()));

        return new UserGetInfoService(repository).execute();
    }
}
