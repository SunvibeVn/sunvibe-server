package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.PendingAccountRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PendingAccountRepositoryAdapter implements PendingAccountRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public List<UserId> findUnverifiedPendingCreatedBefore(Instant cutoff, int limit) {
        return userJpaRepository.findUnverifiedPendingIds(UserStatus.PENDING, cutoff, Limit.of(limit)).stream()
                .map(UserId::from)
                .toList();
    }
}
