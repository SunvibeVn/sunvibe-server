package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.vo.Username;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

/** W5-3: an ADMIN may name as owner only a user who exists and may sign in. */
class AuthUserDirectoryAdapterTest {

	private final Map<String, AppUser> users = new HashMap<>();
	private final AuthUserDirectoryAdapter adapter = new AuthUserDirectoryAdapter(new FindByIdOnlyRepository(users));

	@Test
	void existsActiveUser_activeUser_isTrue() {
		assertTrue(adapter.existsActiveUser(store("01ACTIVE000000000000000000", UserStatus.ACTIVE)));
	}

	@Test
	void existsActiveUser_pendingUser_isTrue() {
		assertTrue(adapter.existsActiveUser(store("01PENDING00000000000000000", UserStatus.PENDING)));
	}

	@Test
	void existsActiveUser_lockedUser_isFalse() {
		assertFalse(adapter.existsActiveUser(store("01LOCKED000000000000000000", UserStatus.LOCK)));
	}

	@Test
	void existsActiveUser_terminatedUser_isFalse() {
		assertFalse(adapter.existsActiveUser(store("01TERMINATED00000000000000", UserStatus.TERMINATED)));
	}

	@Test
	void existsActiveUser_unknownOrSoftDeletedUser_isFalse() {
		// The repository's findById already hides soft-deleted accounts, so both look the same here
		assertFalse(adapter.existsActiveUser("01UNKNOWN00000000000000000"));
	}

	@Test
	void existsActiveUser_nullOrBlankId_isFalse() {
		assertFalse(adapter.existsActiveUser(null));
		assertFalse(adapter.existsActiveUser("  "));
	}

	private String store(String id, UserStatus status) {
		AppUser user = AppUser.create(UserId.create(id), "Owner", Username.create("0912345678"), null,
				Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(Set.of("LANDLORD")),
				Instant.now());
		user.setUserStatus(status);
		users.put(id, user);
		return id;
	}

	/** Only findById is used by the adapter. */
	private record FindByIdOnlyRepository(Map<String, AppUser> users) implements UserDomainRepository {

		@Override
		public Optional<AppUser> findById(UserId userId) {
			return Optional.ofNullable(users.get(userId.getValue()));
		}

		@Override
		public boolean existsByUsername(String userName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean existsByEmail(String email) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean existsByIdCardNumber(IdCardNumber idCardNumber) {
			throw new UnsupportedOperationException();
		}

		@Override
		public AppUser save(AppUser user) {
			throw new UnsupportedOperationException();
		}

		@Override
		public AppUser findByUsername(String username) {
			throw new UnsupportedOperationException();
		}

		@Override
		public AppUser findByEmail(String email) {
			throw new UnsupportedOperationException();
		}
	}
}
