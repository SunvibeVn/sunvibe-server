package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.AccountStanding;
import vn.sunvibe.auth.core.domain.repository.user.AccountStandingRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Native on purpose, with the SQL of {@link UserRepositoryAdapter#findStanding} and {@link UserRepositoryAdapter#findRoleNames}
 * over a bound list: sign_in_losses has no entity field, and a managed entity may hold an old status.
 */
@Service
public class AccountStandingRepositoryAdapter implements AccountStandingRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Map<UserId, AccountStanding> findStandings(Collection<UserId> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<?> rows = entityManager.createNativeQuery("select id, user_status, deleted_at, sign_in_losses, created_date"
                        + " from users where id in (:ids)")
                .setParameter("ids", values(userIds))
                .getResultList();
        Map<UserId, AccountStanding> found = new HashMap<>();
        for (Object item : rows) {
            Object[] row = (Object[]) item;
            found.put(UserId.from((String) row[0]), new AccountStanding((String) row[1],
                    UserRepositoryAdapter.toInstant(row[2]), ((Number) row[3]).intValue(),
                    UserRepositoryAdapter.toInstant(row[4])));
        }
        return found;
    }

    @Override
    public Map<UserId, Set<String>> findRoleNames(Collection<UserId> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<?> rows = entityManager.createNativeQuery("select ur.user_id, r.name from user_roles ur join roles r"
                        + " on r.id = ur.role_id where ur.user_id in (:ids)")
                .setParameter("ids", values(userIds))
                .getResultList();
        Map<UserId, Set<String>> names = new HashMap<>();
        for (Object item : rows) {
            Object[] row = (Object[]) item;
            names.computeIfAbsent(UserId.from((String) row[0]), id -> new HashSet<>()).add((String) row[1]);
        }
        return names;
    }

    private static List<String> values(Collection<UserId> userIds) {
        return userIds.stream().map(UserId::getValue).distinct().toList();
    }
}
