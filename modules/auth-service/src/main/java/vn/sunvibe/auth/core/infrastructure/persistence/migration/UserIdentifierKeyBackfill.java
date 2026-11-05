package vn.sunvibe.auth.core.infrastructure.persistence.migration;

import lombok.extern.log4j.Log4j2;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Gives old rows their canonical keys without guessing (auth-email-access spec 5.3, BR-25). Runs on the caller's connection
 * and in its transaction: it never commits or closes it, so any failure leaves the table as it was.
 * <p>
 * A value the policy refuses gets no key, and so does a key two live rows would share: nobody can tell which of them owns
 * it. Stored values, timestamps and the status are not touched, and a deleted row keeps NULL keys. The log has counts only.
 */
@Log4j2
public final class UserIdentifierKeyBackfill {

    /** What a run did, in counts: never a value. */
    public record Result(int rows, int emailsKeyed, int emailsInvalid, int emailsInCollision, int phonesKeyed,
                         int phonesInvalid, int phonesInCollision) {
    }

    private record Keys(String id, String emailKey, String phoneKey) {
    }

    private static final int PAGE_SIZE = 1000;

    private static final String READ_PAGE = "SELECT id, email, username FROM users WHERE deleted_at IS NULL "
            + "AND (email IS NOT NULL OR username IS NOT NULL) AND id > ? ORDER BY id LIMIT " + PAGE_SIZE;

    private static final String DROP_EMAIL_COLLISIONS = """
            UPDATE user_identifier_keys k SET email_key = NULL
            WHERE k.email_key IS NOT NULL AND (
                k.email_key IN (SELECT email_key FROM user_identifier_keys WHERE email_key IS NOT NULL
                                GROUP BY email_key HAVING count(*) > 1)
                OR EXISTS (SELECT 1 FROM users u WHERE u.deleted_at IS NULL AND u.email_key = k.email_key AND u.id <> k.id))
            """;

    private static final String DROP_PHONE_COLLISIONS = """
            UPDATE user_identifier_keys k SET phone_key = NULL
            WHERE k.phone_key IS NOT NULL AND (
                k.phone_key IN (SELECT phone_key FROM user_identifier_keys WHERE phone_key IS NOT NULL
                                GROUP BY phone_key HAVING count(*) > 1)
                OR EXISTS (SELECT 1 FROM users u WHERE u.deleted_at IS NULL AND u.phone_key = k.phone_key AND u.id <> k.id))
            """;

    private static final String WRITE_EMAIL_KEYS = "UPDATE users u SET email_key = k.email_key FROM user_identifier_keys k "
            + "WHERE u.id = k.id AND k.email_key IS NOT NULL AND u.email_key IS NULL";

    private static final String WRITE_PHONE_KEYS = "UPDATE users u SET phone_key = k.phone_key FROM user_identifier_keys k "
            + "WHERE u.id = k.id AND k.phone_key IS NOT NULL AND u.phone_key IS NULL";

    private final UnaryOperator<String> emailKey;
    private final UnaryOperator<String> phoneKey;

    public UserIdentifierKeyBackfill() {
        this(raw -> Email.create(raw).getValue(), raw -> PhoneNumber.create(raw).key());
    }

    /** @param emailKey and {@code phoneKey} give the key of a stored value; only {@link AuthValidationException} means none */
    public UserIdentifierKeyBackfill(UnaryOperator<String> emailKey, UnaryOperator<String> phoneKey) {
        this.emailKey = emailKey;
        this.phoneKey = phoneKey;
    }

    /** @throws IllegalStateException if rows hold values and nothing was keyed: a broken parser must not commit "all invalid" */
    public Result run(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL lock_timeout = '5s'");
            // No write may slip in between the read and the update
            statement.execute("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE");
            statement.execute("CREATE TEMP TABLE user_identifier_keys (id VARCHAR(26) PRIMARY KEY, email_key VARCHAR(254), "
                    + "phone_key VARCHAR(16)) ON COMMIT DROP");
        }

        int rows = 0;
        int emailsInvalid = 0;
        int phonesInvalid = 0;
        String afterId = "";
        List<Keys> keys = new ArrayList<>(PAGE_SIZE);
        boolean more = true;
        while (more) {
            keys.clear();
            int read = 0;
            try (PreparedStatement page = connection.prepareStatement(READ_PAGE)) {
                page.setString(1, afterId);
                try (ResultSet row = page.executeQuery()) {
                    while (row.next()) {
                        read++;
                        afterId = row.getString("id");
                        String email = row.getString("email");
                        String username = row.getString("username");
                        String emailValue = email == null ? null : keyOf(emailKey, email);
                        String phoneValue = username == null ? null : keyOf(phoneKey, username);
                        emailsInvalid += email != null && emailValue == null ? 1 : 0;
                        phonesInvalid += username != null && phoneValue == null ? 1 : 0;
                        if (emailValue != null || phoneValue != null) {
                            keys.add(new Keys(afterId, emailValue, phoneValue));
                        }
                    }
                }
            }
            insert(connection, keys);
            rows += read;
            more = read == PAGE_SIZE;
        }

        int emailsInCollision = update(connection, DROP_EMAIL_COLLISIONS);
        int phonesInCollision = update(connection, DROP_PHONE_COLLISIONS);
        int emailsKeyed = update(connection, WRITE_EMAIL_KEYS);
        int phonesKeyed = update(connection, WRITE_PHONE_KEYS);

        if (rows > 0 && emailsKeyed + phonesKeyed == 0) {
            throw new IllegalStateException("The identifier backfill keyed nothing although rows hold values.");
        }
        Result result = new Result(rows, emailsKeyed, emailsInvalid, emailsInCollision, phonesKeyed, phonesInvalid,
                phonesInCollision);
        log.info("auth.backfill.identifier-keys rows={} emails-keyed={} emails-invalid={} emails-in-collision={} "
                        + "phones-keyed={} phones-invalid={} phones-in-collision={}", result.rows(), result.emailsKeyed(),
                result.emailsInvalid(), result.emailsInCollision(), result.phonesKeyed(), result.phonesInvalid(),
                result.phonesInCollision());
        return result;
    }

    private static String keyOf(UnaryOperator<String> parser, String value) {
        try {
            return parser.apply(value);
        } catch (AuthValidationException e) {
            return null;
        }
    }

    private static void insert(Connection connection, List<Keys> keys) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO user_identifier_keys (id, email_key, phone_key) VALUES (?, ?, ?)")) {
            for (Keys key : keys) {
                insert.setString(1, key.id());
                insert.setString(2, key.emailKey());
                insert.setString(3, key.phoneKey());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static int update(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }
}
