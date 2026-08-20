/**
 * File scanner for vibegraph-cli.
 * Recursively scans a root directory, applies ignore rules,
 * detects binary files, enforces size limits, and normalizes paths to POSIX.
 */

import { readdir, stat, lstat, readFile } from "node:fs/promises";
import path from "node:path";
import { shouldIgnore, isBinaryFile, getMaxFileSize, getMaxFiles } from "./ignore.js";
import { computeHash } from "./snapshot.js";

/**
 * @typedef {Object} ScannedFile
 * @property {string} relativePath - POSIX relative path
 * @property {string} absolutePath - OS-native absolute path
 * @property {number} size - File size in bytes
 * @property {number} mtimeMs - Last modified time
 * @property {string} sha256 - SHA-256 hash
 * @property {Buffer} content - File content
 */

/**
 * @typedef {Object} SkippedFile
 * @property {string} relativePath - POSIX relative path
 * @property {string} reason - Why it was skipped
 */

/**
 * @typedef {Object} ScanResult
 * @property {ScannedFile[]} files - Files to include
 * @property {SkippedFile[]} skipped - Files that were skipped
 * @property {boolean} truncated - Whether max files limit was hit
 */

/**
 * Scan a directory recursively for eligible files.
 * @param {string} rootDir - Absolute path to project root
 * @param {string[]} ignoreRules - Loaded ignore patterns
 * @returns {Promise<ScanResult>}
 */
export async function scanDirectory(rootDir, ignoreRules) {
  const maxFileSize = getMaxFileSize();
  const maxFiles = getMaxFiles();
  const files = [];
  const skipped = [];
  let truncated = false;

  await walk(rootDir, rootDir, ignoreRules, files, skipped, maxFileSize, maxFiles, () => {
    truncated = true;
  });

  return { files, skipped, truncated };
}

/**
 * Recursive directory walker.
 */
async function walk(currentDir, rootDir, ignoreRules, files, skipped, maxFileSize, maxFiles, onTruncate) {
  if (files.length >= maxFiles) {
    onTruncate();
    return;
  }

  let entries;
  try {
    entries = await readdir(currentDir, { withFileTypes: true });
  } catch (error) {
    // Permission denied or other read error — skip directory
    return;
  }

  for (const entry of entries) {
    if (files.length >= maxFiles) {
      onTruncate();
      return;
    }

    const absolutePath = path.join(currentDir, entry.name);
    const relativePath = toPosixRelative(rootDir, absolutePath);

    // Check ignore rules on relative path
    const ignoreCheck = shouldIgnore(relativePath, ignoreRules);
    if (ignoreCheck.ignored) {
      // Only log file-level skips, not entire directories (too noisy)
      if (!entry.isDirectory()) {
        skipped.push({ relativePath, reason: ignoreCheck.reason });
      }
      continue;
    }

    // Skip symlinks
    try {
      const lstats = await lstat(absolutePath);
      if (lstats.isSymbolicLink()) {
        skipped.push({ relativePath, reason: "symlink" });
        continue;
      }
    } catch {
      continue;
    }

    if (entry.isDirectory()) {
      // Check if the directory itself is ignored (e.g., "node_modules/**")
      const dirPath = relativePath + "/";
      const dirIgnore = shouldIgnore(dirPath, ignoreRules);
      if (dirIgnore.ignored) continue;

      await walk(absolutePath, rootDir, ignoreRules, files, skipped, maxFileSize, maxFiles, onTruncate);
    } else if (entry.isFile()) {
      // Java sources only — the knowledge graph stores nothing else, matching the
      // archive/GitHub importers. The server enforces the same rule.
      if (!entry.name.endsWith(".java")) {
        skipped.push({ relativePath, reason: "not Java source" });
        continue;
      }

      // Check file size
      let fileStat;
      try {
        fileStat = await stat(absolutePath);
      } catch {
        skipped.push({ relativePath, reason: "unreadable" });
        continue;
      }

      if (fileStat.size > maxFileSize) {
        skipped.push({ relativePath, reason: `exceeds ${Math.round(maxFileSize / 1024 / 1024)}MB limit` });
        continue;
      }

      if (fileStat.size === 0) {
        skipped.push({ relativePath, reason: "empty file" });
        continue;
      }

      // Check if binary
      const binary = await isBinaryFile(absolutePath);
      if (binary) {
        skipped.push({ relativePath, reason: "binary file" });
        continue;
      }

      // Read content and compute hash
      let content;
      try {
        content = await readFile(absolutePath);
      } catch {
        skipped.push({ relativePath, reason: "read error" });
        continue;
      }

      const sha256 = computeHash(content);

      files.push({
        relativePath,
        absolutePath,
        size: fileStat.size,
        mtimeMs: fileStat.mtimeMs,
        sha256,
        content,
      });
    }
  }
}

/**
 * Convert absolute path to POSIX relative path.
 * @param {string} rootDir - Absolute root path
 * @param {string} absolutePath - Absolute file path
 * @returns {string} Relative POSIX path (forward slashes)
 */
export function toPosixRelative(rootDir, absolutePath) {
  const relative = path.relative(rootDir, absolutePath);
  // Normalize to forward slashes (handles Windows backslashes)
  return relative.split(path.sep).join("/");
}

/**
 * Build file state map from scanned files (for snapshot storage).
 * @param {ScannedFile[]} files
 * @returns {Record<string, { size: number, mtimeMs: number, sha256: string }>}
 */
export function buildFileStateMap(files) {
  const map = {};
  for (const file of files) {
    map[file.relativePath] = {
      size: file.size,
      mtimeMs: file.mtimeMs,
      sha256: file.sha256,
    };
  }
  return map;
}
