package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumber;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.EmailOccupancy;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.StaffAccountRow;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** A user repository that refuses everything: a test overrides only the calls it expects. */
public class UnsupportedUserRepository implements UserDomainRepository {

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
    public AppUser create(AppUser user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AppUser save(LockedUser user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<AppUser> findByEmailKey(String emailKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EmailOccupancy emailOccupancy(String emailKey, UserId selfOrNull) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean existsByPhoneKey(String phoneKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<AppUser> findByPhoneKey(String phoneKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<AppUser> findKeylessByUsername(String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<AppUser> findById(UserId userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LockedUser> findByIdForSecurity(UserId userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void lockForSessionRefresh(UserId userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addRole(UserId userId, RoleName role) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeRole(UserId userId, RoleName role) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<StaffAccountRow> findSignInAbleByAnyRole(Set<String> roleNames, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> findFullNamesByIds(Collection<String> ids) {
        throw new UnsupportedOperationException();
    }
}
