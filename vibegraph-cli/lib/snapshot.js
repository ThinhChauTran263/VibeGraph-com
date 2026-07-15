/**
 * Snapshot module for vibegraph-cli.
 * Tracks local file state (path, size, mtimeMs, sha256) per project.
 * Stored at ~/.vibegraph/projects/<projectId>.json
 */

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { homedir } from "node:os";
import path from "node:path";

/**
 * Get snapshot directory path.
 */
function snapshotDir() {
  const configDir = process.env.VIBEGRAPH_CONFIG_DIR || path.join(homedir(), ".vibegraph");
  return path.join(configDir, "projects");
}

/**
 * Get snapshot file path for a project.
 * @param {string} projectId
 * @returns {string}
 */
function snapshotPath(projectId) {
  return path.join(snapshotDir(), `${projectId}.json`);
}

/**
 * Load existing snapshot for a project.
 * @param {string} projectId
 * @returns {Promise<Record<string, { size: number, mtimeMs: number, sha256: string }>>}
 */
export async function loadSnapshot(projectId) {
  try {
    const raw = await readFile(snapshotPath(projectId), "utf8");
    const data = JSON.parse(raw);
    return data.files || {};
  } catch (error) {
    if (error.code === "ENOENT") return {};
    throw error;
  }
}

/**
 * Save snapshot for a project.
 * @param {string} projectId
 * @param {Record<string, { size: number, mtimeMs: number, sha256: string }>} files
 */
export async function saveSnapshot(projectId, files) {
  const dir = snapshotDir();
  await mkdir(dir, { recursive: true });
  const data = {
    projectId,
    updatedAt: new Date().toISOString(),
    files,
  };
  await writeFile(snapshotPath(projectId), JSON.stringify(data, null, 2), "utf8");
}

/**
 * Compute SHA-256 hash of file content.
 * @param {Buffer} content
 * @returns {string}
 */
export function computeHash(content) {
  return createHash("sha256").update(content).digest("hex");
}

/**
 * Determine changes between current scan and saved snapshot.
 * @param {Record<string, { size: number, mtimeMs: number, sha256: string }>} currentFiles - Current file states
 * @param {Record<string, { size: number, mtimeMs: number, sha256: string }>} snapshotFiles - Previous snapshot
 * @returns {{ changed: string[], deleted: string[] }}
 */
export function diffSnapshot(currentFiles, snapshotFiles) {
  const changed = [];
  const deleted = [];

  // Find changed/new files
  for (const [relativePath, current] of Object.entries(currentFiles)) {
    const prev = snapshotFiles[relativePath];
    if (!prev) {
      changed.push(relativePath); // new file
    } else if (prev.sha256 !== current.sha256) {
      changed.push(relativePath); // content changed
    }
  }

  // Find deleted files
  for (const relativePath of Object.keys(snapshotFiles)) {
    if (!(relativePath in currentFiles)) {
      deleted.push(relativePath);
    }
  }

  return { changed, deleted };
}
