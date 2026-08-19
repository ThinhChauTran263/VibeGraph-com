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

/** Read a boolean env var. Accepts `1/true/yes/on` (case-insensitive) as true; falls back otherwise. */
export function envBool(key: string, fallback: boolean): boolean {
  const raw = ENV[key]
  if (raw === undefined || raw.trim() === '') return fallback
  const v = raw.trim().toLowerCase()
  if (v === '1' || v === 'true' || v === 'yes' || v === 'on') return true
  if (v === '0' || v === 'false' || v === 'no' || v === 'off') return false
  return fallback
}

// ── Graph rendering ──────────────────────────────────────────────────────────
/**
 * Max nodes handed to the renderer before Safe Mode caps the view; 0 disables the cap.
 * B-M10: the default is POSITIVE so a huge graph can no longer freeze the browser — set
 * 0 explicitly only to disable Safe Mode deliberately.
 */
export const GRAPH_SAFE_NODE_LIMIT = envInt('VITE_GRAPH_SAFE_NODE_LIMIT', 3000, { min: 0 })
/** Max neighbors merged when expanding a single node. */
export const EXPAND_MAX_NEIGHBORS = envInt('VITE_EXPAND_MAX_NEIGHBORS', 500, { min: 1 })
/** Default / min / max rendered node radius (Sigma units). */
export const NODE_SIZE_DEFAULT = envInt('VITE_NODE_SIZE_DEFAULT', 9, { min: 1 })
export const NODE_SIZE_MIN = envInt('VITE_NODE_SIZE_MIN', 6, { min: 1 })
export const NODE_SIZE_MAX = envInt('VITE_NODE_SIZE_MAX', 26, { min: 1 })

// Per-tier node radii (Sigma units). Sizes follow the containment hierarchy:
// the wider a node's structural scope (and the rarer it is), the larger it renders;
// the deeper / more numerous it is, the smaller — so dense member nodes don't drown
// out the architecture. Floats are allowed (e.g. 4.5) for fine-grained tuning.
//   Project > Package > File > Type decl > Member/Endpoint > Detail/metadata
export const NODE_SIZE_PROJECT = envFloat('VITE_NODE_SIZE_PROJECT', 18, { min: 1 })
export const NODE_SIZE_PACKAGE = envFloat('VITE_NODE_SIZE_PACKAGE', 14, { min: 1 })
export const NODE_SIZE_FILE = envFloat('VITE_NODE_SIZE_FILE', 11, { min: 1 })
/** Type declarations: Class / Interface / Enum / Record / DBModel. */
export const NODE_SIZE_TYPE = envFloat('VITE_NODE_SIZE_TYPE', 10, { min: 1 })
/** Behavioral members: Method / Constructor. */
export const NODE_SIZE_MEMBER = envFloat('VITE_NODE_SIZE_MEMBER', 8, { min: 1 })
/** HTTP entry points: Route / APIEndpoint (kept prominent despite shallow scope). */
export const NODE_SIZE_ENDPOINT = envFloat('VITE_NODE_SIZE_ENDPOINT', 8, { min: 1 })
/** Focus-mode opacities for the active vs dimmed nodes (0–1). */
export const FOCUS_OPACITY_ACTIVE = envFloat('VITE_FOCUS_OPACITY_ACTIVE', 1.0, { min: 0, max: 1 })
export const FOCUS_OPACITY_DIMMED = envFloat('VITE_FOCUS_OPACITY_DIMMED', 0.1, { min: 0, max: 1 })

// ── Import / analysis polling ────────────────────────────────────────────────
/** How often the import flows poll project status. */
export const IMPORT_POLL_INTERVAL_MS = envInt('VITE_IMPORT_POLL_INTERVAL_MS', 1000, { min: 100 })
/** Give up only after this long with NO progress (genuine backend stall). */
export const IMPORT_STALL_TIMEOUT_MS = envInt('VITE_IMPORT_STALL_TIMEOUT_MS', 300_000, {
  min: 1000,
})
/** Absolute safety ceiling so a pathological backend can't be polled forever. */
export const IMPORT_ABSOLUTE_TIMEOUT_MS = envInt('VITE_IMPORT_ABSOLUTE_TIMEOUT_MS', 3_600_000, {
  min: 1000,
})

// ── WebSocket (STOMP/SockJS) ─────────────────────────────────────────────────
/** Reconnect delay after a dropped socket. */
export const WS_RECONNECT_DELAY_MS = envInt('VITE_WS_RECONNECT_DELAY_MS', 5000, { min: 0 })
/** Inbound / outbound STOMP heartbeat intervals. */
export const WS_HEARTBEAT_INCOMING_MS = envInt('VITE_WS_HEARTBEAT_INCOMING_MS', 10000, { min: 0 })
export const WS_HEARTBEAT_OUTGOING_MS = envInt('VITE_WS_HEARTBEAT_OUTGOING_MS', 10000, { min: 0 })

// ── Sigma labels (zoom-responsive sizing) ────────────────────────────────────
/** Base node / edge label size at ratio=1 zoom. */
export const SIGMA_BASE_NODE_LABEL_SIZE = envFloat('VITE_SIGMA_BASE_NODE_LABEL_SIZE', 7, { min: 1 })
export const SIGMA_BASE_EDGE_LABEL_SIZE = envFloat('VITE_SIGMA_BASE_EDGE_LABEL_SIZE', 8, { min: 1 })
/** Node label zoom-scale floor / cap. */
export const SIGMA_MIN_LABEL_ZOOM_SCALE = envFloat('VITE_SIGMA_MIN_LABEL_ZOOM_SCALE', 0.5, {
  min: 0,
})
export const SIGMA_MAX_LABEL_ZOOM_SCALE = envFloat('VITE_SIGMA_MAX_LABEL_ZOOM_SCALE', 2.25, {
  min: 0,
})
/**
 * Edge label zoom-scale floor / cap. Edge type labels hold a FIXED size across the
 * normal zoom range (see SIGMA_EDGE_LABEL_GROW_ZOOM) and only enlarge once you zoom
 * deep past that threshold, then scale with 1/ratio up to this cap. Lower the cap to
 * stop growth sooner; raise it to allow larger labels under extreme magnification.
 */
export const SIGMA_MIN_EDGE_LABEL_ZOOM_SCALE = envFloat('VITE_SIGMA_MIN_EDGE_LABEL_ZOOM_SCALE', 1, {
  min: 0,
})
export const SIGMA_MAX_EDGE_LABEL_ZOOM_SCALE = envFloat('VITE_SIGMA_MAX_EDGE_LABEL_ZOOM_SCALE', 5, {
  min: 0,
})
/**
 * Min on-screen node size (px) before Sigma draws its label. Lower = labels appear
 * sooner / at a more zoomed-out view. Kept modest so names show without deep zoom
 * while still hiding under a heavy zoom-out where everything would overlap.
 */
export const SIGMA_LABEL_RENDERED_SIZE_THRESHOLD = envInt(
  'VITE_SIGMA_LABEL_RENDERED_SIZE_THRESHOLD',
  8,
  { min: 0 },
)

/**
 * Zoom-in factor (relative to the initial fit view = 1×) past which labels START
 * growing. Below it, labels keep a CONSTANT on-screen size while panning/zooming so
 * casual zoom doesn't jitter text; only when the user zooms in deeper than this
 * threshold do labels scale up with the zoom for readability. Zooming out below the
 * fit view shrinks labels toward the min scale and then hides them. e.g. 2.5 =
 * labels stay fixed until you zoom to 2.5× the initial view, then enlarge.
 */
export const SIGMA_LABEL_GROW_ZOOM = envFloat('VITE_SIGMA_LABEL_GROW_ZOOM', 1.5, { min: 1 })

/**
 * Zoom-in factor past which EDGE type labels start growing. Kept high so edge labels
 * appear and stay a FIXED size across the normal zoom range, and only begin scaling
 * up once you zoom deep past this factor (e.g. 10 = labels hold their size until 10×
 * the fit view, then enlarge with further zoom). Zooming back out below it returns
 * them to the fixed size. This is separate from the node grow factor so node labels
 * can grow early for readability while edge labels stay calm until deep zoom.
 */
export const SIGMA_EDGE_LABEL_GROW_ZOOM = envFloat('VITE_SIGMA_EDGE_LABEL_GROW_ZOOM', 12, {
  min: 1,
})

/**
 * Edge size attribute fed to Sigma's edge programs. Sigma shares the item-size
 * zoom curve between nodes and edges, but the minimum edge thickness remains the
 * effective line width while the scaled edge size stays below that floor.
 */
export const SIGMA_EDGE_SIZE = envFloat('VITE_SIGMA_EDGE_SIZE', 0.02, { min: 0.005 })

/**
 * Minimum rendered edge thickness (screen px). Sigma floors every edge at this, and
 * because SIGMA_EDGE_SIZE is tiny the floor dominates → edges render at a CONSTANT
 * thin width no matter how far you zoom in (they never balloon with zoom like the
 * default size/√ratio scaling would). This is the effective edge line thickness.
 */
export const SIGMA_MIN_EDGE_THICKNESS = envFloat('VITE_SIGMA_MIN_EDGE_THICKNESS', 1.8, { min: 0.5 })

/**
 * Camera-ratio thresholds that stage label reveal (Sigma ratio: LOWER = zoomed IN).
 *   ratio > MINIMAL           -> 'minimal' (only a forced/selected label)
 *   EDGE < ratio <= MINIMAL   -> 'nodes'   (node labels reveal by size threshold)
 *   ratio <= EDGE             -> 'edges'   (edge type labels appear)
 * EDGE is kept fairly deep so edge type labels only force ON once the view is
 * magnified enough that few edges remain on screen — that, plus viewport culling in
 * the edge-label renderer, keeps zooming smooth while still drawing every frame.
 */
export const SIGMA_MINIMAL_LABEL_RATIO = envFloat('VITE_SIGMA_MINIMAL_LABEL_RATIO', 1.05, {
  min: 0,
})
export const SIGMA_EDGE_LABEL_RATIO = envFloat('VITE_SIGMA_EDGE_LABEL_RATIO', 0.45, { min: 0 })

/**
 * Max edge type labels DRAWN per frame. Off-screen labels are culled for free and
 * don't count; this caps only the visible ones so a zoom level with many edges on
 * screen can't stack hundreds of rotated text draws into a single frame (the last
 * remaining source of zoom jank). Higher = more labels at once (denser, heavier);
 * lower = smoother but fewer labels shown simultaneously.
 */
export const SIGMA_MAX_EDGE_LABELS_PER_FRAME = envInt('VITE_SIGMA_MAX_EDGE_LABELS_PER_FRAME', 48, {
  min: 1,
})

// ── Layout engine (update/graph/qwen/02-ARCHITECTURE.md) ────────────────────
// grapuco recipe: worker macro (d3|ngraph) + d3 forceCollide in-sim, 300
// ticks, pinned; graph-unit sizes via 'positions'.
/** Macro slot for the d3 engine: pure 'd3' (default — matches grapuco spread,
 *  see update/graph/qwen/04-RESULTS.md) or 'ngraph' hybrid (A/B fallback). */
export const LAYOUT_MACRO: 'd3' | 'ngraph' =
  (ENV['VITE_LAYOUT_MACRO'] ?? 'd3').trim().toLowerCase() === 'ngraph' ? 'ngraph' : 'd3'
/**
 * Node draw radius in graph units = max(DRAW_SCALE·val, DRAW_MIN); collide
 * radius = draw + COLLIDE_PAD (grapuco: draw 24–60, pad 100). Raise DRAW_SCALE
 * for bigger visible nodes; keep COLLIDE_PAD ≥ ~2× draw for the 0-overlap
 * guarantee (update/graph/qwen/01-EVIDENCE.md §3).
 */
export const LAYOUT_DRAW_SCALE = envFloat('VITE_LAYOUT_DRAW_SCALE', 3, { min: 0.5 })
export const LAYOUT_DRAW_MIN = envFloat('VITE_LAYOUT_DRAW_MIN', 10, { min: 1 })
export const LAYOUT_COLLIDE_PAD = envFloat('VITE_LAYOUT_COLLIDE_PAD', 100, { min: 0 })

/** Zoom-to-fit camera animation duration. */
export const ZOOM_FIT_DURATION_MS = envInt('VITE_ZOOM_FIT_DURATION_MS', 300, { min: 0 })

// ── Node detail panel ────────────────────────────────────────────────────────
/** Max properties / connections (per direction) shown in the node detail panel. */
export const NODE_DETAIL_MAX_PROPERTIES = envInt('VITE_NODE_DETAIL_MAX_PROPERTIES', 12, { min: 1 })
export const NODE_DETAIL_MAX_CONNECTIONS = envInt('VITE_NODE_DETAIL_MAX_CONNECTIONS', 50, {
  min: 1,
})

// ── Search ───────────────────────────────────────────────────────────────────
/** Max suggestions shown in the graph search bar. */
export const SEARCH_SUGGESTIONS_LIMIT = envInt('VITE_SEARCH_SUGGESTIONS_LIMIT', 8, { min: 1 })

// ── UML use-case diagram ─────────────────────────────────────────────────────
/** Label truncation length (chars) before ellipsis for use cases / actors. */
export const UML_USECASE_MAX_CHARS = envInt('VITE_UML_USECASE_MAX_CHARS', 28, { min: 1 })
export const UML_ACTOR_MAX_CHARS = envInt('VITE_UML_ACTOR_MAX_CHARS', 18, { min: 1 })
