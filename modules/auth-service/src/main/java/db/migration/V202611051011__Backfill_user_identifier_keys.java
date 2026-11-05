package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import vn.sunvibe.auth.core.infrastructure.persistence.migration.UserIdentifierKeyBackfill;

import java.sql.SQLException;

/**
 * Writes {@code email_key} and {@code phone_key} of existing rows (auth-email-access spec 5.3). In Java because the keys
 * come from the one policy of {@code Email} and {@code PhoneNumber}, which SQL can only approximate.
 * <p>
 * Runs in Flyway's transaction on its connection, so a failure rolls it back and stops the start-up. Cost on a populated
 * table: {@code users} takes no writes while it runs, reads go on; two passes touch every index of the table and leave dead
 * rows, so {@code VACUUM ANALYZE users} follows. The time grows with the rows and is unmeasured until the rehearsal (spec
 * O-1b). A fresh database has no rows, and this does nothing.
 */
public class V202611051011__Backfill_user_identifier_keys extends BaseJavaMigration {

    /** Flyway keeps no checksum of a class by itself; a constant states that this file never changes. */
    private static final int CHECKSUM = 1;

    @Override
    public Integer getChecksum() {
        return CHECKSUM;
    }

    @Override
    public void migrate(Context context) throws SQLException {
        new UserIdentifierKeyBackfill().run(context.getConnection());
    }
}
