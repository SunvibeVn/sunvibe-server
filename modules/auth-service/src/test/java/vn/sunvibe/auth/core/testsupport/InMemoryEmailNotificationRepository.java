package vn.sunvibe.auth.core.testsupport;

import vn.sunvibe.auth.core.domain.model.auth.EmailNotification;
import vn.sunvibe.auth.core.domain.repository.auth.EmailNotificationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hand-written fake; stores copies like a database. */
public class InMemoryEmailNotificationRepository implements EmailNotificationRepository {

    private final Map<Long, EmailNotification> rows = new LinkedHashMap<>();

    @Override
    public void save(EmailNotification notification) {
        rows.put(notification.getId(), copy(notification, notification.getNextAttemptAt()));
    }

    @Override
    public List<EmailNotification> claimDue(Instant now, int limit, Instant claimedUntil) {
        List<EmailNotification> due = rows.values().stream()
                .filter(n -> !n.getNextAttemptAt().isAfter(now))
                .sorted(Comparator.comparing(EmailNotification::getNextAttemptAt))
                .limit(limit)
                .toList();
        List<EmailNotification> claimed = new ArrayList<>();
        for (EmailNotification notification : due) {
            EmailNotification moved = copy(notification, claimedUntil);
            rows.put(moved.getId(), moved);
            claimed.add(copy(moved, claimedUntil));
        }
        return claimed;
    }

    @Override
    public void delete(Long id) {
        rows.remove(id);
    }

    public List<EmailNotification> all() {
        return List.copyOf(rows.values());
    }

    /** Makes every row due. */
    public void makeDue(Instant now) {
        rows.replaceAll((id, n) -> copy(n, now));
    }

    private static EmailNotification copy(EmailNotification n, Instant nextAttemptAt) {
        return EmailNotification.load(n.getId(), n.getUserId(), n.getKind(), n.getDestination(), n.getCreatedAt(),
                n.getAttempts(), nextAttemptAt);
    }
}
