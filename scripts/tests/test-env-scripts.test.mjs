// Behavior of the test-environment ops scripts (W35a plan Task S3, D-8):
// - scripts/ops/test-env/gen-secrets.sh <env-file> fills every <generated> value of scripts/ops/env/test-env.env.example
//   with a fresh random value of the length and encoding its consumer needs, never overwrites a filled value, adds a key
//   the file lacks (a <generated> one in an existing file only when --new names it), asks for the mail login and key
//   only on a terminal, prints key names only, and --check reports filled or missing per key.
// - scripts/ops/test-env/backup-db.sh -f <compose-file> --env-file <env-file> --dir <dir> dumps through
//   `docker compose exec -T postgres`, renames the dump only on success and keeps the 7 newest.
// Temporary folders only; a stub `docker` first on PATH stands in for Docker.
import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { delimiter, join } from "node:path";
import { findBash, REPO_DIR, writeFile } from "./helpers.mjs";

const GEN_SECRETS = join(REPO_DIR, "scripts", "ops", "test-env", "gen-secrets.sh");
const BACKUP_DB = join(REPO_DIR, "scripts", "ops", "test-env", "backup-db.sh");
const EXAMPLE = join(REPO_DIR, "scripts", "ops", "env", "test-env.env.example");

/** KEY=VALUE lines of an env file, in order, as a Map (comments and blank lines left out). */
function parseEnv(text) {
  const entries = new Map();
  for (const line of text.split(/\r?\n/)) {
    const match = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (match) entries.set(match[1], match[2]);
  }
  return entries;
}

const exampleKeys = parseEnv(readFileSync(EXAMPLE, "utf8"));
const GENERATED = [...exampleKeys].filter(([, value]) => value === "<generated>").map(([key]) => key);
const OWNER = [...exampleKeys].filter(([, value]) => value === "<owner>").map(([key]) => key);

function tempDir(prefix) {
  const dir = mkdtempSync(join(tmpdir(), prefix));
  return { dir, cleanup: () => rmSync(dir, { recursive: true, force: true }) };
}

/** Runs a script with bash, stdin a pipe (never a terminal); returns {status, stdout, stderr, output}. */
function run(script, args, { env = {} } = {}) {
  const result = spawnSync(findBash(), [script, ...args], {
    input: "",
    encoding: "utf8",
    env: { ...process.env, ...env },
  });
  if (result.error) throw result.error;
  return { status: result.status, stdout: result.stdout, stderr: result.stderr, output: result.stdout + result.stderr };
}

function base64Bytes(value) {
  assert.match(value, /^[A-Za-z0-9+/]+={0,2}$/, "standard base64 on one line");
  return Buffer.from(value, "base64").length;
}

test("genSecrets_emptyFile_fillsEveryGeneratedKey", () => {
  const { dir, cleanup } = tempDir("sv-gensecrets-");
  try {
    assert.ok(GENERATED.length >= 7, "the example names the generated keys");
    const absent = join(dir, "absent.env");
    const empty = join(dir, "empty.env");
    writeFileSync(empty, "");
    for (const file of [absent, empty]) {
      const result = run(GEN_SECRETS, [file]);
      assert.equal(result.status, 0, result.output);
      const filled = parseEnv(readFileSync(file, "utf8"));
      assert.deepEqual([...filled.keys()], [...exampleKeys.keys()], "every key of the example, in its order");
      for (const key of GENERATED) {
        assert.ok(filled.get(key) && !filled.get(key).includes("<"), `${key} filled`);
      }
      for (const [key, value] of exampleKeys) {
        if (!GENERATED.includes(key) && !OWNER.includes(key)) assert.equal(filled.get(key), value, `${key} as in example`);
      }
    }
  } finally {
    cleanup();
  }
});

test("genSecrets_values_distinctWithRequiredLength", () => {
  const { dir, cleanup } = tempDir("sv-gensecrets-");
  try {
    const file = join(dir, "test.env");
    assert.equal(run(GEN_SECRETS, [file]).status, 0);
    const env = parseEnv(readFileSync(file, "utf8"));
    // JWTProvider: a base64 HMAC key of at least 256 bits
    assert.ok(base64Bytes(env.get("JWT_SECRET")) >= 32);
    // application.yml app.crypto: two base64 32-byte keys that differ
    assert.equal(base64Bytes(env.get("ID_CARD_CRYPTO_KEY")), 32);
    assert.equal(base64Bytes(env.get("ID_CARD_CRYPTO_INDEX_KEY")), 32);
    // AUTH_OTP_HASH_KEY and ACQUISITION_HASH_KEY: base64, at least 32 bytes
    assert.ok(base64Bytes(env.get("AUTH_OTP_HASH_KEY")) >= 32);
    assert.ok(base64Bytes(env.get("ACQUISITION_HASH_KEY")) >= 32);
    // One database password for the API and the database container; letters and digits only, so neither Compose nor a
    // JDBC URL reads anything into it
    assert.match(env.get("DB_PASSWORD"), /^[A-Za-z0-9]{32,}$/);
    assert.equal(env.get("POSTGRES_PASSWORD"), env.get("DB_PASSWORD"));
    const values = GENERATED.filter((key) => key !== "POSTGRES_PASSWORD").map((key) => env.get(key));
    assert.equal(new Set(values).size, values.length, "every generated value differs from the others");
  } finally {
    cleanup();
  }
});

test("genSecrets_secondRun_changesNothing", () => {
  const { dir, cleanup } = tempDir("sv-gensecrets-");
  try {
    const file = join(dir, "test.env");
    assert.equal(run(GEN_SECRETS, [file]).status, 0);
    // A value the operator set by hand stays as well
    const first = readFileSync(file, "utf8").replace(/^MAIL_USERNAME=.*$/m, "MAIL_USERNAME=typed-login");
    writeFileSync(file, first);
    const second = run(GEN_SECRETS, [file]);
    assert.equal(second.status, 0, second.output);
    assert.equal(readFileSync(file, "utf8"), first);
  } finally {
    cleanup();
  }
});

test("genSecrets_keyMissingFromFile_addedFromExample", () => {
  const { dir, cleanup } = tempDir("sv-gensecrets-");
  try {
    const file = join(dir, "test.env");
    assert.equal(run(GEN_SECRETS, [file]).status, 0);
    const before = parseEnv(readFileSync(file, "utf8"));
    // A key the example gained after the file was made: a plain one and an owner one
    const dropped = ["AUTH_MAIL_MAX_PER_HOUR", "MAIL_USERNAME"];
    writeFileSync(file, withoutKeys(readFileSync(file, "utf8"), dropped));
    const result = run(GEN_SECRETS, [file]);
    assert.equal(result.status, 0, result.output);
    const after = parseEnv(readFileSync(file, "utf8"));
    assert.deepEqual([...after.keys()].sort(), [...exampleKeys.keys()].sort(), "every key of the example is back");
    assert.equal(after.get("AUTH_MAIL_MAX_PER_HOUR"), exampleKeys.get("AUTH_MAIL_MAX_PER_HOUR"));
    assert.equal(after.get("MAIL_USERNAME"), "<owner>", "left for the owner");
    for (const [key, value] of before) {
      if (!dropped.includes(key)) assert.equal(after.get(key), value, `${key} unchanged`);
    }
    for (const key of dropped) assert.match(result.stdout, new RegExp(`^added ${key}\\b`, "m"));
    assert.doesNotMatch(result.stdout, /^filled MAIL_USERNAME$/m, "no claim of a value it did not write");
  } finally {
    cleanup();
  }
});

function withoutKeys(text, keys) {
  return text
    .split(/\r?\n/)
    .filter((line) => !keys.some((key) => line.startsWith(`${key}=`)))
    .join("\n");
}

test("genSecrets_existingFile_generatedKeyAbsent_refusedUnlessNew", () => {
  const { dir, cleanup } = tempDir("sv-gensecrets-");
  try {
    const file = join(dir, "test.env");
    assert.equal(run(GEN_SECRETS, [file]).status, 0);
    const before = parseEnv(readFileSync(file, "utf8"));
    // A lost line of a key that data is stored under: a new value would not read the ID card numbers stored with the old
    writeFileSync(file, withoutKeys(readFileSync(file, "utf8"), ["ID_CARD_CRYPTO_KEY", "AUTH_MAIL_MAX_PER_HOUR"]));
    const lost = readFileSync(file, "utf8");
    const refused = run(GEN_SECRETS, [file]);
    assert.equal(refused.status, 1, refused.output);
    assert.match(refused.stderr, /ID_CARD_CRYPTO_KEY/);
    assert.match(refused.stderr, /--new/);
    assert.equal(readFileSync(file, "utf8"), lost, "nothing written");

    // A key the example gained is named on purpose
    const added = run(GEN_SECRETS, ["--new", "ID_CARD_CRYPTO_KEY", file]);
    assert.equal(added.status, 0, added.output);
    const after = parseEnv(readFileSync(file, "utf8"));
    assert.equal(base64Bytes(after.get("ID_CARD_CRYPTO_KEY")), 32);
    assert.notEqual(after.get("ID_CARD_CRYPTO_KEY"), after.get("ID_CARD_CRYPTO_INDEX_KEY"));
    assert.equal(after.get("AUTH_MAIL_MAX_PER_HOUR"), exampleKeys.get("AUTH_MAIL_MAX_PER_HOUR"));
    for (const [key, value] of before) {
      if (key !== "ID_CARD_CRYPTO_KEY" && key !== "AUTH_MAIL_MAX_PER_HOUR") assert.equal(after.get(key), value);
    }
  } finally {
    cleanup();
  }
});

test("genSecrets_output_hasNoValue", () => {
  const { dir, cleanup } = tempDir("sv-gensecrets-");
  try {
    const file = join(dir, "test.env");
    const result = run(GEN_SECRETS, [file]);
    const check = run(GEN_SECRETS, ["--check", file]);
    const env = parseEnv(readFileSync(file, "utf8"));
    for (const key of GENERATED) {
      assert.ok(!result.output.includes(env.get(key)), `${key}'s value not printed while filling`);
      assert.ok(!check.output.includes(env.get(key)), `${key}'s value not printed by --check`);
    }
    assert.match(result.output, /JWT_SECRET/, "key names are printed");
  } finally {
    cleanup();
  }
});

test("genSecrets_file_isMode600", { skip: process.platform === "win32" && "file modes are not kept on Windows" }, () => {
  const { dir, cleanup } = tempDir("sv-gensecrets-");
  try {
    const file = join(dir, "test.env");
    assert.equal(run(GEN_SECRETS, [file]).status, 0);
    assert.equal(statSync(file).mode & 0o777, 0o600);
    // A file that exists with a wider mode is narrowed
    writeFileSync(file, readFileSync(file), { mode: 0o644 });
    spawnSync("chmod", ["644", file]);
    assert.equal(run(GEN_SECRETS, [file]).status, 0);
    assert.equal(statSync(file).mode & 0o777, 0o600);
  } finally {
    cleanup();
  }
});

test("genSecrets_noTty_leavesMailBlank_saysSo", () => {
  const { dir, cleanup } = tempDir("sv-gensecrets-");
  try {
    const file = join(dir, "test.env");
    const result = run(GEN_SECRETS, [file]);
    assert.equal(result.status, 0, result.output);
    const env = parseEnv(readFileSync(file, "utf8"));
    assert.deepEqual(OWNER, ["MAIL_USERNAME", "MAIL_PASSWORD"]);
    for (const key of OWNER) {
      assert.equal(env.get(key), "<owner>", `${key} not filled`);
      assert.match(result.output, new RegExp(`${key}.*no terminal|no terminal.*${key}`, "s"));
    }
  } finally {
    cleanup();
  }
});

test("genSecrets_check_placeholderLeft_failsWithNamesOnly", () => {
  const { dir, cleanup } = tempDir("sv-gensecrets-");
  try {
    const file = join(dir, "test.env");
    assert.equal(run(GEN_SECRETS, [file]).status, 0);
    const missing = run(GEN_SECRETS, ["--check", file]);
    assert.notEqual(missing.status, 0, "a placeholder is left");
    const lines = missing.stdout.trim().split(/\r?\n/);
    assert.deepEqual(lines.map((line) => line.split(" ")[0]), [...exampleKeys.keys()], "one line per key, names only");
    for (const line of lines) assert.match(line, /^[A-Z0-9_]+ (filled|missing)$/);
    assert.ok(lines.includes("MAIL_USERNAME missing") && lines.includes("MAIL_PASSWORD missing"));
    assert.ok(lines.includes("JWT_SECRET filled"));

    // A key of the example that the file lacks is missing too
    const text = readFileSync(file, "utf8")
      .replace(/^MAIL_USERNAME=.*$/m, "MAIL_USERNAME=typed-login")
      .replace(/^MAIL_PASSWORD=.*$/m, "MAIL_PASSWORD=typed-key");
    writeFileSync(file, text);
    assert.equal(run(GEN_SECRETS, ["--check", file]).status, 0, "nothing left");
    writeFileSync(file, text.replace(/^ACQUISITION_HASH_KEY=.*\r?\n/m, ""));
    const dropped = run(GEN_SECRETS, ["--check", file]);
    assert.notEqual(dropped.status, 0);
    assert.match(dropped.stdout, /^ACQUISITION_HASH_KEY missing$/m);
  } finally {
    cleanup();
  }
});

/**
 * A stub `docker` that logs its arguments and writes a fake dump, or, when DOCKER_STUB_FAIL is set, writes part of one
 * and fails, as a pg_dump that dies mid-stream does: only the exit status tells that dump from a whole one.
 */
function withStubDocker(fn) {
  const { dir, cleanup } = tempDir("sv-backup-");
  try {
    const bin = join(dir, "bin");
    const log = join(dir, "docker-args.log");
    writeFile(
      bin,
      "docker",
      [
        "#!/usr/bin/env bash",
        `printf '%s\\n' "$@" > '${log.replace(/\\/g, "/")}'`,
        'if [ -n "${DOCKER_STUB_FAIL:-}" ]; then printf "PGDMP partial"; echo "pg_dump: error: connection lost" >&2; exit 1; fi',
        "printf 'PGDMP fake dump'",
        "",
      ].join("\n"),
    );
    spawnSync("chmod", ["+x", join(bin, "docker")]);
    const backups = join(dir, "backups");
    const env = { PATH: `${bin}${delimiter}${process.env.PATH}` };
    return fn({ dir, backups, log, env });
  } finally {
    cleanup();
  }
}

/** Nine older dumps, oldest first, and one file the rotation must not touch. */
function seedBackups(backups) {
  const names = [];
  for (let day = 1; day <= 9; day += 1) {
    const name = `sunvibe-test-202611${String(day).padStart(2, "0")}T193000Z.dump`;
    writeFile(backups, name, "old");
    names.push(name);
  }
  writeFile(backups, "notes.txt", "keep");
  return names;
}

test("backupDb_rotation_keepsSevenNewest", () => {
  withStubDocker(({ backups, env }) => {
    const old = seedBackups(backups);
    const result = run(BACKUP_DB, ["-f", "/srv/compose.yaml", "--env-file", "/srv/test.env", "--dir", backups], { env });
    assert.equal(result.status, 0, result.output);
    const dumps = readdirSync(backups).filter((name) => name.endsWith(".dump")).sort();
    assert.equal(dumps.length, 7);
    const fresh = dumps.filter((name) => !old.includes(name));
    assert.equal(fresh.length, 1, "one new dump");
    assert.match(fresh[0], /^sunvibe-test-\d{8}T\d{6}Z\.dump$/);
    assert.equal(readFileSync(join(backups, fresh[0]), "utf8"), "PGDMP fake dump");
    assert.deepEqual(dumps.filter((name) => old.includes(name)), old.slice(3), "the three oldest removed");
    assert.ok(existsSync(join(backups, "notes.txt")));
    assert.ok(!readdirSync(backups).some((name) => name.includes("partial")), "no temporary file left");
  });
});

test("backupDb_dumpFails_exitsNonZero_keepsOld", () => {
  withStubDocker(({ backups, log, env }) => {
    const old = seedBackups(backups);
    const result = run(BACKUP_DB, ["-f", "/srv/compose.yaml", "--env-file", "/srv/test.env", "--dir", backups], {
      env: { ...env, DOCKER_STUB_FAIL: "1" },
    });
    assert.ok(existsSync(log), "the dump was attempted");
    assert.equal(result.status, 1, result.output);
    assert.match(result.stderr, /dump failed/i);
    assert.deepEqual(readdirSync(backups).sort(), [...old, "notes.txt"].sort(), "every old dump kept, nothing added");
  });
});

test("backupDb_passesComposeAndEnvFile", () => {
  withStubDocker(({ backups, log, env }) => {
    const result = run(BACKUP_DB, ["-f", "/srv/compose.yaml", "--env-file", "/srv/test.env", "--dir", backups], { env });
    assert.equal(result.status, 0, result.output);
    const args = readFileSync(log, "utf8").trim().split(/\r?\n/);
    assert.equal(args[0], "compose");
    const pair = (flag) => args[args.indexOf(flag) + 1];
    assert.equal(pair("-f"), "/srv/compose.yaml");
    assert.equal(pair("--env-file"), "/srv/test.env");
    const exec = args.indexOf("exec");
    assert.ok(exec > args.indexOf("-f") && exec > args.indexOf("--env-file"), "the files come before exec");
    assert.deepEqual(args.slice(exec, exec + 3), ["exec", "-T", "postgres"]);
    assert.match(args.slice(exec + 3).join(" "), /pg_dump -Fc/);
  });
});
