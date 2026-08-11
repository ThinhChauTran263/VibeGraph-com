// =============================================================================
// VibeGraph - Neo4j Schema V2 (Shared :Symbol label)
// =============================================================================
// Every domain node carries `projectId`, but V1 constraints/indexes are all
// label-scoped (:Class, :Method, ...) so the hot label-less queries
// (getFullGraph, impact traversal, node upsert MERGE) could not use ANY index
// and degraded to AllNodesScan across every tenant.
//
// V2 introduces one shared label `:Symbol` on every node that carries a
// `projectId` (including :Project and :External placeholders, which also carry
// it), plus the two indexes the hot paths need:
//   1. (projectId)            — full-graph load per project
//   2. (projectId, fullName)  — node identity lookups (upsert MERGE, impact
//                                target, node detail)
//
// The final statement backfills the label onto pre-V2 nodes. It is idempotent
// (filters on NOT n:Symbol) and runs once per startup; new nodes get the label
// at write time in Neo4jGraphRepository.
//
// Safe to re-run: index statements use IF NOT EXISTS, backfill is a no-op when
// nothing is unlabeled.
// =============================================================================

CREATE INDEX symbol_project IF NOT EXISTS
FOR (s:Symbol) ON (s.projectId);

CREATE INDEX symbol_project_fullname IF NOT EXISTS
FOR (s:Symbol) ON (s.projectId, s.fullName);

MATCH (n) WHERE n.projectId IS NOT NULL AND NOT n:Symbol SET n:Symbol;

// =============================================================================
// End of V2__symbol_label.cypher
// =============================================================================
