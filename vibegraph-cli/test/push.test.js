/**
 * Push tests — verify the CLI payload only ever contains POSIX relative paths,
 * never an absolute local path or a Windows drive path. Uses a stub apiRequest
 * so no real HTTP is issued.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { executePush } from "../lib/push.js";

async function makeProjectRoot() {
  return await mkdtemp(path.join(tmpdir(), "vg-push-"));
}

function makeStubApi() {
  const calls = [];
  return {
    calls,
    async apiRequest(url, options) {
      calls.push({ url, options });
      return { rejected: [] };
    },
  };
}

test("payload paths are POSIX-relative, never absolute, never contain backslash or drive prefix", async () => {
  const root = await makeProjectRoot();
  const prevConfig = process.env.VIBEGRAPH_CONFIG_DIR;
  const snapDir = await mkdtemp(path.join(tmpdir(), "vg-push-cfg-"));
  try {
    process.env.VIBEGRAPH_CONFIG_DIR = snapDir;

    // Real source-like files (only .java is ever pushed)
    await mkdir(path.join(root, "src", "main", "java"), { recursive: true });
    await writeFile(path.join(root, "src", "main", "java", "App.java"), "class App {}\n");
    await writeFile(path.join(root, "src", "main", "java", "Util.java"), "class Util {}\n");
    await writeFile(path.join(root, "README.md"), "# hi\n");

    // Files that MUST be excluded from the payload
    await writeFile(path.join(root, ".env"), "SECRET=xxx\n");
    await mkdir(path.join(root, "node_modules"), { recursive: true });
    await writeFile(path.join(root, "node_modules", "junk.js"), "module.exports = 1;\n");
    await writeFile(path.join(root, "id_rsa"), "-----BEGIN RSA PRIVATE KEY-----\n");
    await writeFile(path.join(root, "leaf.pem"), "cert\n");
    await writeFile(path.join(root, "bundle.tar.gz"), "archive\n");

    const stub = makeStubApi();
    await executePush("proj-push", { root, dryRun: false }, stub.apiRequest);

    assert.equal(stub.calls.length, 1, "should call the backend exactly once");
    const body = stub.calls[0].options.body;
    assert.ok(Array.isArray(body.files), "payload.files should be an array");

    for (const entry of body.files) {
      assert.equal(typeof entry.path, "string");
      // POSIX-relative invariants
      assert.equal(path.isAbsolute(entry.path), false, `path must be relative: ${entry.path}`);
      assert.equal(entry.path.includes("\\"), false, `path must not contain backslash: ${entry.path}`);
      assert.equal(/^[a-zA-Z]:/.test(entry.path), false, `path must not have drive prefix: ${entry.path}`);
      assert.equal(entry.path.startsWith("/"), false, `path must not be absolute POSIX: ${entry.path}`);
      // Denied filenames must not appear
      assert.equal(entry.path.endsWith(".env"), false);
      assert.equal(entry.path.startsWith(".env"), false);
      assert.equal(entry.path.includes("node_modules/"), false);
      assert.equal(entry.path.endsWith("id_rsa"), false);
      assert.equal(entry.path.endsWith(".pem"), false);
      assert.equal(entry.path.endsWith(".tar.gz"), false);
      // base64 encoding is declared
      assert.equal(entry.encoding, "base64");
      assert.equal(typeof entry.contentBase64, "string");
    }

    // Payload MUST contain the safe .java files at their POSIX-relative paths,
    // and only those — non-Java files like README.md are skipped.
    const paths = body.files.map((f) => f.path).sort();
    assert.deepEqual(paths, ["src/main/java/App.java", "src/main/java/Util.java"]);
  } finally {
    if (prevConfig === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = prevConfig;
    await rm(snapDir, { recursive: true, force: true });
    await rm(root, { recursive: true, force: true });
  }
});

test("dry-run does not write a snapshot even when it succeeds against the backend", async () => {
  const root = await makeProjectRoot();
  const snapDir = await mkdtemp(path.join(tmpdir(), "vg-push-cfg-"));
  const prevConfig = process.env.VIBEGRAPH_CONFIG_DIR;
  try {
    process.env.VIBEGRAPH_CONFIG_DIR = snapDir;

    await writeFile(path.join(root, "A.java"), "class A {}\n");

    const stub = makeStubApi();
    await executePush("proj-dry", { root, dryRun: true }, stub.apiRequest);

    // The last call must have dryRun=true
    const body = stub.calls.at(-1).options.body;
    assert.equal(body.dryRun, true);

    // No snapshot file should have been written for a dry-run.
    const snapFile = path.join(snapDir, "projects", "proj-dry.json");
    try {
      await import("node:fs/promises").then(({ stat }) => stat(snapFile));
      assert.fail("dry-run must not write a snapshot");
    } catch (err) {
      assert.equal(err.code, "ENOENT");
    }
  } finally {
    if (prevConfig === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = prevConfig;
    await rm(snapDir, { recursive: true, force: true });
    await rm(root, { recursive: true, force: true });
  }
});

test("no files to push emits a no-op, does not call backend when nothing changed since last snapshot", async () => {
  const root = await makeProjectRoot();
  const snapDir = await mkdtemp(path.join(tmpdir(), "vg-push-cfg-"));
  const prevConfig = process.env.VIBEGRAPH_CONFIG_DIR;
  try {
    process.env.VIBEGRAPH_CONFIG_DIR = snapDir;

    await writeFile(path.join(root, "Same.java"), "class Same {}\n");

    // First push seeds the snapshot with current state.
    const first = makeStubApi();
    await executePush("proj-noop", { root, dryRun: false }, first.apiRequest);
    assert.equal(first.calls.length, 1);

    // Second push with no changes should not hit the backend.
    const second = makeStubApi();
    await executePush("proj-noop", { root, dryRun: false }, second.apiRequest);
    assert.equal(second.calls.length, 0, "no changes should skip the backend call");
  } finally {
    if (prevConfig === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = prevConfig;
    await rm(snapDir, { recursive: true, force: true });
    await rm(root, { recursive: true, force: true });
  }
});

test("project-bound push without projectId uses the current-project endpoint and API-key auth", async () => {
  const root = await makeProjectRoot();
  const snapDir = await mkdtemp(path.join(tmpdir(), "vg-push-cfg-"));
  const prevConfig = process.env.VIBEGRAPH_CONFIG_DIR;
  try {
    process.env.VIBEGRAPH_CONFIG_DIR = snapDir;
    await writeFile(path.join(root, "Current.java"), "class Current {}\n");
    const stub = makeStubApi();

    await executePush(null, {
      root,
      dryRun: false,
      snapshotId: "api-key-1234",
    }, stub.apiRequest);

    assert.equal(stub.calls[0].url, "/api/projects/current/patch");
    assert.equal(stub.calls[0].options.auth, "api-key-first");
  } finally {
    if (prevConfig === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = prevConfig;
    await rm(snapDir, { recursive: true, force: true });
    await rm(root, { recursive: true, force: true });
  }
});

test("truncated scans fail closed and never send deletion payloads", async () => {
  const root = await makeProjectRoot();
  const snapDir = await mkdtemp(path.join(tmpdir(), "vg-push-cfg-"));
  const prevConfig = process.env.VIBEGRAPH_CONFIG_DIR;
  const prevMaxFiles = process.env.VIBEGRAPH_MAX_FILES;
  try {
    process.env.VIBEGRAPH_CONFIG_DIR = snapDir;
    delete process.env.VIBEGRAPH_MAX_FILES;
    await writeFile(path.join(root, "a.java"), "class A {}\n");
    await writeFile(path.join(root, "b.java"), "class B {}\n");
    await writeFile(path.join(root, "c.java"), "class C {}\n");
    const stub = makeStubApi();

    await executePush("proj-truncated", { root, dryRun: false }, stub.apiRequest);
    assert.equal(stub.calls.length, 1);

    process.env.VIBEGRAPH_MAX_FILES = "1";
    await assert.rejects(
      executePush("proj-truncated", { root, dryRun: false }, stub.apiRequest),
      /partial scan could delete files incorrectly/,
    );

    assert.equal(stub.calls.length, 1, "truncated push must not call backend again");
    const snapshot = JSON.parse(
      await readFile(path.join(snapDir, "projects", "proj-truncated.json"), "utf8"),
    );
    assert.deepEqual(Object.keys(snapshot.files).sort(), [
      "a.java",
      "b.java",
      "c.java",
    ]);
  } finally {
    if (prevConfig === undefined) delete process.env.VIBEGRAPH_CONFIG_DIR;
    else process.env.VIBEGRAPH_CONFIG_DIR = prevConfig;
    if (prevMaxFiles === undefined) delete process.env.VIBEGRAPH_MAX_FILES;
    else process.env.VIBEGRAPH_MAX_FILES = prevMaxFiles;
    await rm(snapDir, { recursive: true, force: true });
    await rm(root, { recursive: true, force: true });
  }
});
