-- Counts every loss of sign-in of an account (property-create intake spec 5.2 file 13, D5-10, US-2 AC-3). A proof of ownership
-- stores the count it read and is live only while the count is equal, so a lock followed by an unlock still kills it, with
-- no two clocks to compare. No application code writes LOCK today (an administrator sets it in the database), so a Java
-- stamp would miss that path: a trigger sees every writer. This is the schema's first trigger.
-- The column: ADD COLUMN with a constant default changes metadata only, no row is rewritten; the ACCESS EXCLUSIVE lock is
-- short. UserEntity does not map it, so a save never overwrites it. The WHEN holds the whole condition, so a column rename
-- follows it: the old row could sign in and the new row cannot. The status list is SIGN_IN_BLOCKED_STATUSES, and
-- SignInLossTriggerPostgresTest pins the two together. A save that writes the same status counts nothing. A temporary login
-- lock (locked_until) and a password reset touch other columns and count nothing.
-- It exists on PostgreSQL only, so its tests run there. The count never goes down; it is no measure of anything else.
SET LOCAL lock_timeout = '5s';

ALTER TABLE users ADD COLUMN sign_in_losses INTEGER NOT NULL DEFAULT 0;

CREATE FUNCTION users_count_sign_in_loss() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    NEW.sign_in_losses := OLD.sign_in_losses + 1;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_count_sign_in_loss
    BEFORE UPDATE OF user_status, deleted_at ON users
    FOR EACH ROW
    WHEN (OLD.deleted_at IS NULL AND OLD.user_status NOT IN ('LOCK', 'TERMINATED', 'DELETED')
        AND (NEW.deleted_at IS NOT NULL OR NEW.user_status IN ('LOCK', 'TERMINATED', 'DELETED')))
    EXECUTE FUNCTION users_count_sign_in_loss();
