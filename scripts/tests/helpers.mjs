// Test helpers for the tooling scripts (Claude/Codex hooks, CI checks): temporary Git repos and a bash runner.
// Run the tests with: node --test "scripts/tests/*.test.mjs"
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export const REPO_DIR = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
export const HOOKS_DIR = join(REPO_DIR, ".claude", "hooks");

/**
 * Git Bash on Windows (never WSL's System32 bash), plain bash elsewhere. SUNVIBE_BASH overrides.
 * <git>/bin/bash.exe first: it puts /usr/bin on PATH, so coreutils work from a PowerShell or cmd PATH too.
 */
export function findBash() {
  if (process.env.SUNVIBE_BASH) return process.env.SUNVIBE_BASH;
  if (process.platform !== "win32") return "bash";
  const execPath = spawnSync("git", ["--exec-path"], { encoding: "utf8" }).stdout.trim();
  // <git root>/mingw64/libexec/git-core -> <git root>
  const root = join(execPath, "..", "..", "..");
  for (const candidate of [join(root, "bin", "bash.exe"), join(root, "usr", "bin", "bash.exe")]) {
    if (existsSync(candidate)) return candidate;
  }
  throw new Error("Git Bash not found; set SUNVIBE_BASH");
}

export function git(cwd, ...args) {
  const result = spawnSync("git", args, { cwd, encoding: "utf8" });
  if (result.status !== 0) throw new Error(`git ${args.join(" ")} failed: ${result.stderr}`);
  return result.stdout.trim();
}

/** A fresh repo with one commit holding `files` ({relativePath: content}). */
export function makeRepo(files = {}, { prefix = "sv-hook-", commit = true } = {}) {
  const parent = mkdtempSync(join(tmpdir(), prefix));
  const dir = join(parent, "repo");
  mkdirSync(dir);
  git(dir, "init", "-q", "-b", "main");
  git(dir, "config", "user.email", "test@example.com");
  git(dir, "config", "user.name", "test");
  git(dir, "config", "core.autocrlf", "false");
  for (const [path, content] of Object.entries(files)) writeFile(dir, path, content);
  if (commit && Object.keys(files).length > 0) {
    git(dir, "add", "-A");
    git(dir, "commit", "-q", "-m", "init");
  }
  return { dir, cleanup: () => rmSync(parent, { recursive: true, force: true }) };
}

export function writeFile(dir, path, content) {
  const full = join(dir, path);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, content);
  return full;
}

/** Runs a bash script with `args` and `input` on stdin; returns {status, stdout, stderr}. */
export function runHook(script, { input = "", cwd, env = {}, args = [] } = {}) {
  const baseEnv = { ...process.env };
  delete baseEnv.CLAUDE_PROJECT_DIR;
  const result = spawnSync(findBash(), [script, ...args], {
    cwd,
    input: typeof input === "string" ? input : JSON.stringify(input),
    encoding: "utf8",
    env: { ...baseEnv, ...env },
  });
  if (result.error) throw result.error;
  return { status: result.status, stdout: result.stdout, stderr: result.stderr };
}
