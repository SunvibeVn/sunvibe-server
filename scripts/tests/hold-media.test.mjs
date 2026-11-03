// Behavior of scripts/ops/hold-media.sh <ids-file> <reason> <api-base-url> (survey-media spec A-29, plan PD-15; code
// review M5 and security review M-3, SV-069): one hold per photo id and no other request (no list read per hold), an
// earlier hold with another reason reported with that reason, the ADMIN token never printed, an http:// base refused
// unless it is this machine, and a non-zero exit when any id was not found or refused. Runs against a local fake API;
// no Sunvibe server is needed.
import { test } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { findBash, REPO_DIR } from "./helpers.mjs";

const SCRIPT = join(REPO_DIR, "scripts", "ops", "hold-media.sh");
const TOKEN = "eyJ.fake-admin-token.sig";
const CHECKLIST = "01JCHECKLIST0000000000000A";

/**
 * A fake API: POST .../survey-media/{id}/hold answers per photo: "held" (held now with the requested reason),
 * "already" (held earlier with the other reason, which a no-op keeps), or an HTTP status. Any other request is 404.
 */
async function withApi(behaviors, fn) {
  const requests = [];
  let setVersion = 7;
  const server = createServer((req, res) => {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      requests.push({ method: req.method, url: req.url, auth: req.headers.authorization, body });
      const list = req.url.match(/^\/api\/v1\/staff\/survey-checklists\/([^/]+)\/media$/);
      const hold = req.url.match(/^\/api\/v1\/staff\/survey-media\/([^/]+)\/hold$/);
      res.setHeader("Content-Type", "application/json");
      if (req.method === "GET" && list) {
        res.end(JSON.stringify({ checklistId: list[1], setVersion, items: [] }));
      } else if (req.method === "POST" && hold) {
        const behavior = behaviors[hold[1]] ?? "held";
        const asked = JSON.parse(body).reason;
        const other = asked === "SECURITY_INCIDENT" ? "CREDENTIAL_EXPOSURE" : "SECURITY_INCIDENT";
        const held = (reason) => ({ at: "2026-11-03T10:00:00Z", byUserId: "01JADMIN00000000000000000A",
          byName: "Admin", reason });
        if (behavior === "held") {
          setVersion += 1;
          res.end(JSON.stringify({ media: { id: hold[1], status: "READY", held: held(asked) }, setVersion }));
        } else if (behavior === "already") {
          res.end(JSON.stringify({ media: { id: hold[1], status: "READY", held: held(other) }, setVersion }));
        } else {
          res.statusCode = behavior;
          res.end(JSON.stringify({ code: "X", message: "refused" }));
        }
      } else {
        res.statusCode = 404;
        res.end("{}");
      }
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    return await fn(base, requests);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

/** Runs the script with the lines as its ids file; resolves {status, stdout, stderr}. */
function run(lines, { base, reason = "SECURITY_INCIDENT", token = TOKEN } = {}) {
  const dir = mkdtempSync(join(tmpdir(), "sv-hold-"));
  const file = join(dir, "ids.txt");
  writeFileSync(file, lines.join("\n") + "\n");
  const env = { ...process.env };
  delete env.SUNVIBE_ADMIN_TOKEN;
  if (token !== null) env.SUNVIBE_ADMIN_TOKEN = token;
  return new Promise((resolve, reject) => {
    const child = spawn(findBash(), [SCRIPT, file, reason, base], { env });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (chunk) => (stdout += chunk));
    child.stderr.on("data", (chunk) => (stderr += chunk));
    child.on("error", reject);
    child.on("close", (status) => {
      rmSync(dir, { recursive: true, force: true });
      resolve({ status, stdout, stderr });
    });
  });
}

const holds = (requests) => requests.filter((r) => r.method === "POST");

test("holdsEachIdOnce", async () => {
  await withApi({}, async (base, requests) => {
    const result = await run([`${CHECKLIST} 01JMEDIA000000000000000001`, `${CHECKLIST} 01JMEDIA000000000000000002`,
      `${CHECKLIST} 01JMEDIA000000000000000001`, "", "# a comment"], { base });

    assert.equal(result.status, 0, result.stderr + result.stdout);
    assert.deepEqual(holds(requests).map((r) => r.url).sort(), [
      "/api/v1/staff/survey-media/01JMEDIA000000000000000001/hold",
      "/api/v1/staff/survey-media/01JMEDIA000000000000000002/hold"]);
    assert.ok(holds(requests).every((r) => JSON.parse(r.body).reason === "SECURITY_INCIDENT"));
    assert.equal(requests.length, 2, "no list read per hold (it counted in the ADMIN's list budget)");
    assert.match(result.stdout, /01JMEDIA000000000000000001 held/);
  });
});

test("heldEarlierWithAnotherReason_reportedWithThatReason", async () => {
  await withApi({ "01JMEDIA000000000000000003": "already" }, async (base) => {
    const result = await run([`${CHECKLIST} 01JMEDIA000000000000000003`], { base });

    assert.equal(result.status, 0, result.stderr + result.stdout);
    assert.match(result.stdout, /01JMEDIA000000000000000003 already-held CREDENTIAL_EXPOSURE/);
  });
});

test("httpBase_refusedUnlessThisMachine", async () => {
  const result = await run([`${CHECKLIST} 01JMEDIA00000000000000000B`], { base: "http://api.sunvibe.vn" });

  assert.equal(result.status, 2);
  assert.match(result.stderr, /https/);
  assert.ok(!result.stdout.includes("held"));
});

test("baseWithUserinfo_refused", async () => {
  // curl would read "localhost:1" as credentials and send the token in clear to the host after @
  for (const base of ["http://localhost:1@api.example.test", "http://127.0.0.1:80@api.example.test",
    "https://user@api.example.test", "HTTP://api.example.test"]) {
    const result = await run([`${CHECKLIST} 01JMEDIA00000000000000000C`], { base });

    assert.equal(result.status, 2, base);
  }
});

test("tokenWithCharactersNoAccessTokenHas_refused", async () => {
  await withApi({}, async (base, requests) => {
    const result = await run([`${CHECKLIST} 01JMEDIA00000000000000000D`], { base, token: 'abc"\nurl = "x' });

    assert.equal(result.status, 2);
    assert.equal(requests.length, 0);
  });
});

test("notFoundAndRefused_reportedNonZeroExit", async () => {
  const behaviors = { "01JMEDIA000000000000000004": 404, "01JMEDIA000000000000000005": 409 };
  await withApi(behaviors, async (base) => {
    const result = await run([`${CHECKLIST} 01JMEDIA000000000000000004`, `${CHECKLIST} 01JMEDIA000000000000000005`,
      `${CHECKLIST} 01JMEDIA000000000000000006`], { base });

    assert.notEqual(result.status, 0);
    assert.match(result.stdout, /01JMEDIA000000000000000004 not-found/);
    assert.match(result.stdout, /01JMEDIA000000000000000005 refused 409/);
    assert.match(result.stdout, /01JMEDIA000000000000000006 held/, "the other ids still run");
  });
});

test("token_neverPrinted", async () => {
  await withApi({}, async (base, requests) => {
    const result = await run([`${CHECKLIST} 01JMEDIA000000000000000007`], { base });

    assert.equal(result.status, 0, result.stderr + result.stdout);
    assert.ok(requests.length > 0 && requests.every((r) => r.auth === `Bearer ${TOKEN}`));
    assert.ok(!result.stdout.includes(TOKEN) && !result.stderr.includes(TOKEN));
  });
});

test("missingTokenWithoutTerminal_refusesToRun", async () => {
  await withApi({}, async (base, requests) => {
    const result = await run([`${CHECKLIST} 01JMEDIA000000000000000008`], { base, token: null });

    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /SUNVIBE_ADMIN_TOKEN/);
    assert.equal(requests.length, 0);
  });
});
