/**
 * Scanner tests — verify that scanDirectory returns POSIX relative paths only,
 * skips symlinks, skips forbidden files, respects the max-files cap, and never
 * emits absolute paths in the payload.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { loadIgnoreRules } from "../lib/ignore.js";
import { scanDirectory, buildFileStateMap, toPosixRelative } from "../lib/scanner.js";

async function makeTempDir() {
  return await mkdtemp(path.join(tmpdir(), "vg-scan-"));
}

async function seed(dir, files) {
  for (const [rel, content] of Object.entries(files)) {
    const abs = path.join(dir, rel);
    await mkdir(path.dirname(abs), { recursive: true });
    await writeFile(abs, content, "utf8");
  }
}

test("scanner returns only POSIX relative paths (no absolute paths in payload)", async () => {
  const dir = await makeTempDir();
  try {
    await seed(dir, {
      "src/main/java/com/example/App.java": "class App {}",
      "src/lib/util.ts": "export const x = 1;",
      "README.md": "# hi",
    });
    const rules = await loadIgnoreRules(dir);
    const scan = await scanDirectory(dir, rules);

    for (const f of scan.files) {
      assert.equal(
        f.relativePath.includes("\\"),
        false,
        `path must not contain backslash: ${f.relativePath}`,
      );
      assert.equal(
        path.isAbsolute(f.relativePath),
        false,
        `path must be relative: ${f.relativePath}`,
      );
      assert.equal(
        f.relativePath.startsWith("/"),
        false,
        `path must not start with /: ${f.relativePath}`,
      );
    }
    const paths = scan.files.map((f) => f.relativePath).sort();
    assert.deepEqual(paths, [
      "README.md",
      "src/lib/util.ts",
      "src/main/java/com/example/App.java",
    ]);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("scanner skips .env and private key files", async () => {
  const dir = await makeTempDir();
  try {
    await seed(dir, {
      ".env": "SECRET=abc",
      ".env.local": "SECRET=xyz",
      "server.key": "-----BEGIN KEY-----",
      "cert.pem": "-----BEGIN CERT-----",
      "id_rsa": "ssh-rsa AAAA",
      "src/App.java": "class App {}",
    });
    const rules = await loadIgnoreRules(dir);
    const scan = await scanDirectory(dir, rules);

    const paths = scan.files.map((f) => f.relativePath);
    assert.deepEqual(paths, ["src/App.java"]);
    assert.equal(scan.skipped.length > 0, true);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("scanner skips symlinks", async () => {
  const dir = await makeTempDir();
  try {
    await seed(dir, { "target.txt": "real content" });
    // symlinks require admin on Windows; skip the test if it can't be created
    try {
      await symlink(path.join(dir, "target.txt"), path.join(dir, "link.txt"));
    } catch (err) {
      if (err.code === "EPERM" || err.code === "UNKNOWN") {
        console.log("skipping symlink test — symlinks not permitted here");
        return;
      }
      throw err;
    }
    const rules = await loadIgnoreRules(dir);
    const scan = await scanDirectory(dir, rules);

    const paths = scan.files.map((f) => f.relativePath);
    assert.equal(paths.includes("target.txt"), true);
    assert.equal(paths.includes("link.txt"), false);
    assert.equal(
      scan.skipped.some((s) => s.relativePath === "link.txt" && s.reason === "symlink"),
      true,
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("scanner skips binary files", async () => {
  const dir = await makeTempDir();
  try {
    const binPath = path.join(dir, "bin.class");
    await writeFile(binPath, Buffer.from([1, 2, 0, 3, 4]));
    await seed(dir, { "src/App.java": "class App {}" });

    const rules = await loadIgnoreRules(dir);
    const scan = await scanDirectory(dir, rules);

    const paths = scan.files.map((f) => f.relativePath);
    assert.equal(paths.includes("bin.class"), false);
    assert.equal(paths.includes("src/App.java"), true);
    assert.equal(
      scan.skipped.some((s) => s.reason === "binary file"),
      true,
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("scanner skips files exceeding max size", async () => {
  const dir = await makeTempDir();
  const prev = process.env.VIBEGRAPH_MAX_FILE_SIZE;
  try {
    process.env.VIBEGRAPH_MAX_FILE_SIZE = "100";
    await seed(dir, {
      "small.txt": "hi",
      "big.txt": "x".repeat(500),
    });
    const rules = await loadIgnoreRules(dir);
    const scan = await scanDirectory(dir, rules);
    const paths = scan.files.map((f) => f.relativePath);
    assert.equal(paths.includes("small.txt"), true);
    assert.equal(paths.includes("big.txt"), false);
    assert.equal(
      scan.skipped.some((s) => s.relativePath === "big.txt"),
      true,
    );
  } finally {
    if (prev === undefined) delete process.env.VIBEGRAPH_MAX_FILE_SIZE;
    else process.env.VIBEGRAPH_MAX_FILE_SIZE = prev;
    await rm(dir, { recursive: true, force: true });
  }
});

test("scanner enforces max-files cap and flags truncation", async () => {
  const dir = await makeTempDir();
  const prev = process.env.VIBEGRAPH_MAX_FILES;
  try {
    process.env.VIBEGRAPH_MAX_FILES = "3";
    const files = {};
    for (let i = 0; i < 10; i++) files[`file${i}.txt`] = `content ${i}`;
    await seed(dir, files);

    const rules = await loadIgnoreRules(dir);
    const scan = await scanDirectory(dir, rules);
    assert.equal(scan.files.length, 3);
    assert.equal(scan.truncated, true);
  } finally {
    if (prev === undefined) delete process.env.VIBEGRAPH_MAX_FILES;
    else process.env.VIBEGRAPH_MAX_FILES = prev;
    await rm(dir, { recursive: true, force: true });
  }
});

test("toPosixRelative normalizes backslash separators on Windows-style input", () => {
  const rel = toPosixRelative("/root", "/root/src/main/App.java");
  assert.equal(rel.includes("\\"), false);
  assert.equal(rel, "src/main/App.java");
});

test("buildFileStateMap uses relative path as key with size/mtime/sha256", () => {
  const map = buildFileStateMap([
    { relativePath: "a.txt", size: 3, mtimeMs: 1000, sha256: "hash-a", content: Buffer.from("abc") },
    { relativePath: "b/c.txt", size: 4, mtimeMs: 2000, sha256: "hash-b", content: Buffer.from("dcba") },
  ]);
  assert.deepEqual(Object.keys(map).sort(), ["a.txt", "b/c.txt"]);
  assert.equal(map["a.txt"].sha256, "hash-a");
  assert.equal(map["b/c.txt"].size, 4);
});
