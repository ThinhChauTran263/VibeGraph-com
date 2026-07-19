/**
 * Push command for vibegraph-cli.
 * Sends local file changes to backend as a patch.
 */

import path from "node:path";
import { loadIgnoreRules } from "./ignore.js";
import { loadSnapshot, saveSnapshot, diffSnapshot } from "./snapshot.js";
import { scanDirectory, buildFileStateMap } from "./scanner.js";
import { createPatchRequest, resolveSnapshotId } from "./project-target.js";

/**
 * Execute the push command.
 * @param {string} projectId
 * @param {object} options - { root, dryRun }
 * @param {function} apiRequest - The CLI's apiRequest function
 */
export async function executePush(projectId, options, apiRequest) {
  const rootDir = path.resolve(options.root || ".");
  const snapshotId = resolveSnapshotId(projectId, options.snapshotId);

  // Load ignore rules
  const ignoreRules = await loadIgnoreRules(rootDir);

  // Scan current files
  const scan = await scanDirectory(rootDir, ignoreRules);

  if (scan.truncated) {
    throw new Error(truncatedScanMessage());
  }

  // Build current state map
  const currentState = buildFileStateMap(scan.files);

  // Load previous snapshot to detect deletions
  const previousSnapshot = await loadSnapshot(snapshotId);

  // Diff to find changed and deleted
  const { changed, deleted } = diffSnapshot(currentState, previousSnapshot);

  // Prepare payload
  const filesToSend = scan.files.filter((f) => changed.includes(f.relativePath));
  const payload = {
    files: filesToSend.map((f) => ({
      path: f.relativePath,
      contentBase64: f.content.toString("base64"),
      encoding: "base64",
    })),
    deletions: deleted.map((p) => ({ path: p })),
    dryRun: Boolean(options.dryRun),
  };

  // Summary
  const summary = {
    changed: filesToSend.length,
    deleted: deleted.length,
    skipped: scan.skipped.length,
  };

  if (options.dryRun) {
    // Send to backend with dryRun=true
    try {
      const request = createPatchRequest(projectId, payload);
      const result = await apiRequest(request.endpoint, request.options);
      console.log(`Dry run: ${summary.changed} changed, ${summary.deleted} deleted, ${summary.skipped} skipped`);
      if (result?.rejected?.length) {
        console.log(`Rejected by server: ${result.rejected.length} files`);
        for (const r of result.rejected) {
          console.log(`  ${r.path}: ${r.reason || "unknown"}`);
        }
      }
    } catch (error) {
      const msg = error.message || String(error);
      // Only fall back to local-only for network-level failures (server unreachable).
      // HTTP errors (404, 400, etc.) are real server responses and must be surfaced.
      if (isNetworkError(msg)) {
        console.log(`Dry run (local-only, backend unavailable):`);
        console.log(`  ${summary.changed} changed, ${summary.deleted} deleted, ${summary.skipped} skipped`);
        printSkippedSummary(scan.skipped);
      } else {
        throw error;
      }
    }
    return summary;
  }

  // Real push
  if (filesToSend.length === 0 && deleted.length === 0) {
    console.log("No changes to push.");
    return summary;
  }

  const request = createPatchRequest(projectId, payload);
  const result = await apiRequest(request.endpoint, request.options);

  // Update snapshot after successful push
  await saveSnapshot(snapshotId, currentState);

  console.log(`Pushed patch: ${summary.changed} changed, ${summary.deleted} deleted`);
  if (result?.rejected?.length) {
    console.log(`Rejected by server: ${result.rejected.length} files`);
    for (const r of result.rejected) {
      console.log(`  ${r.path}: ${r.reason || "unknown"}`);
    }
  }

  return summary;
}

function printSkippedSummary(skipped) {
  if (skipped.length === 0) return;

  // Group by reason
  const groups = {};
  for (const s of skipped) {
    groups[s.reason] = (groups[s.reason] || 0) + 1;
  }
  for (const [reason, count] of Object.entries(groups)) {
    console.log(`  Skipped ${count} file(s): ${reason}`);
  }
}

function truncatedScanMessage() {
  return "Scan stopped after VIBEGRAPH_MAX_FILES. No patch was sent because a partial scan could delete files incorrectly. Increase VIBEGRAPH_MAX_FILES or narrow --root.";
}

/**
 * Check if an error message indicates a network-level failure (server unreachable).
 * HTTP responses (404, 400, 500, etc.) are NOT network errors — they mean the server
 * responded and the error is meaningful.
 */
function isNetworkError(msg) {
  return (
    msg.includes("fetch failed") ||
    msg.includes("ECONNREFUSED") ||
    msg.includes("ENOTFOUND") ||
    msg.includes("ECONNRESET") ||
    msg.includes("ETIMEDOUT") ||
    msg.includes("EAI_AGAIN")
  );
}
