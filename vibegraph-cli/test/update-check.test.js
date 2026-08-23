import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import {
  getLatestVersion,
  isNewerVersion,
} from "../lib/update-check.js";

test("version comparison only reports a newer semantic version", () => {
  assert.equal(isNewerVersion("0.1.3", "0.1.4"), true);
  assert.equal(isNewerVersion("0.1.3", "1.0.0"), true);
  assert.equal(isNewerVersion("0.1.3", "0.1.3"), false);
  assert.equal(isNewerVersion("0.1.3", "0.1.2"), false);
  assert.equal(isNewerVersion("dev", "0.1.4"), false);
});

test("latest version check caches the registry result", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-update-check-"));
  let calls = 0;
  const fetchImpl = async () => {
    calls += 1;
    return { ok: true, async json() { return { version: "0.1.4" }; } };
  };
  try {
    assert.equal(await getLatestVersion({ currentVersion: "0.1.3", configDir, fetchImpl, now: 1000 }), "0.1.4");
    assert.equal(await getLatestVersion({ currentVersion: "0.1.3", configDir, fetchImpl, now: 2000 }), "0.1.4");
    assert.equal(calls, 1);
    const cache = JSON.parse(await readFile(path.join(configDir, "update-check.json"), "utf8"));
    assert.equal(cache.latestVersion, "0.1.4");
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});

test("latest version check fails silently when the registry is unavailable", async () => {
  const configDir = await mkdtemp(path.join(tmpdir(), "vg-update-check-"));
  try {
    const result = await getLatestVersion({
      currentVersion: "0.1.3",
      configDir,
      fetchImpl: async () => { throw new Error("offline"); },
    });
    assert.equal(result, null);
  } finally {
    await rm(configDir, { recursive: true, force: true });
  }
});
