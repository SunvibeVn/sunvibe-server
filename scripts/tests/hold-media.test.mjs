// Behavior of scripts/ops/hold-media.sh <ids-file> <reason> <api-base-url> (survey-media spec A-29, plan PD-15):
// one hold per photo id, "already held" told apart by an unchanged setVersion, the ADMIN token only from the
// environment and never printed, and a non-zero exit when any id was not found or refused. Runs against a local fake
// API; no Sunvibe server is needed.
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
 * A fake API: GET .../survey-checklists/{id}/media answers the set version; POST .../survey-media/{id}/hold answers
 * per photo: "held" (the version moves on), "already" (unchanged), or an HTTP status.
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
        if (behavior === "held") {
          setVersion += 1;
          res.end(JSON.stringify({ media: { id: hold[1] }, setVersion }));
        } else if (behavior === "already") {
          res.end(JSON.stringify({ media: { id: hold[1] }, setVersion }));
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
    assert.match(result.stdout, /01JMEDIA000000000000000001 held/);
  });
});

test("alreadyHeld_reportedAsNoOp", async () => {
  await withApi({ "01JMEDIA000000000000000003": "already" }, async (base) => {
    const result = await run([`${CHECKLIST} 01JMEDIA000000000000000003`], { base });

    assert.equal(result.status, 0, result.stderr + result.stdout);
    assert.match(result.stdout, /01JMEDIA000000000000000003 already-held/);
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

test("tokenOnlyFromEnv_neverPrinted", async () => {
  await withApi({}, async (base, requests) => {
    const result = await run([`${CHECKLIST} 01JMEDIA000000000000000007`], { base });

    assert.equal(result.status, 0, result.stderr + result.stdout);
    assert.ok(requests.length > 0 && requests.every((r) => r.auth === `Bearer ${TOKEN}`));
    assert.ok(!result.stdout.includes(TOKEN) && !result.stderr.includes(TOKEN));
  });
});

test("missingToken_refusesToRun", async () => {
  await withApi({}, async (base, requests) => {
    const result = await run([`${CHECKLIST} 01JMEDIA000000000000000008`], { base, token: null });

    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /SUNVIBE_ADMIN_TOKEN/);
    assert.equal(requests.length, 0);
  });
});

test("noOpDetectedByUnchangedSetVersion", async () => {
  await withApi({ "01JMEDIA000000000000000009": "already" }, async (base, requests) => {
    const result = await run([`${CHECKLIST} 01JMEDIA000000000000000009`, `${CHECKLIST} 01JMEDIA00000000000000000A`],
      { base });

    assert.equal(result.status, 0, result.stderr + result.stdout);
    const order = requests.map((r) => r.method);
    assert.equal(order[0], "GET", "the list is read before the hold");
    assert.match(result.stdout, /01JMEDIA000000000000000009 already-held/);
    assert.match(result.stdout, /01JMEDIA00000000000000000A held/);
  });
});
