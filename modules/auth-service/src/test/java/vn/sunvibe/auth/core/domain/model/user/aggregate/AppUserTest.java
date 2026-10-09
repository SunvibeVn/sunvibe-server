package vn.sunvibe.auth.core.domain.model.user.aggregate;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;

import java.time.Instant;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class AppUserTest {

    private AppUser newUser() {
        return AppUser.create(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                Username.create("0912345678"),
                Email.create("a@example.com"),
                Password.create("hashed-password"),
                ProfileImage.create(null),
                IdCardNumber.create("123456789012"),
                new HashSet<>(),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void create_defaultsKycStatusToUnverified() {
        assertEquals(KycStatus.UNVERIFIED, newUser().getKycStatus());
    }

    @Test
    void create_leavesIdCardImagesAndDeletedAtNull() {
        AppUser user = newUser();
        assertNull(user.getIdCardFrontUrl());
        assertNull(user.getIdCardBackUrl());
        assertNull(user.getDeletedAt());
    }

    @Test
    void create_storesTheGivenIdCardNumber() {
        assertEquals("123456789012", newUser().getIdCardNumber().getValue());
    }

    @Test
    void deactivate_setsStatusDeletedAndDeletedAtTimestamp() {
        AppUser user = newUser();
        user.deactivate();
        assertEquals(UserStatus.DELETED, user.getUserStatus());
        assertNotNull(user.getDeletedAt());
    }
}
