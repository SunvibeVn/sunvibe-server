package vn.sunvibe.auth.core.domain.repository.user;

import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * The stored standing of a set of accounts, read past the persistence context as {@link UserDomainRepository#findStanding}
 * and {@link UserDomainRepository#findRoleNames} read one (property-create intake D5-10). Each method is one statement for
 * the whole set; an empty input answers an empty map without a statement.
 */
public interface AccountStandingRepository {

    /** The standing of each account found; an id with no row is absent. */
    Map<UserId, AccountStanding> findStandings(Collection<UserId> userIds);

    /** The role names of each account that holds a role; an id with no role is absent. */
    Map<UserId, Set<String>> findRoleNames(Collection<UserId> userIds);
}
