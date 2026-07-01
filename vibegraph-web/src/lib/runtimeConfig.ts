/**
 * Runtime-tunable configuration — the single source of truth for every adjustable
 * behavioral / performance knob in the frontend.
 *
 * Nothing tunable should be hardcoded deep in the codebase: each value below is
 * sourced from a `VITE_*` env var (root `.env`, see envDir in vite.config.ts) and
 * falls back to a safe default when the var is absent or malformed. Existing modules
 * (graphCap, neighborsAdapter, archiveUpload, the import composables, …) re-export
 * these so call sites stay unchanged.
 *
 * IMPORTANT: `VITE_*` values are baked in at BUILD time. After editing `.env`,
 * restart `vite dev` (or rebuild the frontend image) for changes to take effect.
 */

type EnvBag = Record<string, string | undefined>

const ENV = import.meta.env as unknown as EnvBag

interface NumberBounds {
  min?: number
  max?: number
}

function clamp(value: number, { min, max }: NumberBounds): number {
  if (min !== undefined && value < min) return min
  if (max !== undefined && value > max) return max
  return value
}

/** Read an integer env var. Falls back when missing/blank/non-finite; clamps to bounds. */
export function envInt(key: string, fallback: number, bounds: NumberBounds = {}): number {
  const raw = ENV[key]
  if (raw === undefined || raw.trim() === '') return clamp(fallback, bounds)
  const parsed = Number(raw)
  if (!Number.isFinite(parsed)) return clamp(fallback, bounds)
  return clamp(Math.trunc(parsed), bounds)
}

/** Read a floating-point env var. Falls back when missing/blank/non-finite; clamps to bounds. */
export function envFloat(key: string, fallback: number, bounds: NumberBounds = {}): number {
  const raw = ENV[key]
  if (raw === undefined || raw.trim() === '') return clamp(fallback, bounds)
  const parsed = Number(raw)
  if (!Number.isFinite(parsed)) return clamp(fallback, bounds)
  return clamp(parsed, bounds)
}

// ── Graph rendering ──────────────────────────────────────────────────────────
/** Max nodes handed to the renderer before Safe Mode caps the view. */
export const GRAPH_SAFE_NODE_LIMIT = envInt('VITE_GRAPH_SAFE_NODE_LIMIT', 1500, { min: 0 })
/** Max neighbors merged when expanding a single node. */
export const EXPAND_MAX_NEIGHBORS = envInt('VITE_EXPAND_MAX_NEIGHBORS', 500, { min: 1 })
/** Default / min / max rendered node radius (Sigma units). */
export const NODE_SIZE_DEFAULT = envInt('VITE_NODE_SIZE_DEFAULT', 5, { min: 1 })
export const NODE_SIZE_MIN = envInt('VITE_NODE_SIZE_MIN', 3, { min: 1 })
export const NODE_SIZE_MAX = envInt('VITE_NODE_SIZE_MAX', 20, { min: 1 })

// Per-tier node radii (Sigma units). Sizes follow the containment hierarchy:
// the wider a node's structural scope (and the rarer it is), the larger it renders;
// the deeper / more numerous it is, the smaller — so dense member nodes don't drown
// out the architecture. Floats are allowed (e.g. 4.5) for fine-grained tuning.
//   Project > Package > File > Type decl > Member/Endpoint > Detail/metadata
export const NODE_SIZE_PROJECT = envFloat('VITE_NODE_SIZE_PROJECT', 10, { min: 1 })
export const NODE_SIZE_PACKAGE = envFloat('VITE_NODE_SIZE_PACKAGE', 7, { min: 1 })
export const NODE_SIZE_FILE = envFloat('VITE_NODE_SIZE_FILE', 6, { min: 1 })
/** Type declarations: Class / Interface / Enum / Record / DBModel. */
export const NODE_SIZE_TYPE = envFloat('VITE_NODE_SIZE_TYPE', 5, { min: 1 })
/** Behavioral members: Method / Constructor. */
export const NODE_SIZE_MEMBER = envFloat('VITE_NODE_SIZE_MEMBER', 4, { min: 1 })
/** HTTP entry points: Route / APIEndpoint (kept prominent despite shallow scope). */
export const NODE_SIZE_ENDPOINT = envFloat('VITE_NODE_SIZE_ENDPOINT', 4, { min: 1 })
/** Focus-mode opacities for the active vs dimmed nodes (0–1). */
export const FOCUS_OPACITY_ACTIVE = envFloat('VITE_FOCUS_OPACITY_ACTIVE', 1.0, { min: 0, max: 1 })
export const FOCUS_OPACITY_DIMMED = envFloat('VITE_FOCUS_OPACITY_DIMMED', 0.1, { min: 0, max: 1 })

// ── Import / analysis polling ────────────────────────────────────────────────
/** How often the import flows poll project status. */
export const IMPORT_POLL_INTERVAL_MS = envInt('VITE_IMPORT_POLL_INTERVAL_MS', 1000, { min: 100 })
/** Give up only after this long with NO progress (genuine backend stall). */
export const IMPORT_STALL_TIMEOUT_MS = envInt('VITE_IMPORT_STALL_TIMEOUT_MS', 300_000, { min: 1000 })
/** Absolute safety ceiling so a pathological backend can't be polled forever. */
export const IMPORT_ABSOLUTE_TIMEOUT_MS = envInt('VITE_IMPORT_ABSOLUTE_TIMEOUT_MS', 3_600_000, { min: 1000 })

// ── Project list ─────────────────────────────────────────────────────────────
/** Background refresh cadence for the "Your projects" list on the home page. */
export const PROJECTS_AUTO_REFRESH_INTERVAL_MS = envInt('VITE_PROJECTS_AUTO_REFRESH_INTERVAL_MS', 5000, { min: 1000 })

// ── Archive upload ───────────────────────────────────────────────────────────
/** Client-side max archive size. Backend remains the authority and must re-validate. */
export const ARCHIVE_MAX_SIZE_BYTES = envInt('VITE_ARCHIVE_MAX_SIZE_MB', 100, { min: 1 }) * 1024 * 1024

// ── WebSocket (STOMP/SockJS) ─────────────────────────────────────────────────
/** Reconnect delay after a dropped socket. */
export const WS_RECONNECT_DELAY_MS = envInt('VITE_WS_RECONNECT_DELAY_MS', 5000, { min: 0 })
/** Inbound / outbound STOMP heartbeat intervals. */
export const WS_HEARTBEAT_INCOMING_MS = envInt('VITE_WS_HEARTBEAT_INCOMING_MS', 10000, { min: 0 })
export const WS_HEARTBEAT_OUTGOING_MS = envInt('VITE_WS_HEARTBEAT_OUTGOING_MS', 10000, { min: 0 })

// ── Sigma labels (zoom-responsive sizing) ────────────────────────────────────
/** Base node / edge label size at ratio=1 zoom. */
export const SIGMA_BASE_NODE_LABEL_SIZE = envFloat('VITE_SIGMA_BASE_NODE_LABEL_SIZE', 8, { min: 1 })
export const SIGMA_BASE_EDGE_LABEL_SIZE = envFloat('VITE_SIGMA_BASE_EDGE_LABEL_SIZE', 3, { min: 1 })
/** Node label zoom-scale floor / cap. */
export const SIGMA_MIN_LABEL_ZOOM_SCALE = envFloat('VITE_SIGMA_MIN_LABEL_ZOOM_SCALE', 0.5, { min: 0 })
export const SIGMA_MAX_LABEL_ZOOM_SCALE = envFloat('VITE_SIGMA_MAX_LABEL_ZOOM_SCALE', 2.25, { min: 0 })
/** Edge label zoom-scale floor / cap. */
export const SIGMA_MIN_EDGE_LABEL_ZOOM_SCALE = envFloat('VITE_SIGMA_MIN_EDGE_LABEL_ZOOM_SCALE', 1, { min: 0 })
export const SIGMA_MAX_EDGE_LABEL_ZOOM_SCALE = envFloat('VITE_SIGMA_MAX_EDGE_LABEL_ZOOM_SCALE', 4, { min: 0 })
/** Min on-screen node size before Sigma draws its label (progressive reveal). */
export const SIGMA_LABEL_RENDERED_SIZE_THRESHOLD = envInt('VITE_SIGMA_LABEL_RENDERED_SIZE_THRESHOLD', 15, { min: 0 })

// ── ForceAtlas2 layout ───────────────────────────────────────────────────────
export const FA2_GRAVITY = envFloat('VITE_FA2_GRAVITY', 0.2, { min: 0 })
export const FA2_SCALING_RATIO = envFloat('VITE_FA2_SCALING_RATIO', 100, { min: 0 })
/** Enable Barnes-Hut optimization once node count exceeds this. */
export const FA2_BARNES_HUT_MIN_NODES = envInt('VITE_FA2_BARNES_HUT_MIN_NODES', 500, { min: 0 })
export const FA2_SLOW_DOWN = envFloat('VITE_FA2_SLOW_DOWN', 5, { min: 0 })
/** Synchronous ForceAtlas2 iterations run once before first paint (no live animation). */
export const FA2_ITERATIONS = envInt('VITE_FA2_ITERATIONS', 400, { min: 1 })
/** Auto-stop the layout worker after this long. */
export const LAYOUT_AUTO_STOP_MS = envInt('VITE_LAYOUT_AUTO_STOP_MS', 5000, { min: 0 })
/** Zoom-to-fit camera animation duration. */
export const ZOOM_FIT_DURATION_MS = envInt('VITE_ZOOM_FIT_DURATION_MS', 300, { min: 0 })

// ── Node detail panel ────────────────────────────────────────────────────────
/** Max properties / connections (per direction) shown in the node detail panel. */
export const NODE_DETAIL_MAX_PROPERTIES = envInt('VITE_NODE_DETAIL_MAX_PROPERTIES', 12, { min: 1 })
export const NODE_DETAIL_MAX_CONNECTIONS = envInt('VITE_NODE_DETAIL_MAX_CONNECTIONS', 50, { min: 1 })

// ── Search ───────────────────────────────────────────────────────────────────
/** Max suggestions shown in the graph search bar. */
export const SEARCH_SUGGESTIONS_LIMIT = envInt('VITE_SEARCH_SUGGESTIONS_LIMIT', 8, { min: 1 })

// ── UML use-case diagram ─────────────────────────────────────────────────────
/** Label truncation length (chars) before ellipsis for use cases / actors. */
export const UML_USECASE_MAX_CHARS = envInt('VITE_UML_USECASE_MAX_CHARS', 28, { min: 1 })
export const UML_ACTOR_MAX_CHARS = envInt('VITE_UML_ACTOR_MAX_CHARS', 18, { min: 1 })
