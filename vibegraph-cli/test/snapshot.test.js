/**
 * Snapshot tests — critical property: VIBEGRAPH_CONFIG_DIR must fully redirect
 * both reads and writes, so tests (and CI) never touch the real user home dir.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm, stat, readFile } from "node:fs/promises";
import { tmpdir, homedir } from "node:os";
import path from "node:path";

import { loadSnapshot, saveSnapshot, diffSnapshot, computeHash } from "../lib/snapshot.js";

async function makeTempDir() {
  return await mkdtemp(path.join(tmpdir(), "vg-snap-"));
}

test("saveSnapshot writes under VIBEGRAPH_CONFIG_DIR, never under real home", async () => {
  const dir = await makeTempDir();
  const prev = process.env.VIBEGRAPH_CONFIG_DIR;
  try {
    process.env.VIBEGRAPH_CONFIG_DIR = dir;
    const files = {
      "src/App.java": { size: 12, mtimeMs: 1000, sha256: "hash-a" },
    };
    await saveSnapshot("proj-1", files);

    const snapPath = path.join(dir, "projects", "proj-1.json");
    const stats = await stat(snapPath);
    assert.equal(stats.isFile(), true);

    // Nothing should exist under real ~/.vibegraph/projects/proj-1.json for this test.
    const realHomeSnap = path.join(homedir(), ".vibegraph", "projects", "proj-1.json");
    try {
      await stat(realHomeSnap);
      // If it *does* exist, it was there before the test — check it's not the file we just wrote.
      const written = await readFile(snapPath, "utf8");
      const real = await readFile(realHomeSnap, "utf8");
      assert.notEqual(written, real, "test snapshot must not equal real home snapshot");
    } catch (err) {
      if (err.code !== "ENOENT") throw err;
      // ENOENT is the good case — real home is untouched.
    }
  } finally {
    if (prev === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = prev;
    await rm(dir, { recursive: true, force: true });
  }
});

test("loadSnapshot returns {} when no snapshot exists", async () => {
  const dir = await makeTempDir();
  const prev = process.env.VIBEGRAPH_CONFIG_DIR;
  try {
    process.env.VIBEGRAPH_CONFIG_DIR = dir;
    const loaded = await loadSnapshot("does-not-exist");
    assert.deepEqual(loaded, {});
  } finally {
    if (prev === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = prev;
    await rm(dir, { recursive: true, force: true });
  }
});

test("save then load round-trips file state", async () => {
  const dir = await makeTempDir();
  const prev = process.env.VIBEGRAPH_CONFIG_DIR;
  try {
    process.env.VIBEGRAPH_CONFIG_DIR = dir;
    const files = {
      "a.txt": { size: 1, mtimeMs: 100, sha256: "aaa" },
      "b/c.txt": { size: 2, mtimeMs: 200, sha256: "bbb" },
    };
    await saveSnapshot("proj-2", files);
    const loaded = await loadSnapshot("proj-2");
    assert.deepEqual(loaded, files);
  } finally {
    if (prev === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = prev;
    await rm(dir, { recursive: true, force: true });
  }
});

test("diffSnapshot detects added, changed, and deleted files", () => {
  const previous = {
    "unchanged.txt": { size: 3, mtimeMs: 100, sha256: "same" },
    "changed.txt": { size: 3, mtimeMs: 100, sha256: "old" },
    "gone.txt": { size: 3, mtimeMs: 100, sha256: "bye" },
  };
  const current = {
    "unchanged.txt": { size: 3, mtimeMs: 100, sha256: "same" },
    "changed.txt": { size: 3, mtimeMs: 200, sha256: "new" },
    "new.txt": { size: 5, mtimeMs: 300, sha256: "hello" },
  };
  const { changed, deleted } = diffSnapshot(current, previous);
  assert.deepEqual(changed.sort(), ["changed.txt", "new.txt"]);
  assert.deepEqual(deleted, ["gone.txt"]);
});

test("computeHash is deterministic SHA-256 of content", () => {
  const h1 = computeHash(Buffer.from("hello"));
  const h2 = computeHash(Buffer.from("hello"));
  assert.equal(h1, h2);
  // SHA-256 of "hello"
  assert.equal(h1, "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
});
