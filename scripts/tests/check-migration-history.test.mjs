// Behavior of scripts/ci/check-migration-history.sh <base> [--include-worktree]: fails when a versioned Flyway
// migration that exists at the fork point with <base> is modified, deleted or renamed on this branch.
import { test } from "node:test";
import assert from "node:assert/strict";
import { join } from "node:path";
import { git, makeRepo, REPO_DIR, runHook, writeFile } from "./helpers.mjs";

const SCRIPT = join(REPO_DIR, "scripts", "ci", "check-migration-history.sh");
const MIG = "modules/auth-service/src/main/resources/db/migration";
const BASE_MIG = `${MIG}/V202610091200__create_roles_table.sql`;

/** main has one migration; the checkout is on branch feature/x, forked from main. */
function withBranch(fn, options) {
  const repo = makeRepo({ [BASE_MIG]: "create table roles();\n", "README.md": "x\n" }, options);
  try {
    git(repo.dir, "checkout", "-q", "-b", "feature/x");
    return fn(repo.dir);
  } finally {
    repo.cleanup();
  }
}

function commitAll(dir, message) {
  git(dir, "add", "-A");
  git(dir, "commit", "-q", "-m", message);
}

test("passes when the branch only adds a new migration", () => {
  withBranch((dir) => {
    writeFile(dir, `${MIG}/V202610191200__add_x.sql`, "alter table roles add column x int;\n");
    commitAll(dir, "add migration");
    const result = runHook(SCRIPT, { cwd: dir, args: ["main"] });
    assert.equal(result.status, 0, result.stdout + result.stderr);
  });
});

test("fails when the branch modifies a migration that exists on the base", () => {
  withBranch((dir) => {
    writeFile(dir, BASE_MIG, "create table roles(id int);\n");
    commitAll(dir, "edit migration");
    const result = runHook(SCRIPT, { cwd: dir, args: ["main"] });
    assert.equal(result.status, 1);
    assert.match(result.stdout + result.stderr, /V202610091200__create_roles_table\.sql/);
  });
});

test("javaMigrationEdit_failsHistoryCheck: a Java migration has no checksum, so this is the only guard", () => {
  const javaMigration = "modules/auth-service/src/main/java/db/migration/V202610091300__Backfill_x.java";
  const repo = makeRepo({ [javaMigration]: "class V202610091300__Backfill_x {}\n", "README.md": "x\n" });
  try {
    git(repo.dir, "checkout", "-q", "-b", "feature/x");
    writeFile(repo.dir, javaMigration, "class V202610091300__Backfill_x { int changed; }\n");
    commitAll(repo.dir, "edit java migration");
    const result = runHook(SCRIPT, { cwd: repo.dir, args: ["main"] });
    assert.equal(result.status, 1);
    assert.match(result.stdout + result.stderr, /V202610091300__Backfill_x\.java/);
  } finally {
    repo.cleanup();
  }
});

test("fails when the branch edits the logic class of a Java migration", () => {
  const logic = "modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/migration/Backfill.java";
  const repo = makeRepo({ [logic]: "class Backfill {}\n", "README.md": "x\n" });
  try {
    git(repo.dir, "checkout", "-q", "-b", "feature/x");
    writeFile(repo.dir, logic, "class Backfill { int changed; }\n");
    commitAll(repo.dir, "edit backfill logic");
    assert.equal(runHook(SCRIPT, { cwd: repo.dir, args: ["main"] }).status, 1);
  } finally {
    repo.cleanup();
  }
});

test("passes when the branch only adds a new Java migration", () => {
  withBranch((dir) => {
    writeFile(dir, "modules/auth-service/src/main/java/db/migration/V202610191300__Backfill_y.java", "class Y {}\n");
    commitAll(dir, "add java migration");
    const result = runHook(SCRIPT, { cwd: dir, args: ["main"] });
    assert.equal(result.status, 0, result.stdout + result.stderr);
  });
});

test("fails when the branch deletes or renames a base migration", () => {
  withBranch((dir) => {
    git(dir, "mv", BASE_MIG, `${MIG}/V202610091201__create_roles_table.sql`);
    commitAll(dir, "rename migration");
    assert.equal(runHook(SCRIPT, { cwd: dir, args: ["main"] }).status, 1);
  });
  withBranch((dir) => {
    git(dir, "rm", "-q", BASE_MIG);
    commitAll(dir, "delete migration");
    assert.equal(runHook(SCRIPT, { cwd: dir, args: ["main"] }).status, 1);
  });
});

test("ignores migrations added to the base after the fork point", () => {
  withBranch((dir) => {
    git(dir, "checkout", "-q", "main");
    writeFile(dir, `${MIG}/V202610201200__later_on_main.sql`, "select 1;\n");
    commitAll(dir, "main moves on");
    git(dir, "checkout", "-q", "feature/x");
    writeFile(dir, "README.md", "branch work\n");
    commitAll(dir, "branch work");
    const result = runHook(SCRIPT, { cwd: dir, args: ["main"] });
    assert.equal(result.status, 0, result.stdout + result.stderr);
  });
});

test("allows changing a migration that the branch itself added", () => {
  withBranch((dir) => {
    const own = `${MIG}/V202610191200__add_x.sql`;
    writeFile(dir, own, "alter table roles add column x int;\n");
    commitAll(dir, "add");
    writeFile(dir, own, "alter table roles add column x bigint;\n");
    commitAll(dir, "fix own migration before merge");
    assert.equal(runHook(SCRIPT, { cwd: dir, args: ["main"] }).status, 0);
  });
});

test("does not guard repeatable migrations", () => {
  const repeatable = `${MIG}/R__views.sql`;
  const repo = makeRepo({ [repeatable]: "create view v as select 1;\n" });
  try {
    git(repo.dir, "checkout", "-q", "-b", "feature/x");
    writeFile(repo.dir, repeatable, "create view v as select 2;\n");
    commitAll(repo.dir, "change view");
    assert.equal(runHook(SCRIPT, { cwd: repo.dir, args: ["main"] }).status, 0);
  } finally {
    repo.cleanup();
  }
});

test("fails closed when the base ref cannot be resolved", () => {
  withBranch((dir) => {
    const result = runHook(SCRIPT, { cwd: dir, args: ["origin/missing"] });
    assert.equal(result.status, 1);
    assert.match(result.stdout + result.stderr, /fetch-depth: 0/);
  });
});

test("has nothing to compare for a push that creates a branch (all-zero base)", () => {
  withBranch((dir) => {
    const result = runHook(SCRIPT, { cwd: dir, args: ["0000000000000000000000000000000000000000"] });
    assert.equal(result.status, 0);
    assert.match(result.stdout, /nothing to compare/);
  });
});

test("checks uncommitted changes only with --include-worktree", () => {
  withBranch((dir) => {
    writeFile(dir, BASE_MIG, "uncommitted edit\n");
    assert.equal(runHook(SCRIPT, { cwd: dir, args: ["main"] }).status, 0);
    assert.equal(runHook(SCRIPT, { cwd: dir, args: ["main", "--include-worktree"] }).status, 1);
  });
});

test("works like CI: origin/main as base and a detached pull-request merge commit as HEAD", () => {
  withBranch((dir) => {
    writeFile(dir, BASE_MIG, "edited on the branch\n");
    commitAll(dir, "edit migration");
    const clone = join(dir, "..", "ci-clone");
    git(join(dir, ".."), "clone", "-q", "--no-local", dir, clone);
    git(clone, "config", "user.email", "t@example.com");
    git(clone, "config", "user.name", "t");
    git(clone, "checkout", "-q", "--detach", "origin/main");
    git(clone, "merge", "-q", "--no-ff", "-m", "pr merge", "origin/feature/x");
    const result = runHook(SCRIPT, { cwd: clone, args: ["origin/main"] });
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(result.stderr, /V202610091200__create_roles_table\.sql/);
  });
});

test("works in a repository path with spaces", () => {
  withBranch(
    (dir) => {
      writeFile(dir, BASE_MIG, "changed\n");
      commitAll(dir, "edit");
      assert.equal(runHook(SCRIPT, { cwd: dir, args: ["main"] }).status, 1);
    },
    { prefix: "sv ci with space " },
  );
});
