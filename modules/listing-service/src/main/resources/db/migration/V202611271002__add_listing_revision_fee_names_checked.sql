-- Whether a revision's fee names passed the machine check of listing-publish BR-2 at its submission (E06-S7 spec
-- E06S7-D11, LP-15): every existing row reads FALSE, a submission writes TRUE, a copy starts FALSE. A live revision whose
-- flag is FALSE gets its fee names checked once when its publication row is built, and none is written if a name fails.
-- Cost on a populated table (flyway.md): listing_revisions holds the E2E revisions on staging. A BOOLEAN with the
-- constant default FALSE: metadata only, no rewrite; the ACCESS EXCLUSIVE lock is short and bounded by lock_timeout. No
-- CHECK, so no VALIDATE file and no recovery step. Forward fix: expand-only, a fix is a new file.
SET LOCAL lock_timeout = '5s';

ALTER TABLE listing_revisions ADD COLUMN fee_names_checked BOOLEAN NOT NULL DEFAULT FALSE;
