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
export SUNVIBE_ADMIN_TOKEN='<access token of an ADMIN account>'   # never paste it into a file or a ticket
scripts/ops/hold-media.sh affected.txt SECURITY_INCIDENT https://api.sunvibe.vn
unset SUNVIBE_ADMIN_TOKEN
```

- `affected.txt`: one `checklistId mediaId` per line; blank lines and `#` comments are skipped; an id listed twice is
  held once.
- Each id is reported as `held`, `already-held` (the hold was a no-op: the checklist's `setVersion` did not move),
  `not-found` or `refused <status>`; a summary line follows. The exit status is 1 when any id was not found or
  refused (re-run the script with those lines after checking them), 2 on a usage error or a missing token.
- Each hold is checked, versioned and written as one security event by the server (spec A-23), exactly like a hold
  from the page.
- The token is read only from `SUNVIBE_ADMIN_TOKEN` and reaches `curl` through a config on stdin: it is never
  printed, stored or put on a command line. Needs `bash` and `curl`.

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
   Before the start, note the time and the count of photos it may mark:
   `SELECT count(*) FROM survey_media WHERE status = 'READY' AND content_missing_at IS NULL AND content_deleted_at IS NULL;`
   after it: `SELECT count(*) FROM survey_media WHERE content_missing_at >= '<start time>';`.
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
again. Read only (no write to these tables is part of this procedure):

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
