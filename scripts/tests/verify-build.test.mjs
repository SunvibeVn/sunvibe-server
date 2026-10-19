// Behavior of .claude/hooks/verify-build.sh (Stop): a compile-only smoke check, run when Java, Gradle or SQL files
// changed in the working tree. It never runs tests; ./gradlew build stays the real check before "done".
import { test } from "node:test";
import assert from "node:assert/strict";
import { chmodSync, existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { HOOKS_DIR, makeRepo, runHook, writeFile } from "./helpers.mjs";

const HOOK = join(HOOKS_DIR, "verify-build.sh");

/** A repo whose ./gradlew records its arguments and exits with `exitCode`. */
function repoWithFakeGradle(exitCode, output = "") {
  const repo = makeRepo({
    "gradlew": `#!/usr/bin/env bash\necho "$*" >> gradlew-calls.txt\nprintf '%s\\n' ${JSON.stringify(output)}\nexit ${exitCode}\n`,
    "app/src/main/java/App.java": "class App {}\n",
    "README.md": "x\n",
  });
  // Linux runs ./gradlew only when it is executable (the real one is committed as 100755).
  chmodSync(join(repo.dir, "gradlew"), 0o755);
  return { ...repo, calls: () => (existsSync(join(repo.dir, "gradlew-calls.txt")) ? readFileSync(join(repo.dir, "gradlew-calls.txt"), "utf8") : "") };
}

test("compiles main and test code when a Java file changed", () => {
  const repo = repoWithFakeGradle(0);
  try {
    writeFile(repo.dir, "app/src/main/java/App.java", "class App { int x; }\n");
    const result = runHook(HOOK, { input: { stop_hook_active: false }, env: { CLAUDE_PROJECT_DIR: repo.dir } });
    assert.equal(result.status, 0, result.stderr);
    assert.match(repo.calls(), /compileJava compileTestJava/);
  } finally {
    repo.cleanup();
  }
});

test("blocks the stop with the compiler output when compilation fails", () => {
  const repo = repoWithFakeGradle(1, "App.java:1: error: ';' expected");
  try {
    writeFile(repo.dir, "app/src/main/java/App.java", "class App { int x }\n");
    const result = runHook(HOOK, { input: { stop_hook_active: false }, env: { CLAUDE_PROJECT_DIR: repo.dir } });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /';' expected/);
  } finally {
    repo.cleanup();
  }
});

test("does nothing when only non-code files changed", () => {
  const repo = repoWithFakeGradle(1);
  try {
    writeFile(repo.dir, "README.md", "changed\n");
    const result = runHook(HOOK, { input: { stop_hook_active: false }, env: { CLAUDE_PROJECT_DIR: repo.dir } });
    assert.equal(result.status, 0);
    assert.equal(repo.calls(), "");
  } finally {
    repo.cleanup();
  }
});

test("does nothing on the continuation of a stop it already blocked", () => {
  const repo = repoWithFakeGradle(1);
  try {
    writeFile(repo.dir, "app/src/main/java/App.java", "broken\n");
    const result = runHook(HOOK, { input: { stop_hook_active: true }, env: { CLAUDE_PROJECT_DIR: repo.dir } });
    assert.equal(result.status, 0);
    assert.equal(repo.calls(), "");
  } finally {
    repo.cleanup();
  }
});

test("finds the repository root when started from a subdirectory without CLAUDE_PROJECT_DIR (Codex)", () => {
  const repo = repoWithFakeGradle(0);
  try {
    writeFile(repo.dir, "app/src/main/java/App.java", "class App { int y; }\n");
    const result = runHook(HOOK, { input: { stop_hook_active: false }, cwd: join(repo.dir, "app") });
    assert.equal(result.status, 0, result.stderr);
    assert.match(repo.calls(), /compileJava compileTestJava/);
  } finally {
    repo.cleanup();
  }
});
