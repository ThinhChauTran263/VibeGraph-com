/**
 * Ignore rules tests — verify DEFAULT_DENY catches every secret / build / archive
 * pattern the security review requires. Run: `npm test` (uses node --test).
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import {
  loadIgnoreRules,
  shouldIgnore,
  isBinaryFile,
  getMaxFileSize,
  getMaxFiles,
} from "../lib/ignore.js";

async function makeTempDir() {
  return await mkdtemp(path.join(tmpdir(), "vg-ignore-"));
}

test("DEFAULT_DENY blocks .env and every .env.* variant", async () => {
  const dir = await makeTempDir();
  try {
    const rules = await loadIgnoreRules(dir);
    for (const p of [".env", ".env.local", ".env.production", ".env.staging"]) {
      const result = shouldIgnore(p, rules);
      assert.equal(result.ignored, true, `expected ${p} to be ignored`);
      assert.equal(result.reason, "secret pattern");
    }
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("DEFAULT_DENY blocks private keys and SSH key files at project root", async () => {
  const dir = await makeTempDir();
  try {
    const rules = await loadIgnoreRules(dir);
    // Root-level names — these are covered by the current matcher.
    for (const p of ["id_rsa", "id_dsa", "id_ed25519", "server.key", "cert.pem"]) {
      const result = shouldIgnore(p, rules);
      assert.equal(result.ignored, true, `expected ${p} to be ignored`);
      assert.equal(result.reason, "secret pattern");
    }
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("DEFAULT_DENY blocks nested private keys", async () => {
  const dir = await makeTempDir();
  try {
    const rules = await loadIgnoreRules(dir);
    for (const p of ["secrets/prod.pem", "config/id_rsa", "deep/nested/server.key"]) {
      const result = shouldIgnore(p, rules);
      assert.equal(result.ignored, true, `expected nested ${p} to be ignored by CLI`);
    }
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("DEFAULT_DENY blocks VCS, dependency, and build directories", async () => {
  const dir = await makeTempDir();
  try {
    const rules = await loadIgnoreRules(dir);
    assert.equal(shouldIgnore(".git/HEAD", rules).ignored, true);
    assert.equal(shouldIgnore("node_modules/foo/index.js", rules).ignored, true);
    assert.equal(shouldIgnore("dist/bundle.js", rules).ignored, true);
    assert.equal(shouldIgnore("target/classes/App.class", rules).ignored, true);
    assert.equal(shouldIgnore("build/output.txt", rules).ignored, true);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("DEFAULT_DENY blocks archive files", async () => {
  const dir = await makeTempDir();
  try {
    const rules = await loadIgnoreRules(dir);
    for (const p of ["bundle.zip", "src.tar", "bundle.tgz", "notes.gz", "old.rar", "backup.7z"]) {
      const result = shouldIgnore(p, rules);
      assert.equal(result.ignored, true, `expected ${p} to be ignored`);
      assert.equal(result.reason, "archive file");
    }
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("regular source files are not ignored", async () => {
  const dir = await makeTempDir();
  try {
    const rules = await loadIgnoreRules(dir);
    for (const p of [
      "src/main/java/com/example/App.java",
      "src/index.ts",
      "README.md",
      "package.json",
      "docs/guide.md",
    ]) {
      assert.equal(shouldIgnore(p, rules).ignored, false, `expected ${p} to be allowed`);
    }
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test(".vibegraphignore adds custom rules on top of DEFAULT_DENY", async () => {
  const dir = await makeTempDir();
  try {
    await writeFile(
      path.join(dir, ".vibegraphignore"),
      "# comment\ncustom_secret.txt\ninternal/**\n",
      "utf8",
    );
    const rules = await loadIgnoreRules(dir);
    assert.equal(shouldIgnore("custom_secret.txt", rules).ignored, true);
    assert.equal(shouldIgnore("internal/notes.md", rules).ignored, true);
    // DEFAULT_DENY still applies
    assert.equal(shouldIgnore(".env", rules).ignored, true);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("isBinaryFile detects NUL byte", async () => {
  const dir = await makeTempDir();
  try {
    const binaryPath = path.join(dir, "b.bin");
    const textPath = path.join(dir, "t.txt");
    await writeFile(binaryPath, Buffer.from([1, 2, 0, 3, 4]));
    await writeFile(textPath, "hello world", "utf8");

    assert.equal(await isBinaryFile(binaryPath), true);
    assert.equal(await isBinaryFile(textPath), false);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("getMaxFileSize respects VIBEGRAPH_MAX_FILE_SIZE env override", () => {
  const prev = process.env.VIBEGRAPH_MAX_FILE_SIZE;
  try {
    delete process.env.VIBEGRAPH_MAX_FILE_SIZE;
    assert.equal(getMaxFileSize(), 1024 * 1024);
    process.env.VIBEGRAPH_MAX_FILE_SIZE = "2048";
    assert.equal(getMaxFileSize(), 2048);
    process.env.VIBEGRAPH_MAX_FILE_SIZE = "not-a-number";
    assert.equal(getMaxFileSize(), 1024 * 1024);
  } finally {
    if (prev === undefined) delete process.env.VIBEGRAPH_MAX_FILE_SIZE;
    else process.env.VIBEGRAPH_MAX_FILE_SIZE = prev;
  }
});

test("getMaxFiles respects VIBEGRAPH_MAX_FILES env override", () => {
  const prev = process.env.VIBEGRAPH_MAX_FILES;
  try {
    delete process.env.VIBEGRAPH_MAX_FILES;
    assert.equal(getMaxFiles(), 200);
    process.env.VIBEGRAPH_MAX_FILES = "50";
    assert.equal(getMaxFiles(), 50);
  } finally {
    if (prev === undefined) delete process.env.VIBEGRAPH_MAX_FILES;
    else process.env.VIBEGRAPH_MAX_FILES = prev;
  }
});
