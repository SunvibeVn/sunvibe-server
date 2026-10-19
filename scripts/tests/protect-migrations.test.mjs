// Behavior of .claude/hooks/protect-migrations.sh (PreToolUse on Edit|Write|MultiEdit).
// Policy: block an edit only when the target is a versioned Flyway migration (V*.sql) that exists in HEAD.
// New migrations (untracked or only staged) stay editable; anything the hook cannot parse is let through.
import { test } from "node:test";
import assert from "node:assert/strict";
import { join } from "node:path";
import { git, HOOKS_DIR, makeRepo, runHook, writeFile } from "./helpers.mjs";

const HOOK = join(HOOKS_DIR, "protect-migrations.sh");
const MIG_DIR = "modules/auth-service/src/main/resources/db/migration";
const COMMITTED = `${MIG_DIR}/V202610091200__create_roles_table.sql`;
const NEW = `${MIG_DIR}/V202610191200__add_column.sql`;

function edit(filePath, tool = "Edit") {
  return { tool_name: tool, tool_input: { file_path: filePath } };
}

function withRepo(files, fn, options) {
  const repo = makeRepo(files, options);
  try {
    return fn(repo.dir);
  } finally {
    repo.cleanup();
  }
}

test("blocks an edit of a migration that exists in HEAD (absolute path)", () => {
  withRepo({ [COMMITTED]: "create table roles();\n" }, (dir) => {
    const result = runHook(HOOK, { input: edit(join(dir, COMMITTED).replace(/\\/g, "/")), env: { CLAUDE_PROJECT_DIR: dir } });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /committed Flyway migration/);
  });
});

test("allows editing a new migration that is only staged, not committed", () => {
  withRepo({ [COMMITTED]: "create table roles();\n" }, (dir) => {
    writeFile(dir, NEW, "alter table roles add column x int;\n");
    git(dir, "add", NEW);
    const result = runHook(HOOK, { input: edit(join(dir, NEW)), env: { CLAUDE_PROJECT_DIR: dir } });
    assert.equal(result.status, 0, result.stderr);
  });
});

test("allows writing a new untracked migration", () => {
  withRepo({ [COMMITTED]: "create table roles();\n" }, (dir) => {
    const result = runHook(HOOK, { input: edit(join(dir, NEW), "Write"), env: { CLAUDE_PROJECT_DIR: dir } });
    assert.equal(result.status, 0, result.stderr);
  });
});

test("blocks a committed migration given with Windows backslashes", () => {
  withRepo({ [COMMITTED]: "x\n" }, (dir) => {
    const result = runHook(HOOK, { input: edit(join(dir, COMMITTED).replace(/\//g, "\\")), env: { CLAUDE_PROJECT_DIR: dir } });
    assert.equal(result.status, 2);
  });
});

test("blocks a committed migration given relative to the project dir", () => {
  withRepo({ [COMMITTED]: "x\n" }, (dir) => {
    const result = runHook(HOOK, { input: edit(COMMITTED, "MultiEdit"), cwd: dir, env: { CLAUDE_PROJECT_DIR: dir } });
    assert.equal(result.status, 2);
  });
});

test("handles a project path with spaces: committed is blocked, staged new is allowed", () => {
  withRepo(
    { [COMMITTED]: "x\n" },
    (dir) => {
      assert.match(dir, / /);
      const blocked = runHook(HOOK, { input: edit(join(dir, COMMITTED)), env: { CLAUDE_PROJECT_DIR: dir } });
      assert.equal(blocked.status, 2);
      writeFile(dir, NEW, "y\n");
      git(dir, "add", NEW);
      const allowed = runHook(HOOK, { input: edit(join(dir, NEW)), env: { CLAUDE_PROJECT_DIR: dir } });
      assert.equal(allowed.status, 0, allowed.stderr);
    },
    { prefix: "sv hook with space " },
  );
});

test("still blocks a committed migration that has a staged modification or was removed from the index", () => {
  withRepo({ [COMMITTED]: "x\n" }, (dir) => {
    writeFile(dir, COMMITTED, "changed\n");
    git(dir, "add", COMMITTED);
    assert.equal(runHook(HOOK, { input: edit(join(dir, COMMITTED)), env: { CLAUDE_PROJECT_DIR: dir } }).status, 2);
    git(dir, "rm", "-q", "--cached", COMMITTED);
    assert.equal(runHook(HOOK, { input: edit(join(dir, COMMITTED)), env: { CLAUDE_PROJECT_DIR: dir } }).status, 2);
  });
});

test("blocks a committed migration inside a linked worktree (.git is a file)", () => {
  withRepo({ [COMMITTED]: "x\n" }, (dir) => {
    const worktree = join(dir, "..", "wt");
    git(dir, "worktree", "add", "-q", "-b", "feature/x", worktree);
    const result = runHook(HOOK, { input: edit(join(worktree, COMMITTED)), env: { CLAUDE_PROJECT_DIR: worktree } });
    assert.equal(result.status, 2);
  });
});

test("works without CLAUDE_PROJECT_DIR (Codex) by using the current repository", () => {
  withRepo({ [COMMITTED]: "x\n" }, (dir) => {
    const result = runHook(HOOK, { input: edit(join(dir, COMMITTED)), cwd: dir });
    assert.equal(result.status, 2);
  });
});

test("checks the repository that holds the file, not the session's project dir", () => {
  withRepo({ [COMMITTED]: "x\n" }, (serverDir) => {
    withRepo({ "AGENTS.md": "hub\n" }, (hubDir) => {
      const result = runHook(HOOK, { input: edit(join(serverDir, COMMITTED)), env: { CLAUDE_PROJECT_DIR: hubDir } });
      assert.equal(result.status, 2);
    });
  });
});

test("guards versioned migrations in subfolders of db/migration, like Flyway and the CI check", () => {
  const nested = `${MIG_DIR}/2026/V202610091300__nested.sql`;
  withRepo({ [nested]: "x\n" }, (dir) => {
    assert.equal(runHook(HOOK, { input: edit(join(dir, nested)), env: { CLAUDE_PROJECT_DIR: dir } }).status, 2);
  });
});

test("guards only files named V*.sql, not every file under a folder whose name starts with V", () => {
  const notMigration = `${MIG_DIR}/V1/notes.sql`;
  withRepo({ [notMigration]: "x\n" }, (dir) => {
    assert.equal(runHook(HOOK, { input: edit(join(dir, notMigration)), env: { CLAUDE_PROJECT_DIR: dir } }).status, 0);
  });
});

test("does not guard repeatable migrations or other files", () => {
  const repeatable = `${MIG_DIR}/R__refresh_views.sql`;
  withRepo({ [repeatable]: "x\n", "AGENTS.md": "x\n" }, (dir) => {
    assert.equal(runHook(HOOK, { input: edit(join(dir, repeatable)), env: { CLAUDE_PROJECT_DIR: dir } }).status, 0);
    assert.equal(runHook(HOOK, { input: edit(join(dir, "AGENTS.md")), env: { CLAUDE_PROJECT_DIR: dir } }).status, 0);
  });
});

test("lets the edit through when the input is not valid JSON (convenience guard, fail open)", () => {
  withRepo({ [COMMITTED]: "x\n" }, (dir) => {
    const result = runHook(HOOK, { input: "not json", env: { CLAUDE_PROJECT_DIR: dir } });
    assert.equal(result.status, 0);
  });
});

test("allows a staged migration in a repository without any commit yet", () => {
  withRepo(
    {},
    (dir) => {
      writeFile(dir, NEW, "x\n");
      git(dir, "add", NEW);
      const result = runHook(HOOK, { input: edit(join(dir, NEW)), env: { CLAUDE_PROJECT_DIR: dir } });
      assert.equal(result.status, 0, result.stderr);
    },
    { commit: false },
  );
});
