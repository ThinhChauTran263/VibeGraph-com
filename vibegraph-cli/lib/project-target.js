/**
 * Builds the local-patch request shared by push and watch.
 * A missing project ID means the backend resolves the project from X-API-Key.
 */
export function createPatchRequest(projectId, payload) {
  const endpoint = projectId
    ? `/api/projects/${encodeURIComponent(projectId)}/patch`
    : "/api/projects/current/patch";
  return {
    endpoint,
    options: {
      method: "POST",
      auth: "api-key-first",
      body: payload,
    },
  };
}

export function resolveSnapshotId(projectId, snapshotId) {
  if (projectId) return projectId;
  if (snapshotId) return snapshotId;
  throw new Error("Project-bound push requires an API-key snapshot identity.");
}
