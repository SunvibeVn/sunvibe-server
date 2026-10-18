-- E2E fixture owner leads for a LOCAL development database (acquisition-owner-lead-queue-ui spec 4.4).
-- Not a Flyway migration: never copy it under db/migration and never run it against a shared or production database.
--
-- Needs the accounts of scripts/dev/seed-e2e-accounts.sql first (E2E Sale, E2E Trưởng nhóm, E2E Admin); it stops with
-- a clear message when they are missing. It writes no password and no secret.
--
-- Ten leads of Đà Nẵng (province 48), each with its consent record and its SUBMITTED history entry:
--   no  lead id                      request code  status      who holds it / what it shows
--   L1  01JE2EHEAD0000000000000001   SV-E2EA-AA2A  NEW         nobody (unassigned), with coordinates
--   L2  01JE2EHEAD0000000000000002   SV-E2EA-AA2B  ASSIGNED    E2E Sale; ward An Hải, the ward E2E Sale looks after
--   L3  01JE2EHEAD0000000000000003   SV-E2EA-AA2C  CONTACTED   E2E Sale; next action time in the past
--   L4  01JE2EHEAD0000000000000004   SV-E2EA-AA2D  ON_HOLD     E2E Sale
--   L5  01JE2EHEAD0000000000000005   SV-E2EA-AA2E  ASSIGNED    E2E Trưởng nhóm (the SALE_LEADER seed)
--   L6  01JE2EHEAD0000000000000006   SV-E2EA-AA2F  REJECTED    nobody; closed with a reason
--   L7  01JE2EHEAD0000000000000007   SV-E2EA-AA2G  ASSIGNED    E2E Sale; same phone as L8
--   L8  01JE2EHEAD0000000000000008   SV-E2EA-AA2H  ASSIGNED    E2E Sale; same phone as L7, possible duplicate of L7
--   L9  01JE2EHEAD0000000000000009   SV-E2EA-AA2J  ASSIGNED    E2E Sale; without coordinates
--   L10 01JE2EHEAD0000000000000010   SV-E2EA-AA2K  NEW         nobody (unassigned), with coordinates
-- Leads L2 to L5 and L7 to L9 also have the ASSIGNED history entry made by E2E Trưởng nhóm. L3, L4 and L6 have the
-- STATUS_CHANGED entry that moved them to their status. Consent records are 01JE2EHCNT00000000000000nn (nn = 01..10).
-- History ids are 20261018000001.., 20261018000101.., 20261018000201.. and the territory change is 20261018000301.
-- One ward is held: An Hải (20275, first by the Vietnamese collator when written), by E2E Sale, with its change
-- (acquisition-owner-lead-queue BR-10); every other ward an E2E seed account held is freed.
-- Times are relative to the moment of the run (a re-run moves them); versions follow what the application writes.
--
-- Idempotent: a re-run deletes the rows with the fixed ids above and inserts them again, whatever happened to them
-- since. Rows of these leads that other code added (history, access logs, idempotency keys, the duplicate links
-- the lead itself owns) go with them. Wards held by an E2E seed account are freed first (an interrupted admin e2e
-- run); wards of other holders are left as they are. The change rows the application writes for An Hải stay as history.
-- It never changes any other lead, consent, user or territory. It refuses, before any change, when another lead
-- already uses one of the request codes, when a lead outside the fixture names one of these leads as its original
-- or owns a duplicate link to one, or when ward An Hải is held by someone who is not an E2E seed account.
--
-- Run after the accounts seed (bash; DATABASE_URL is a libpq URL, not the JDBC DB_URL):
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v target=local -f scripts/dev/seed-e2e-owner-leads.sql
-- It refuses a server that is not on this machine (psql host other than localhost, 127.0.0.1, ::1 or a Unix
-- socket) even with -v target=local. It ends by printing the row counts of the fixtures.

\set ON_ERROR_STOP on

-- The file is UTF-8 (names with diacritics); psql on Windows would use the console code page.
\encoding UTF8

-- Guard: refuse to run, before any change, unless -v target=local is given.
\if :{?target}
\else
\set target ''
\endif
SELECT :'target' = 'local' AS seed_target_is_local \gset
\if :seed_target_is_local
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-owner-leads.sql refused: it runs only against a local database, pass -v target=local';
END
$$;
\endif

-- Guard: refuse to run, before any statement that carries a password or writes, against a server that is not on
-- this machine (a DATABASE_URL pointing at a shared database). HOST is psql's own variable: the host name or address
-- connected to, or the socket directory (starts with /) for a Unix socket.
SELECT :'HOST' IN ('localhost', '127.0.0.1', '::1') OR :'HOST' LIKE '/%' AS seed_host_is_local \gset
\if :seed_host_is_local
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-owner-leads.sql refused: the server is not on this machine';
END
$$;
\endif

\set sale_id '01JE2EACCT0000000000000002'
\set leader_id '01JE2EACCT0000000000000003'
\set admin_id '01JE2EACCT0000000000000004'
\set territory_ward '20275'

-- The accounts seed must have run: the fixtures point at its users.
SELECT count(*) = 3 AS seed_users_present
FROM users
WHERE id IN (:'sale_id', :'leader_id', :'admin_id') AND deleted_at IS NULL \gset
\if :seed_users_present
\else
DO $$
BEGIN
    RAISE EXCEPTION 'seed-e2e-owner-leads.sql refused: run scripts/dev/seed-e2e-accounts.sql first (users 01JE2EACCT0000000000000002, 01JE2EACCT0000000000000003 and 01JE2EACCT0000000000000004 are missing)';
END
$$;
\endif

BEGIN;

-- The fixture leads: one row each, everything else is derived from it below.
CREATE TEMPORARY TABLE seed_leads (
    n INTEGER NOT NULL,
    id VARCHAR(26) NOT NULL,
    request_code VARCHAR(12) NOT NULL,
    status VARCHAR(30) NOT NULL,
    intent VARCHAR(20) NOT NULL,
    contact_name VARCHAR(100) NOT NULL,
    phone VARCHAR(16) NOT NULL,
    email VARCHAR(254),
    ward_code VARCHAR(10) NOT NULL,
    address_detail VARCHAR(255) NOT NULL,
    address_key VARCHAR(255) NOT NULL,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    property_type VARCHAR(20),
    price_amount NUMERIC(15, 0),
    price_unit VARCHAR(10),
    available_from DATE,
    notes VARCHAR(1000),
    callback VARCHAR(100),
    assigned_user_id VARCHAR(26),
    next_action_at TIMESTAMP WITH TIME ZONE,
    close_reason VARCHAR(500),
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_leads (n, id, request_code, status, intent, contact_name, phone, email, ward_code, address_detail,
                        address_key, latitude, longitude, property_type, price_amount, price_unit, available_from,
                        notes, callback, assigned_user_id, next_action_at, close_reason, version, created_at,
                        updated_at)
VALUES
    (1, '01JE2EHEAD0000000000000001', 'SV-E2EA-AA2A', 'NEW', 'LONG_TERM_RENT', 'Nguyễn Thị Lan', '+84912000001',
     'lan.e2e@example.com', '20242', '12 Bạch Đằng, căn A-1203', '12 bạch đằng, căn a-1203', 16.067800, 108.220800,
     'TWO_BEDROOM', 15000000, 'MONTH', DATE '2026-12-01', 'Có nội thất', 'Buổi tối', NULL, NULL, NULL, 0,
     now() - interval '6 days', now() - interval '6 days'),
    (2, '01JE2EHEAD0000000000000002', 'SV-E2EA-AA2B', 'ASSIGNED', 'SHORT_TERM_RENT', 'Trần Văn Minh',
     '+84912000002', NULL, '20275', '88 Võ Văn Kiệt', '88 võ văn kiệt', 16.081200, 108.245300, 'ONE_BEDROOM',
     800000, 'NIGHT', NULL, NULL, NULL, :'sale_id', NULL, NULL, 1,
     now() - interval '5 days', now() - interval '5 days' + interval '1 hour'),
    (3, '01JE2EHEAD0000000000000003', 'SV-E2EA-AA2C', 'CONTACTED', 'LONG_TERM_RENT', 'Lê Thị Hạnh',
     '+84912000003', 'hanh.e2e@example.com', '20263', '5 Hoàng Sa', '5 hoàng sa', 16.098700, 108.241100,
     'STUDIO', 9000000, 'MONTH', DATE '2026-11-15', 'Gần biển', 'Sáng', :'sale_id',
     now() - interval '1 day', NULL, 2,
     now() - interval '4 days', now() - interval '3 days'),
    (4, '01JE2EHEAD0000000000000004', 'SV-E2EA-AA2D', 'ON_HOLD', 'SALE', 'Phạm Quốc Bảo', '+84912000004', NULL,
     '20285', '120 Lê Văn Hiến', '120 lê văn hiến', 15.987300, 108.263400, 'TOWNHOUSE', 4200000000, 'TOTAL', NULL,
     NULL, NULL, :'sale_id', NULL, NULL, 2,
     now() - interval '4 days', now() - interval '3 days'),
    (5, '01JE2EHEAD0000000000000005', 'SV-E2EA-AA2E', 'ASSIGNED', 'LONG_TERM_RENT', 'Võ Thị Thu', '+84912000005',
     NULL, '20209', '33 Lê Duẩn', '33 lê duẩn', 16.066500, 108.211200, 'THREE_BEDROOM', 18000000, 'MONTH', NULL,
     NULL, 'Chiều', :'leader_id', NULL, NULL, 1,
     now() - interval '3 days', now() - interval '3 days' + interval '1 hour'),
    (6, '01JE2EHEAD0000000000000006', 'SV-E2EA-AA2F', 'REJECTED', 'SALE', 'Đặng Văn Hùng', '+84912000006', NULL,
     '20260', '9 Cách Mạng Tháng Tám', '9 cách mạng tháng tám', 16.021500, 108.208800, 'LAND', NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, 'Chủ nhà không còn nhu cầu', 1,
     now() - interval '3 days', now() - interval '3 days' + interval '2 hours'),
    (7, '01JE2EHEAD0000000000000007', 'SV-E2EA-AA2G', 'ASSIGNED', 'LONG_TERM_RENT', 'Hoàng Thị Mai',
     '+84912000007', 'mai.e2e@example.com', '20314', '45 Nguyễn Văn Linh', '45 nguyễn văn linh', 16.012400,
     108.177600, 'TWO_BEDROOM', 11000000, 'MONTH', NULL, NULL, NULL, :'sale_id', NULL, NULL, 1,
     now() - interval '3 days', now() - interval '3 days' + interval '1 hour'),
    (8, '01JE2EHEAD0000000000000008', 'SV-E2EA-AA2H', 'ASSIGNED', 'LONG_TERM_RENT', 'Hoàng Thị Mai',
     '+84912000007', 'mai.e2e@example.com', '20314', '47 Nguyễn Văn Linh', '47 nguyễn văn linh', 16.012600,
     108.177900, 'ONE_BEDROOM', 8000000, 'MONTH', NULL, 'Căn thứ hai của cùng chủ nhà', NULL, :'sale_id', NULL, NULL,
     1, now() - interval '2 days', now() - interval '2 days' + interval '1 hour'),
    (9, '01JE2EHEAD0000000000000009', 'SV-E2EA-AA2J', 'ASSIGNED', 'SHORT_TERM_RENT', 'Bùi Văn Sơn',
     '+84912000009', NULL, '20332', 'Thôn Hòa Tiến', 'thôn hòa tiến', NULL, NULL, 'OTHER', 600000, 'NIGHT', NULL,
     NULL, NULL, :'sale_id', NULL, NULL, 1,
     now() - interval '2 days', now() - interval '2 days' + interval '1 hour'),
    (10, '01JE2EHEAD0000000000000010', 'SV-E2EA-AA2K', 'NEW', 'SHORT_TERM_RENT', 'Ngô Thị Yến', '+84912000010',
     'yen.e2e@example.com', '20197', '70 Nguyễn Lương Bằng', '70 nguyễn lương bằng', 16.075300, 108.150400,
     'STUDIO', 700000, 'NIGHT', NULL, NULL, NULL, NULL, NULL, NULL, 0,
     now() - interval '1 day', now() - interval '1 day');

-- The wards and the province must be in the catalog (the property migrations seed them).
DO $$
BEGIN
    IF (SELECT count(*) FROM seed_leads s JOIN wards w ON w.code = s.ward_code AND w.province_code = '48') <> 10 THEN
        RAISE EXCEPTION 'seed-e2e-owner-leads.sql: a ward of Đà Nẵng is missing from the catalog; run the Flyway migrations first';
    END IF;
END
$$;

-- Refuse when another lead already uses one of the request codes, when a lead outside the fixture points at one of
-- the fixture leads as its original, or when the territory ward is held by a real person.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM owner_leads l JOIN seed_leads s ON s.request_code = l.request_code AND s.id <> l.id) THEN
        RAISE EXCEPTION 'seed-e2e-owner-leads.sql refused: another lead already uses one of the SV-E2EA request codes';
    END IF;
    IF EXISTS (SELECT 1 FROM owner_leads l JOIN seed_leads s ON s.id = l.duplicate_of_lead_id
               WHERE l.id NOT IN (SELECT id FROM seed_leads)) THEN
        RAISE EXCEPTION 'seed-e2e-owner-leads.sql refused: a lead outside the fixture is marked as a duplicate of a fixture lead';
    END IF;
    IF EXISTS (SELECT 1 FROM owner_lead_duplicate_candidates
               WHERE candidate_lead_id IN (SELECT id FROM seed_leads)
                 AND lead_id NOT IN (SELECT id FROM seed_leads)) THEN
        RAISE EXCEPTION 'seed-e2e-owner-leads.sql refused: a lead outside the fixture has a duplicate link to a fixture lead; delete that link or that lead first';
    END IF;
    IF EXISTS (SELECT 1 FROM sale_territories WHERE ward_code = '20275' AND user_id NOT LIKE '01JE2EACCT%') THEN
        RAISE EXCEPTION 'seed-e2e-owner-leads.sql refused: ward An Hải (20275) is held by an account that is not an E2E seed account; free it first';
    END IF;
END
$$;

-- Delete the fixture rows (and only them), children first.
DELETE FROM owner_lead_access_logs WHERE lead_id IN (SELECT id FROM seed_leads);
DELETE FROM owner_lead_activities WHERE lead_id IN (SELECT id FROM seed_leads);
DELETE FROM owner_lead_duplicate_candidates
WHERE lead_id IN (SELECT id FROM seed_leads);
DELETE FROM owner_intake_idempotency_keys WHERE lead_id IN (SELECT id FROM seed_leads);
DELETE FROM owner_leads WHERE id IN (SELECT id FROM seed_leads);
DELETE FROM consent_records WHERE id IN (SELECT '01JE2EHCNT' || substr(id, 11) FROM seed_leads);
-- Territories: An Hải (checked above to be held by nobody or an E2E account), and any other ward an E2E seed account
-- holds (left by an interrupted admin e2e run) is freed; wards of other holders are left as they are.
DELETE FROM sale_territories
WHERE ward_code = :'territory_ward' OR user_id LIKE '01JE2EACCT%';
-- Only the fixed change row goes: the change rows the application writes for An Hải stay as history.
DELETE FROM sale_territory_changes WHERE id = 20261018000301;

-- Consent: one record per lead, the current notice, accepted when the lead was sent (no IP or user agent hash).
INSERT INTO consent_records (id, notice_version, purpose, channel, ip_hash, user_agent_hash, accepted_at)
SELECT '01JE2EHCNT' || substr(id, 11), 'owner-intake-2026-10', 'OWNER_INTAKE_CONTACT', 'WEB', NULL, NULL, created_at
FROM seed_leads;

-- The leads. The snapshot is the form as the owner sent it (same fields as the public intake request).
INSERT INTO owner_leads (id, request_code, status, intent, source, contact_name, phone_normalized, email,
                         province_code, province_name, ward_code, ward_name, address_detail, address_key,
                         latitude, longitude, property_type, asking_price_amount, asking_price_unit, available_from,
                         notes, preferred_callback_time, consent_record_id, submission_snapshot, assigned_user_id,
                         next_action_at, duplicate_of_lead_id, close_reason, created_at, updated_at, version)
SELECT s.id, s.request_code, s.status, s.intent, 'WEB', s.contact_name, s.phone, s.email,
       p.code, p.name, w.code, w.name, s.address_detail, s.address_key,
       s.latitude, s.longitude, s.property_type, s.price_amount, s.price_unit, s.available_from,
       s.notes, s.callback, '01JE2EHCNT' || substr(s.id, 11),
       json_build_object(
           'contactName', s.contact_name,
           'phone', '0' || substr(s.phone, 4),
           'email', s.email,
           'intent', s.intent,
           'provinceCode', p.code,
           'wardCode', w.code,
           'addressDetail', s.address_detail,
           'latitude', s.latitude,
           'longitude', s.longitude,
           'propertyType', s.property_type,
           'askingPrice', CASE WHEN s.price_amount IS NULL THEN NULL
                               ELSE json_build_object('amount', s.price_amount, 'currency', 'VND',
                                                      'unit', s.price_unit) END,
           'availableFrom', s.available_from,
           'notes', s.notes,
           'preferredCallbackTime', s.callback,
           'consent', json_build_object('accepted', true, 'noticeVersion', 'owner-intake-2026-10'))::text,
       s.assigned_user_id, s.next_action_at, NULL, s.close_reason, s.created_at, s.updated_at, s.version
FROM seed_leads s
JOIN wards w ON w.code = s.ward_code
JOIN provinces p ON p.code = w.province_code;

-- Possible duplicate: L8 repeats the phone of the open lead L7, as the intake records it (the newer lead points at
-- the older open one).
INSERT INTO owner_lead_duplicate_candidates (lead_id, candidate_lead_id, detected_at)
SELECT l8.id, l7.id, l8.created_at
FROM seed_leads l8
JOIN seed_leads l7 ON l7.n = 7
WHERE l8.n = 8;

-- History: SUBMITTED for every lead (no actor), ASSIGNED by the leader for the assigned ones, and the status change
-- that moved L3, L4 and L6 to their status.
INSERT INTO owner_lead_activities (id, lead_id, type, actor_user_id, from_status, to_status, from_assignee_id,
                                   to_assignee_id, note, next_action_at, occurred_at)
SELECT 20261018000000 + n, id, 'SUBMITTED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, created_at
FROM seed_leads;

INSERT INTO owner_lead_activities (id, lead_id, type, actor_user_id, from_status, to_status, from_assignee_id,
                                   to_assignee_id, note, next_action_at, occurred_at)
SELECT 20261018000100 + n, id, 'ASSIGNED', :'leader_id', 'NEW', 'ASSIGNED', NULL, assigned_user_id, NULL, NULL,
       created_at + interval '1 hour'
FROM seed_leads
WHERE n IN (2, 3, 4, 5, 7, 8, 9);

INSERT INTO owner_lead_activities (id, lead_id, type, actor_user_id, from_status, to_status, from_assignee_id,
                                   to_assignee_id, note, next_action_at, occurred_at)
VALUES (20261018000203, '01JE2EHEAD0000000000000003', 'STATUS_CHANGED', :'sale_id', 'ASSIGNED', 'CONTACTED', NULL,
        NULL, 'Đã gọi lần đầu, hẹn gọi lại.', now() - interval '1 day', now() - interval '3 days'),
       (20261018000204, '01JE2EHEAD0000000000000004', 'STATUS_CHANGED', :'sale_id', 'ASSIGNED', 'ON_HOLD', NULL,
        NULL, 'Chủ nhà chưa sẵn sàng, tạm hoãn.', NULL, now() - interval '3 days'),
       (20261018000206, '01JE2EHEAD0000000000000006', 'STATUS_CHANGED', :'leader_id', 'NEW', 'REJECTED', NULL,
        NULL, NULL, NULL, now() - interval '3 days' + interval '2 hours');

-- The one territory: E2E Sale looks after An Hải, assigned by E2E Admin (only an ADMIN assigns wards), with its change.
INSERT INTO sale_territories (ward_code, user_id, assigned_by, assigned_at)
VALUES (:'territory_ward', :'sale_id', :'admin_id', now() - interval '7 days');

INSERT INTO sale_territory_changes (id, ward_code, old_user_id, new_user_id, actor_user_id, occurred_at)
VALUES (20261018000301, :'territory_ward', NULL, :'sale_id', :'admin_id', now() - interval '7 days');

-- Counts of the fixture rows, taken inside the transaction while the id list still exists.
SELECT (SELECT count(*) FROM owner_leads WHERE id IN (SELECT id FROM seed_leads)) AS leads,
       (SELECT count(*) FROM consent_records
        WHERE id IN (SELECT consent_record_id FROM owner_leads WHERE id IN (SELECT id FROM seed_leads))) AS consents,
       (SELECT count(*) FROM owner_lead_activities WHERE lead_id IN (SELECT id FROM seed_leads)) AS activities,
       (SELECT count(*) FROM owner_lead_duplicate_candidates
        WHERE lead_id IN (SELECT id FROM seed_leads)) AS candidates,
       (SELECT count(*) FROM sale_territories
        WHERE ward_code = :'territory_ward' AND user_id = :'sale_id') AS territories,
       (SELECT count(*) FROM sale_territory_changes WHERE id = 20261018000301) AS territory_changes \gset

COMMIT;

\echo seed-e2e-owner-leads: leads=:leads consents=:consents activities=:activities candidates=:candidates territories=:territories territory_changes=:territory_changes
