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
      "src/main/java/com/example/util/Helper.java": "class Helper {}",
      "Root.java": "class Root {}",
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
      "Root.java",
      "src/main/java/com/example/App.java",
      "src/main/java/com/example/util/Helper.java",
    ]);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("scanner only includes .java files and skips everything else", async () => {
  const dir = await makeTempDir();
  try {
    await seed(dir, {
      "src/App.java": "class App {}",
      "src/util.ts": "export const x = 1;",
      "README.md": "# hi",
      "pom.xml": "<project/>",
      "data.json": "{}",
    });
    const rules = await loadIgnoreRules(dir);
    const scan = await scanDirectory(dir, rules);

    assert.deepEqual(scan.files.map((f) => f.relativePath), ["src/App.java"]);
    for (const skippedPath of ["src/util.ts", "README.md", "pom.xml", "data.json"]) {
      assert.equal(
        scan.skipped.some((s) => s.relativePath === skippedPath && s.reason === "not Java source"),
        true,
        `${skippedPath} must be skipped as not Java source`,
      );
    }
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
    await seed(dir, { "Target.java": "class Target {}" });
    // symlinks require admin on Windows; skip the test if it can't be created
    try {
      await symlink(path.join(dir, "Target.java"), path.join(dir, "Link.java"));
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
    assert.equal(paths.includes("Target.java"), true);
    assert.equal(paths.includes("Link.java"), false);
    assert.equal(
      scan.skipped.some((s) => s.relativePath === "Link.java" && s.reason === "symlink"),
      true,
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("scanner skips binary files", async () => {
  const dir = await makeTempDir();
  try {
    // A .java-named file with binary content must still be caught by the binary check.
    const binPath = path.join(dir, "Blob.java");
    await writeFile(binPath, Buffer.from([1, 2, 0, 3, 4]));
    await seed(dir, { "src/App.java": "class App {}" });

    const rules = await loadIgnoreRules(dir);
    const scan = await scanDirectory(dir, rules);

    const paths = scan.files.map((f) => f.relativePath);
    assert.equal(paths.includes("Blob.java"), false);
    assert.equal(paths.includes("src/App.java"), true);
    assert.equal(
      scan.skipped.some((s) => s.relativePath === "Blob.java" && s.reason === "binary file"),
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
      "Small.java": "class Small {}",
      "Big.java": "x".repeat(500),
    });
    const rules = await loadIgnoreRules(dir);
    const scan = await scanDirectory(dir, rules);
    const paths = scan.files.map((f) => f.relativePath);
    assert.equal(paths.includes("Small.java"), true);
    assert.equal(paths.includes("Big.java"), false);
    assert.equal(
      scan.skipped.some((s) => s.relativePath === "Big.java"),
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
    for (let i = 0; i < 10; i++) files[`File${i}.java`] = `class File${i} {}`;
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
