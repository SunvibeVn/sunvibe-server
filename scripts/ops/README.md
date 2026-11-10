# Survey photo operations runbook

Operations on survey photos (survey-media spec A-23, A-24, A-29, 9.4). Every step goes through the API or a
configuration switch; direct SQL on the hold columns is not the procedure (spec A-29).

## 1. Containment order for an incident

1. **Stop new work on files.**
   - A decoder or scanner problem (for example a decoder CVE): set `app.survey.media.processing-enabled=false` and
     restart. The worker stops, intake pauses (`PROCESSING_OFF`), reads go on.
   - A wide incident (photos that must not be shown at all): set `app.survey.media.serving-enabled=false` and restart
     (section 3). Every content read answers 503 `MEDIA_SERVING_DISABLED`; the lists still work.
2. **List the affected photo ids**, as lines `checklistId mediaId`: by `processor_version` of their variants, or by
   id from the incident report. Read-only SQL on `survey_media` and `survey_media_variants` is fine for this list.
3. **Hold them** with `hold-media.sh` (section 2), reason `SECURITY_INCIDENT`, or `CREDENTIAL_EXPOSURE` when a
   Sunvibe system secret shows in a photo or its caption.
4. **Switch back on** what step 1 turned off, and restart.
5. **Release after the review**, one photo at a time, with `POST /api/v1/staff/survey-media/{mediaId}/hold-release`
   and `{}` as an ADMIN. Release a `CREDENTIAL_EXPOSURE` hold only after the exposed secret is rotated; its caption
   stays hidden from non-ADMIN viewers after the release. A removed photo's content deletion is requested at its
   release (done within 24 hours).

## 2. Bulk hold: `hold-media.sh`

```bash
scripts/ops/hold-media.sh affected.txt SECURITY_INCIDENT https://api.sunvibe.vn
# it asks for the access token of an ADMIN account and does not show it (read -rs)
```

- `affected.txt`: one `checklistId mediaId` per line; blank lines and `#` comments are skipped; an id listed twice is
  held once.
- One hold request per id and no other request (no list read, which would count in the ADMIN's read budget).
- Each id is reported as `held` (the photo is held now), `already-held <REASON>` (it was held earlier with that other
  reason, which stays: a hold on a held photo is a no-op that keeps the first reason, so check before releasing a
  `CREDENTIAL_EXPOSURE` hold), `not-found` or `refused <status>`; a summary line follows. The exit status is 1 when
  any id was not found or refused (re-run the script with those lines after checking them), 2 on a usage error or a
  missing token.
- Each hold is checked, versioned and written as one security event by the server (spec A-23), exactly like a hold
  from the page.
- The token is typed at the script's prompt (not shown, never in the shell history), or taken from
  `SUNVIBE_ADMIN_TOKEN` when that is already set (to set it by hand: `read -rs SUNVIBE_ADMIN_TOKEN; export
  SUNVIBE_ADMIN_TOKEN`, never `export SUNVIBE_ADMIN_TOKEN=<token>`). It reaches `curl` through a config on stdin: it is
  never printed, stored or put on a command line. The base URL must be `https://` (`http://` only for this machine).
  Needs `bash` and `curl`.

## 3. Serving switch restart

`app.survey.media.serving-enabled` is read at start-up. To change it, set the property (environment variable
`APP_SURVEY_MEDIA_SERVING_ENABLED`) and restart every instance. While it is off, content reads answer 503 with
`Retry-After`, lists show `servingEnabled: false`, and uploads, processing, removal, hold and clean-up go on.

## 4. Reconciliation after a database restore

A restore can bring back photo rows whose stored objects were deleted after the backup (spec 5.1).

1. **When:** for the first start after a database restore only. Keep uploads off
   (`app.survey.media.upload-enabled=false`) and set `APP_SURVEY_MEDIA_RECONCILE_AFTER_RESTORE=true` on one instance.
2. **What it does:** at start-up it lists `media/<mediaId>/` for every READY photo not yet marked and marks the
   photos with a missing variant `CONTENT_MISSING` (not served; the page shows them as not available). It deletes
   nothing and logs counts only (`Survey media restore reconciliation: N READY photo(s) checked, M marked ...`).
   It marks nothing, and logs `restore reconciliation stopped`, when the store does not answer its probe, when the
   media bucket lists nothing while READY photos exist, or when more than half of at least 20 photos with variants
   miss every variant: those are the signs of a wrong bucket or wrong credentials. Fix the store settings and start
   again. Only when the loss is real and confirmed, set `APP_SURVEY_MEDIA_RECONCILE_AFTER_RESTORE_FORCE=true` for that
   one start as well.
3. **How long:** one store listing per READY photo. Measured on the local test store (Task A22): 10,000 photos in
   about 9 seconds; on R2 each listing is one request, so expect minutes per 10,000 photos (and one Class A
   operation each).
   Before the start, take the start time from the database, five minutes early for clock skew between the API and the
   database (the mark is written with the API's clock), in the psql session you use for the later queries:
   `SELECT now() - interval '5 minutes' AS start_time;` (it carries its offset; a time typed without one is read in the
   session's time zone). Note the count of photos it may mark:
   `SELECT count(*) FROM survey_media WHERE status = 'READY' AND content_missing_at IS NULL AND content_deleted_at IS NULL;`
   after it: `SELECT count(*) FROM survey_media WHERE content_missing_at >= '<start time>';` with the value of
   `start_time` as written by psql.
4. **Undo a mistaken run:** the application never clears the mark. Only this job writes `content_missing_at`, so
   one run's marks are found by its start time; in one transaction:
   `UPDATE survey_media_sets SET set_version = set_version + 1 WHERE checklist_id IN (SELECT checklist_id FROM
   survey_media WHERE content_missing_at >= '<start time>');`
   `UPDATE survey_media SET content_missing_at = NULL, version = version + 1 WHERE content_missing_at >= '<start time>';`
5. **Then:** remove the property (and the force setting) and restart (they must not stay on). Let the sweep and the daily full listing remove
   the orphans (objects no restored row references); check that no `ORPHAN_OVERDUE` incident stays open. Tell the
   holders of the marked photos to upload them again as new photos.

## 5. Index recovery

The survey-media migrations create their indexes inside the migration's transaction (no `CREATE INDEX
CONCURRENTLY`), so a failed migration rolls back and leaves no invalid index: nothing to recover by hand. Re-run the
deployment after fixing the cause.

## 6. Intake stays paused after its cause is fixed

A sweep that finds everything clean still keeps intake paused while a full listing run holds orphans it could not
delete (spec A-18, 4.4 step 4, SM-14): the open run with `undeleted_count > 0`, or the latest finished run when it
listed to the end with `undeleted_count > 0`. The sweeper then logs "intake stays paused: a full listing run kept
orphans it could not delete" every 5 minutes, and the listing logged the orphans it could not delete (run id, photo
id, error code; at most 10 per page, then the page's count). The block ends by itself when the next run ends: listing
to the end decides with its own count, and a run abandoned after 24 hours blocks nothing, so no run blocks for ever
(at worst about two days from the blocking run's start: the next run starts 24 hours after it, and is abandoned 24
hours later). An abandoned run lifts the block without those deletions confirmed; the next complete run finds them
again. A run also pauses intake (`DELETION_OVERDUE`) and opens or counts in an `ORPHAN_OVERDUE` incident with each
page that finds an orphan over 24 hours, so a pause can come back within one run after a sweep resumed. Read only (no
write to these tables is part of this procedure):

```sql
-- the runs, newest first: OPEN, COMPLETE (listed to the end) or ABANDONED
SELECT id, started_at, finished_at, keys_seen, undeleted_count,
       CASE WHEN finished_at IS NULL THEN 'OPEN' WHEN start_after_key IS NULL THEN 'COMPLETE' ELSE 'ABANDONED' END
FROM survey_media_listing_runs ORDER BY started_at DESC, id DESC LIMIT 10;
-- the two parts of the block, as the sweeper reads them
SELECT EXISTS (SELECT 1 FROM survey_media_listing_runs WHERE finished_at IS NULL AND undeleted_count > 0) AS open_blocks,
       COALESCE((SELECT start_after_key IS NULL AND undeleted_count > 0 FROM survey_media_listing_runs
                 WHERE finished_at IS NOT NULL ORDER BY started_at DESC, id DESC LIMIT 1), false) AS finished_blocks;
-- when the next run is due (the hourly poll starts it)
SELECT max(started_at) + interval '24 hours' AS next_run_due FROM survey_media_listing_runs;
-- the pause itself
SELECT intake_paused_since, pause_reason, failure_seq, last_sweep_ok_at FROM survey_media_health WHERE id = 1;
```

Fix what made the deletes fail first (R2 status, the token still Object Read & Write on the media bucket, no bucket
lock). Several ABANDONED runs in a row mean the listing cannot finish within 24 hours: that is the 4.12 upgrade
trigger far exceeded, or a page that fails every time (see the run's "stopped a run" error log), and it goes to the
owner. Changing `undeleted_count`, `start_after_key`, `started_at` or `finished_at` by hand is not the procedure: they
encode the run's outcome and the next due time.

## 7. Go-live and deploy checks

Before uploads are turned on in production (spec 9.4), and at each deploy where noted:

- **clamd timeout and threads** (deployed values, not the committed ones; `ClamdTimeoutRelationTest` checks only the
  defaults): the API's `app.survey.media.clamav.timeout` (default 30 s) is at least clamd's `MaxScanTime` + 10 s
  (`docker/clamav/clamd.conf`: 20000 ms), and clamd's `MaxThreads` (there 8) is at least the scans that can run at once
  plus 1 for the probe: the worker concurrency (`app.survey.media.worker.concurrency`, default 2) times the number of
  API instances, + 1. A scan waiting for a clamd thread counts against the client timeout. Read the deployed values
  with `docker exec <clamd container> clamconf | grep -E 'MaxScanTime|MaxThreads'` and the API's environment.
- **`survey_access_logs` size before `V202611021403`** (the deploy that brings the media-list index): the index is
  built inside the migration's transaction and holds a SHARE lock on the table (writes wait, reads go on) while it
  builds; it took about 5 s on 12 M rows. The migration waits at most 5 s for that lock (`lock_timeout`), during which
  writes queue too, then fails and the deploy rolls back. Check the size first (the estimate is -1 on a table never
  analyzed; then use `SELECT count(*) FROM survey_access_logs;`):
  `SELECT c.reltuples::bigint, s.n_live_tup, s.last_autoanalyze FROM pg_class c JOIN pg_stat_user_tables s ON
  s.relid = c.oid WHERE c.oid = 'survey_access_logs'::regclass;` Above about 10 M rows, deploy in a quiet window.
- **The checklist review deploy (`V202611041530` to `V202611041534`; owner decision 2026-11-04):** stop every old API
  instance before the new one starts, in a quiet window; no overlap. `V202611041530` and `V202611041532` change the type
  of `owner_lead_activities.reference_detail` and `survey_checklists.status`, and an old instance that keeps serving can
  fail reads with `cached plan must not change result type`. `V202611041532` also takes four table locks in one
  transaction: reads of `survey_checklists` stop while it runs, and each lock wait is bounded by the 5 s `lock_timeout`,
  after which the file rolls back. These times were not measured.
  1. Before: nothing long-running should hold these tables; every row of this query can make a lock wait fail:
     `SELECT pid, state, now() - xact_start AS age FROM pg_stat_activity WHERE xact_start IS NOT NULL AND pid <>
     pg_backend_pid() AND now() - xact_start > interval '2 seconds';`
  2. Start the new instance with `app.survey.checklist.submission-enabled` false (the default).
  3. After: `SELECT conrelid::regclass, conname FROM pg_constraint WHERE NOT convalidated;` answers no row, then run
     `ANALYZE survey_checklists; ANALYZE owner_lead_activities;` (the type change dropped their column statistics).
  4. Turn the switch on only when no old instance can come back: there is no rollback after the first submit or
     QUALIFIED.
- **The requalification deploy (`V202611051940` to `V202611051942`; property-create requalification spec 5.2, 7):**
  stop every old API instance before the new one starts, in a quiet window; no overlap. An old instance that keeps
  serving can fail native `select *` reads of `survey_checklists` with `cached plan must not change result type` (not
  reproduced), and after a first mark it reads a withdrawn PASSED as current. `V202611051940` holds an ACCESS EXCLUSIVE
  lock on `survey_checklists` (reads stop) for catalog work only; `V202611051941` scans the table with reads and writes
  going on; `V202611051942` builds a unique index under a SHARE lock (writes wait), a full scan of a table with one row
  per revision, justified by its small size before go-live. Each lock wait is bounded by the 5 s `lock_timeout`, after
  which the file rolls back and a restart retries it. The run times are not bounded and were not measured (SV-090).
  1. After the stop, before the start: the first two queries must answer no row, and the third gives the size to note.
     A row in the first makes `V202611051942` fail with 23505 and the new binary cannot start: do not start it, take it
     to the owner. There is no repair template: the command that marks a revision exists only in the new binary. A row
     in the second: wait for that session to finish, then run the query again. If the table is no longer small, take
     the build time of the index to the owner too.
     `SELECT lead_id, intent_cycle, count(*) FROM survey_checklists WHERE status = 'PASSED' GROUP BY 1, 2 HAVING
     count(*) > 1;`
     `SELECT l.pid, l.mode, l.granted, a.state, now() - a.xact_start AS age FROM pg_locks l JOIN pg_stat_activity a
     USING (pid) WHERE l.relation = 'survey_checklists'::regclass AND l.pid <> pg_backend_pid();`
     `SELECT count(*), pg_size_pretty(pg_total_relation_size('survey_checklists')) FROM survey_checklists;`
  2. Start the new instance. A failed file stays out of the history and a restart retries it.
  3. After, before traffic: three rows with `success` true, whose `execution_time` (ms) is the measurement to keep;
     no unvalidated constraint; the index valid and unique:
     `SELECT version, success, execution_time FROM flyway_schema_history WHERE version IN ('202611051940',
     '202611051941', '202611051942');`
     `SELECT conrelid::regclass, conname FROM pg_constraint WHERE NOT convalidated;`
     `SELECT i.indisvalid, i.indisready, i.indisunique FROM pg_index i WHERE i.indexrelid =
     'uq_survey_checklists_current_passed_per_cycle'::regclass;`
  4. Reconciliation, at any time after the first mark; each must answer no row (the second finds a mark without its
     row, or a row without its mark):
     `SELECT lead_id, intent_cycle, count(*) FROM survey_checklists WHERE status = 'PASSED' AND requalified_at IS NULL
     GROUP BY 1, 2 HAVING count(*) > 1;`
     `SELECT c.id, c.requalified_at, r.marked_at FROM survey_checklists c LEFT JOIN survey_checklist_requalifications r
     ON r.checklist_id = c.id WHERE c.requalified_at IS DISTINCT FROM r.marked_at;`
  5. An older binary is safe only before the first mark (not rehearsed); after it, fix forward only. Before any
     rollback all three must be 0: `SELECT (SELECT count(*) FROM survey_checklist_requalifications) AS rows, (SELECT count(*) FROM
     survey_checklists WHERE requalified_at IS NOT NULL) AS marks, (SELECT count(*) FROM owner_lead_activities WHERE
     type = 'REQUALIFICATION_MARKED') AS history;`
- **The processor at start-up:** each API instance retries the processor self-probe every 5 s for about a minute
  before it records a PROCESSOR pause and runs without a worker. The worker then starts by itself, with no restart,
  within a minute of the first processor probe of that instance that passes; the log says `this instance now
  processes`. That probe is the re-run of an incomplete one, within a minute, or else the next sweep, about 5 minutes
  plus the sweep's own run. No compose file here starts the API; a deployment that does starts the processor first
  (`depends_on`) so the probe passes within that minute.
- **The spool** (`app.survey.media.spool-dir`, on tmpfs or a volume outside backups): each instance keeps its bodies
  in a subdirectory named by its instance id and locks it while it runs, so instances may share the directory; set
  `app.survey.media.instance-id` per instance when its logs should name it across restarts.

## 8. Auth identifier backfill (auth-email-access spec 5.3, 8)

Both scripts are read-only, print counts only and run in a `READ ONLY` transaction.

- `psql "$DATABASE_URL" -f scripts/ops/auth-identifier-preflight.sql` before the release (O-1), also on the old schema. The
  counts are indicative; the rehearsal on a restored copy with the release bootJar decides (O-1b).
- Run the preflight again once the old server has stopped, and keep that `row_hash`: `last_login` is part of the hash, so one
  taken while the old server still answers cannot match.
- `psql "$DATABASE_URL" -f scripts/ops/auth-identifier-verify.sql` after the migration (O-3), before traffic reaches the new
  server: a sign-in moves the hash and a new account moves the counts. It exits non-zero if the backfill is not recorded, if
  rows hold values and no key exists, if a deleted row holds a key, if a key differs from its email or if a canonical value is
  shared with a key. Compare `row_hash` with that preflight, and `emails_keyed`, `phones_keyed` with the log line
  `auth.backfill.identifier-keys`.
- If rows hold values and no key was written, the migration did not run or did not take: stop (spec 7.5).
- The rehearsal times every file: `V202611051000` above all, which holds an ACCESS EXCLUSIVE lock on `users` through the
  rebuild of the partial `idx_users_email`, and the backfill, whose two passes touch every index of `users`. Note the size
  of `users` before and after.
- After the verify, `VACUUM ANALYZE users`: the backfill leaves dead rows, and `email_key` and `phone_key` have no statistics.

## 9. Auth mail through Brevo SMTP (auth-email-access spec 5.6, O-2)

Every auth mail leaves through `EmailService` on the one `spring.mail.*` sender: the sign-up code, the bind code, the reset
mail, the reset confirmation and the email-changed notice. No code or template names the provider, so Brevo is a
configuration change. Local and E2E runs stay on Mailpit (`scripts/dev/run-e2e-auth-api.*`).

Templates without secrets: `scripts/ops/env/brevo-staging.env.example` and `brevo-production.env.example`. Set the values in
the deployment system of the environment, not in a file: this repository is public. `<ref: name>` is the name of a secret
store entry. The names are proposals, so keep the ones your store uses and record those, never the values.

**Before these steps:** a Brevo account owned by Sunvibe with transactional sending on, `sunvibe.vn` authenticated and
`services@sunvibe.vn` set up as a sender (hub rollout plan, section 2.3). Without them a mail the relay accepts may never
arrive. Turn on two-factor sign-in for that account as well.

**Fill in the values, once per environment:**

1. Set every line of the template that has no placeholder. `MAIL_HOST` first: unset, it falls back to another provider, and
   the login and key go there. `MAIL_STARTTLS` stays `true`: it makes TLS mandatory before the login is sent.
2. In Brevo, open Settings > SMTP & API > SMTP. Copy the login exactly as the page shows it into the entry behind
   `MAIL_USERNAME`. It can be the account email or an address ending in `@smtp-brevo.com`, so do not guess it. It is not
   `MAIL_FROM`: the login opens the relay, the sender is what the recipient sees.
3. On the same page generate an SMTP key named after the environment. Brevo shows it once. Paste it straight into the entry
   behind `MAIL_PASSWORD`, never into a chat, a terminal or a PR. An API key does not work here. Staging and production
   each get their own key. Confirm in Brevo what one key can reach: on a shared account a leaked staging key could send as
   `services@sunvibe.vn` and spend the production quota, so prefer a separate account for staging, or guard the staging
   entry like the production one.
4. Staging: set `AUTH_FRONTEND_BASE_URL` and `APP_CORS_ALLOWED_ORIGINS` to the real staging origin, https, no path, no
   trailing slash, the two equal. Production: `AUTH_FRONTEND_BASE_URL=https://www.sunvibe.vn`, and leave
   `APP_CORS_ALLOWED_ORIGINS` unset. `application-prod.yml` holds that allow-list, so a staging URL copied here stops the
   start-up instead of sending reset links to staging.
5. Set `AUTH_MAIL_MAX_PER_HOUR`: turn the limit of the plan into an hourly figure, subtract other traffic on the account and
   a reserve, and divide by the most processes planned. The counter resets on restart and does not replace the quota view
   in Brevo.
6. In Brevo, confirm `Never store previews`, the log retention and who can read the logs. A stored preview shows the reset
   code and link together.
7. Restart every instance, then run the smoke test below.
8. Once the smoke test passes, delete any earlier sender credential from the store and revoke it at its provider, and record
   the date in the hub issue register. It is never a fallback. An earlier credential that may be exposed does not wait for
   this step: revoke it now, as in Rotate the key.

**The start-up stops on** (measured on the bootJar, profile `prod`):

| Mistake | Message |
|---|---|
| Base URL outside the allow-list: a staging host without `APP_CORS_ALLOWED_ORIGINS`, a staging URL in the production template, or the staging template with its two frontend lines left as written | `app.auth.frontend-base-url is not in app.cors.allowed-origins` |
| `AUTH_MAIL_MAX_PER_HOUR` is not a number | `NumberFormatException` |
| `MAIL_USERNAME` and `MAIL_FROM` both unset | `Could not resolve placeholder 'MAIL_USERNAME'` |

**It starts and then fails or misleads:**

| Mistake | Result |
|---|---|
| `MAIL_USERNAME` or `MAIL_PASSWORD` unset | Sends fail at sign-in to the relay: `class=MailAuthenticationException` (measured). A `<ref:>` left in place should fail the same way (not run) |
| `MAIL_FROM` empty | Every send fails: `class=MailParseException` on sign-up, `class=AddressException` on the reset mail (measured) |
| `MAIL_FROM` unset | The SMTP login becomes the sender (measured) |
| `AUTH_FRONTEND_BASE_URL` and `APP_CORS_ALLOWED_ORIGINS` both unset on staging | Reset links point at production (measured) |
| `MAIL_STARTTLS=false` | The client never asks for TLS, even from a server that offers it: the login goes in clear to any server that takes AUTH first (measured) |
| `MAIL_HOST` unset | `application.yml` sends the login and key to `smtp.gmail.com` (read from the configuration, not run). It shows as `MailAuthenticationException`: fix the host first, then replace that key as exposed (see Rotate the key) |
| `AUTH_MAIL_MAX_PER_HOUR` unset | 300 per hour (read from the configuration) |

A sender that fails gives sign-up and bind a 503, but recovery still answers 200: a broken recovery shows only in the log
and the audit table.

**Smoke test, after every change of these values, of the Brevo account or of the plan.** One sign-up and one recovery to a
test mailbox, 60 s apart at least for recoveries of one address (five a day), because a failed recovery still starts the
cooldown. Both mails must come from `services@sunvibe.vn`. Then, without clicking, read the raw reset mail. It is
quoted-printable, so decode it first: `=3D` is `=`, and a line ending in `=` continues on the next one. The `href` must start
with the real web origin of this environment, taken from the environment record and not from the variable (production:
`https://www.sunvibe.vn`), then `/reset-password#token=`. Any other host is a stop: O-2 is not passed, and a click would hand
the token to that host. Keep only the URL without its fragment as evidence. Then read the log for the signals below, and count
the audit failures in a `READ ONLY` transaction. The rows hold IP addresses and user agents, so they never go into evidence:

```sql
SELECT count(*) FROM auth_audit_events WHERE details LIKE '%SEND_FAILED%' AND occurred_at > now() - interval '1 hour';
```

| Signal | Meaning |
|---|---|
| `auth.mail.sent`, but nothing in the mailbox | The relay took it: read the transactional log in Brevo (sender, domain, blocked, deferred) |
| `auth.mail.failed ... class=MailAuthenticationException` | Wrong or revoked SMTP login or key, or a wrong `MAIL_HOST` |
| `auth.mail.failed ... class=MailSendException` | The relay refused or stayed silent, could not be reached, or failed the TLS check |
| `auth.mail.refused reason=budget` | `AUTH_MAIL_MAX_PER_HOUR` is spent on this process; check for abuse before raising it (spec R-7) |
| `auth.mail.refused reason=bulkhead` | Four sends are busy (spec R-6, SV-123) |
| `auth.mail.queue-full` | The mail queue is full: a recovery mail or confirmation was lost, or a notice run was skipped |
| `auth.mail.notice-given-up` | A notice to an old address was dropped after its retries |
| `EMAIL SEND_FAILED` in `auth_audit_events` | A reset mail or a reset confirmation was lost; the user saw a normal answer |

A silent relay cost 5 s when measured, and 10.6 s when it stopped after the message body. These are samples, not a ceiling: a
slow relay has no hard bound, and sign-up and bind hold a database connection meanwhile (spec R-6).

While diagnosing, never set `mail.smtp.ssl.trust`, which switches off the certificate checks, nor `mail.debug` in a shared
environment, which can write the mail to the log.

**Rotate the key.** Planned: generate a new key in Brevo, replace the value of the secret store entry, restart every
instance, run the smoke test, delete the old key the same day, and record the date. If a key may be exposed, delete it in
Brevo first: sign-up answers 503 and recovery mail is lost until the new key is live, and that is the smaller harm.

**Check the path before there is a key.** This needs no credential and sends no mail. From the runtime of the environment
(a slim image may have no `openssl`: then from a host on the same network), look for `AUTH` in the answer:

```bash
printf 'EHLO test\nQUIT\n' | openssl s_client -quiet -crlf -starttls smtp -connect smtp-relay.brevo.com:587 \
  -servername smtp-relay.brevo.com -verify_return_error -verify_hostname smtp-relay.brevo.com
```

**Rehearse without Brevo.** A relay that demands AUTH and STARTTLS. Every value here is invented for the run: a throwaway
local PostgreSQL, invented keys of the right shape for each `<ref:>` (base64, at least 32 bytes, the two ID card keys exactly 32 and different), an
SMTP login and key that match `smtp-auth.txt`. Never an entry of a secret store, and never a shared database: the jar runs
Flyway on it and its notice job would consume real rows. Keep `certs/` outside the checkout.

```bash
# certs/: a throwaway CA, relay.crt and relay.key for DNS:localhost signed by it, smtp-auth.txt with one line login:key
docker run -d --rm --name sv-smtp-relay -p 127.0.0.1:2525:1025 -p 127.0.0.1:8026:8025 -v "$PWD/certs:/certs:ro" \
  axllent/mailpit:v1.22.3 --smtp-tls-cert /certs/relay.crt --smtp-tls-key /certs/relay.key --smtp-require-starttls \
  --smtp-auth-file /certs/smtp-auth.txt --enable-chaos
```

Start the bootJar on a template with `MAIL_HOST=localhost`, `MAIL_PORT=2525`, a numeric budget and, for staging, both
frontend lines, on a JVM that trusts the CA for this run only (`-Djavax.net.ssl.trustStore=...`, with its type and password). For an SMTP
refusal, `PUT http://127.0.0.1:8026/api/v1/chaos` with `{"Recipient":{"ErrorCode":550,"Probability":100}}`; for a silent
relay, `docker pause sv-smtp-relay`.

A rehearsal proves what Sunvibe does, not what Brevo does. Delivery, DNS, link tracking and egress from the runtime are
release evidence (spec O-2), tracked in the hub file
`docs/features/auth/auth-email-access/impl/2026-11-05-auth-email-access-brevo-evidence.md`.

## 10. Test environment on one VM (W35a)

The free test environment of hub plan `docs/rebuild/implementation/2026-11-09-w35a-test-env-plan.md`: the API and
PostgreSQL 17 on one Oracle Cloud Always Free VM (Singapore, `VM.Standard.A1.Flex`, 2 OCPU, 6 GB, Ubuntu 24.04 aarch64) at
`https://api-test.sunvibe.vn`, for the web at `https://test.sunvibe.vn`. Files: `docker/api/Dockerfile`,
`docker/test-env/compose.yaml` and `Caddyfile`, `scripts/ops/env/test-env.env.example`, `scripts/ops/test-env/`.

**Limits.** Test data only. One VM: no HA, no point-in-time recovery, and the nightly dumps sit on the same VM, so losing
the VM loses the data. Sign-up is open to anyone who finds the site (owner decision OD-1): each account leaves a real email,
a password hash, and its IP and user agent in the auth audit for 180 days, in Singapore. This does not open sign-up on
`www`. Survey photo upload is off (no store, scanner or processor runs); direct property creation is on, so visitors can
enter property data too. Production hosting and hardening Task A5 come before any real user.

**Shape.** Caddy is the only public entry (80 and 443, a Let's Encrypt certificate, http redirected to https). Every answer
of the API carries `X-Robots-Tag: noindex` and no `Server` header; Caddy's own answers (the redirect, a 502 while the API
restarts) keep `Server: Caddy` and carry no `X-Robots-Tag`, and neither is indexed. The API and PostgreSQL publish no port.
Caddy has the fixed address `172.30.0.10` on the compose network (the other containers get addresses from `.128/25` only),
and the API trusts forwarded headers from that address only (`SERVER_FORWARD_HEADERS_STRATEGY=native` and
`SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` in the env file; change the address in both files together; never `framework`,
which trusts every client's headers). Caddy replaces a client's `X-Forwarded-For`, `-Proto` and `-Host` and drops its
`X-Forwarded-Port`, `X-Forwarded-Prefix`, `Forwarded` and `X-Real-IP`. Without those two env lines every client shares
Caddy's rate-limit bucket; `smoke.sh` checks (c) and (d) show both. Ports 80 and 443 are published on IPv4 only: on IPv6
every client would come through Docker's proxy with one address and share one bucket, so give the VCN no IPv6 and add no
AAAA record. The API runs with a fixed 1.5 GB heap touched at start and `TZ=Asia/Ho_Chi_Minh`, so the nightly auth jobs run at night in
Vietnam.

**Secrets (plan D-8).** One env file outside the checkout, `/opt/sunvibe/test.env`, owned by root, mode 600, made and
filled by `gen-secrets.sh`. The owner types the Brevo login and key at its hidden prompt in their own SSH terminal; they
never go into a chat, a file in the repo or a PR. `gen-secrets.sh --check` (key names with `filled` or `missing`) is the only
way a session looks at the file. Only the `postgres` service gets the `POSTGRES_*` values; the api service gets the whole
file, so the java process holds every value in its environment.

A session on the VM runs only the commands this section gives (firewall, first start, update, logs, backup, restore, the
first staff account, teardown; `gen-secrets.sh` never under `bash -x`, which traces the values), `docker compose ps`,
`restart` and `stop`, and checks that print no value: `free -m`, `df -h`, `timedatectl`, and
`sudo git -C /opt/sunvibe/server status` or `log`. Anything that reads the env file, a
process environment or container configuration is the owner's alone, for example `docker compose config` or `convert`,
`docker inspect` or `docker container inspect`, `env`, `printenv` or `echo $KEY` inside a container, `ps e`, `ps eww` or
`ps -E` on the VM, `cat`, `grep`, `less` or an editor on the env file, and a read of `/proc/*/environ`. Each prints values.

**Oracle.** An Always Free instance is reclaimed when its CPU, network and memory (95th percentile) all stay below 20% for
7 days. Keep the Oracle Cloud Agent's Compute Instance Monitoring plugin on, so that Oracle sees the memory; the fixed heap
keeps the VM near 45% of 6 GB. Read MemoryUtilization in the console, or `free -m` on the VM. Resize to 12 GB (Edit, shape
2 OCPU, 12 GB) when photo upload is turned on. Never create a second A1 instance: going past 2 OCPU or 12 GB of A1 in the
tenancy disables every A1 instance.

**Firewall, before Docker is installed.** Oracle's Ubuntu image keeps its rules in `/etc/iptables/rules.v4` and may reject
all but SSH. Read it; if it rejects the rest, find the line number of the `REJECT` rule of `INPUT` and insert the two rules
before it, then save:

```bash
sudo cat /etc/iptables/rules.v4
sudo iptables -L INPUT --line-numbers
sudo iptables -I INPUT <number of the REJECT line> -p tcp -m state --state NEW --dport 80 -j ACCEPT
sudo iptables -I INPUT <number of the REJECT line> -p tcp -m state --state NEW --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

Never run `netfilter-persistent save` or `reload` once Docker runs: a save stores Docker's chains, a reload flushes them.
The subnet's security list in the Oracle console must allow TCP 80 and 443 as well. Traffic to a port Docker publishes is
forwarded by Docker's own chains, not through `INPUT`; what decides the exposure is those chains and the security list, so
the proof is the outside port check of step 7.

**First start.**

1. Swap for the image build: `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile &&
   sudo swapon /swapfile`, then add `/swapfile none swap sw 0 0` to `/etc/fstab`.
2. Docker Engine and the compose plugin from Docker's apt repository (docs.docker.com, "Install Docker Engine on Ubuntu").
   Leave unattended upgrades on.
3. The code: `sudo git clone --branch main https://github.com/SunvibeVn/sunvibe-server /opt/sunvibe/server`.
4. The secrets: `sudo /opt/sunvibe/server/scripts/ops/test-env/gen-secrets.sh /opt/sunvibe/test.env < /dev/null` fills
   every generated key and says it left the mail login and key. The owner then runs
   `sudo /opt/sunvibe/server/scripts/ops/test-env/gen-secrets.sh /opt/sunvibe/test.env` in their own SSH terminal and types
   them. `MAIL_FROM` is `services@sunvibe.vn`: it must be a verified sender of the separate test account (owner decision
   OD-2), or the owner changes that line. It is not a secret.
5. `sudo /opt/sunvibe/server/scripts/ops/test-env/gen-secrets.sh --check /opt/sunvibe/test.env` exits 0.
6. Start, from the checkout (`API_ENV_FILE` in the env file must name the env file itself):

   ```bash
   cd /opt/sunvibe/server
   sudo docker compose --env-file /opt/sunvibe/test.env -f docker/test-env/compose.yaml up -d --build
   sudo docker compose --env-file /opt/sunvibe/test.env -f docker/test-env/compose.yaml ps
   ```

7. The smoke checks. From another machine, then within one minute from the VM itself as the second client:

   ```bash
   scripts/ops/test-env/smoke.sh https://api-test.sunvibe.vn
   scripts/ops/test-env/smoke.sh https://api-test.sunvibe.vn --second-client --resolve api-test.sunvibe.vn:443:127.0.0.1
   ```

   Every line must say `PASS`. The first run fills the login bucket of its machine's address for about a minute. From
   outside, TCP 8081 and 5432 must not connect.
8. The backup cron line below.

**Update.** `cd /opt/sunvibe/server && sudo git pull --ff-only origin main`, then steps 4 and 5 again: a key the example
gained since is added to the env file with the example's value (an `<owner>` one waits for the owner's terminal), and so
is a key removed from the file by hand, so change a value instead of removing its line. A `<generated>` key the file
lacks is refused (exit 1, nothing written), because a new value would not read the data stored with the old one. Check
with `sudo git -C /opt/sunvibe/server log -p ORIG_HEAD..HEAD -- scripts/ops/env/test-env.env.example` (the checkout is
root's, so git needs `sudo`) that the pull added that key; only then run
`sudo /opt/sunvibe/server/scripts/ops/test-env/gen-secrets.sh --new <KEY> /opt/sunvibe/test.env`. A key the pull did not
add is a lost line: the owner puts back its old value (never the example's `<generated>` line, which the script would fill
anew). Never empty the env file: an empty file is rebuilt from the example with new values. Then the `up -d --build`
command of step 6. Flyway migrates at start. `sudo docker image prune -f` frees the old images.

**Logs.** `cd /opt/sunvibe/server && sudo docker compose --env-file /opt/sunvibe/test.env -f docker/test-env/compose.yaml
logs --since 1h api` (or `caddy`, `postgres`). Each service keeps at most five files of 10 MB.

**Backup, nightly at 19:30 UTC (02:30 in Vietnam).** The VM clock is UTC (`timedatectl`). As root, in
`/etc/cron.d/sunvibe-test-backup`:

```text
30 19 * * * root /opt/sunvibe/server/scripts/ops/test-env/backup-db.sh -f /opt/sunvibe/server/docker/test-env/compose.yaml --env-file /opt/sunvibe/test.env >> /var/log/sunvibe-test-backup.log 2>&1
```

`backup-db.sh` writes `/var/backups/sunvibe-test/sunvibe-test-<UTC time>.dump` (mode 600) through a temporary name, keeps
the 7 newest, and exits 1 without touching the earlier dumps when the dump fails.

**Restore into a scratch database** (a check, or before copying data back by hand):

```bash
cd /opt/sunvibe/server
DC="docker compose --env-file /opt/sunvibe/test.env -f docker/test-env/compose.yaml"
sudo $DC exec -T postgres createdb -U sunvibe restore_check
sudo sh -c "$DC exec -T postgres pg_restore -U sunvibe -d restore_check --no-owner < /var/backups/sunvibe-test/<dump>"
sudo $DC exec -T postgres psql -U sunvibe -d restore_check -Atc 'SELECT count(*) FROM roles'
sudo $DC exec -T postgres dropdb -U sunvibe restore_check
```

**The first staff account (owner decision OD-3; test environment only).** After the owner signs up on
`https://test.sunvibe.vn`:

```bash
cd /opt/sunvibe/server
sudo docker compose --env-file /opt/sunvibe/test.env -f docker/test-env/compose.yaml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U sunvibe -d sunvibe_core -v email='<the sign-up email>' \
  < scripts/ops/test-env/grant-super-admin.sql
```

It grants `SUPER_ADMIN` to the one active account with that email and prints the account's roles; a second run changes
nothing. It refuses, grants nothing and exits 3 when no single active account matches. The grant writes no audit event.
The owner signs in again; then `SUPER_ADMIN` grants `ADMIN` through the API. Never run it on a production database: SV-142's
production runbook is hardening F9.

**Teardown.** The key `sunvibe_test_ed25519` (plan D-9) serves this VM only, so it goes with it.

1. `cd /opt/sunvibe/server && sudo docker compose --env-file /opt/sunvibe/test.env -f docker/test-env/compose.yaml down -v`
   (containers, network, the database and certificate volumes), then `cd /` and
   `sudo rm -rf /opt/sunvibe /var/backups/sunvibe-test /etc/cron.d/sunvibe-test-backup /var/log/sunvibe-test-backup.log`.
2. Remove the key's line from `~/.ssh/authorized_keys` on the VM, then terminate the instance with its boot volume in the
   Oracle console.
3. On the owner's Windows profile: delete `%USERPROFILE%\.ssh\sunvibe_test_ed25519` and `sunvibe_test_ed25519.pub`, and the
   VM's line in `known_hosts`.
4. Delete the SMTP key of the test Brevo account, the DNS records `api-test` and `test`, and the Vercel project of the test
   web.
