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
 * Edge size attribute fed to Sigma's edge programs (NOT the rendered thickness by
 * itself). With ZOOM_SIZE_POWER = 1.0 an edge starts ballooning once the zoom
 * factor r^p drops below SIGMA_EDGE_SIZE / SIGMA_MIN_EDGE_THICKNESS. The floor
 * holds while  SIGMA_EDGE_SIZE < SIGMA_MIN_EDGE_THICKNESS · M^(−p)
 *              = 2.8 / 100 = 0.028,
 * so 0.02 keeps edges hairline-thin up to 100× zoom
 * (update/graph/02-SIGMA-INTERNALS.md §5). The { min } floor MUST stay below the
 * default, otherwise the default is silently clamped back up and the fix does not
 * apply.
 */
export const SIGMA_EDGE_SIZE = envFloat('VITE_SIGMA_EDGE_SIZE', 0.02, { min: 0.005 })

/**
 * Minimum rendered edge thickness (screen px). Sigma floors every edge at this, and
 * because SIGMA_EDGE_SIZE is tiny the floor dominates → edges render at a CONSTANT
 * thin width no matter how far you zoom in (they never balloon with zoom like the
 * default size/√ratio scaling would). This is the effective edge line thickness.
 */
export const SIGMA_MIN_EDGE_THICKNESS = envFloat('VITE_SIGMA_MIN_EDGE_THICKNESS', 2.8, { min: 0.5 })

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

// ── ForceAtlas2 layout ───────────────────────────────────────────────────────
export const FA2_GRAVITY = envFloat('VITE_FA2_GRAVITY', 0.001, { min: 0 })
export const FA2_SCALING_RATIO = envFloat('VITE_FA2_SCALING_RATIO', 20000, { min: 0 })
/** Enable Barnes-Hut optimization once node count exceeds this. */
export const FA2_BARNES_HUT_MIN_NODES = envInt('VITE_FA2_BARNES_HUT_MIN_NODES', 500, { min: 0 })
export const FA2_SLOW_DOWN = envFloat('VITE_FA2_SLOW_DOWN', 5, { min: 0 })
// NOTE (T11): FA2_ITERATIONS was deleted — it was referenced nowhere. FA2 runs
// in an async web worker and is stopped by the LAYOUT_AUTO_STOP_MS timer below,
// not by a synchronous pre-paint iteration count (the old comment claimed
// otherwise and was provably false — update/graph/01-EVIDENCE-LOG.md §4).

// ── ForceAtlas2 cluster separation (anti-hairball) ───────────────────────────
// Standard ForceAtlas2 (NOT LinLog) with strong
// repulsion + dissuade-hubs: connected nodes stay close (short, local edges) while
// unrelated nodes push far apart, so the graph spreads into organic branches
// instead of one dense hairball. LinLog is intentionally OFF — it lengthens edges
// and pulls the body toward the center (measured edgeToRadius 0.48 vs 0.29).
//
// Do NOT enable adjustSizes + Noverlap together — those pack the graph into a
// uniform square and destroy the branch structure. They default OFF.
export const FA2_LINLOG_MODE = envBool('VITE_FA2_LINLOG_MODE', false)
export const FA2_OUTBOUND_ATTRACTION = envBool('VITE_FA2_OUTBOUND_ATTRACTION', true)
/** Account for node radius while laying out. OFF: it packs nodes into a solid square. */
export const FA2_ADJUST_SIZES = envBool('VITE_FA2_ADJUST_SIZES', false)
/**
 * Strong gravity pulls a node toward the center by a force PROPORTIONAL to its
 * distance. It keeps outliers close but COMPRESSES the whole body into a dense
 * disc (crammed look), so it defaults OFF. Framing is handled instead by clamping
 * outliers (below), which keeps the body airy while still bounding the view.
 */
export const FA2_STRONG_GRAVITY_MODE = envBool('VITE_FA2_STRONG_GRAVITY_MODE', false)

// NOTE (T11): FA2_OUTLIER_CLAMP_PERCENTILE was deleted — it was referenced
// nowhere. Its doc comment described an outlier-clamping post-pass that does not
// exist in the code at all (update/graph/01-EVIDENCE-LOG.md §4–§5). Framing is
// handled by the maxCameraRatio clamp in useSigma instead.

// Adaptive settings: small graphs already spread well with the base values, so we
// only switch to the heavier, more separated large-graph profile past this size.
export const FA2_LARGE_GRAPH_THRESHOLD = envInt('VITE_FA2_LARGE_GRAPH_THRESHOLD', 300, { min: 1 })
/**
 * Large-graph overrides. Strong repulsion (high scalingRatio) spreads the graph
 * wide; gravity ~1 combined with strongGravityMode keeps disconnected nodes near
 * the body so the camera frames the main cluster instead of a distant outlier.
 */
export const FA2_GRAVITY_LARGE = envFloat('VITE_FA2_GRAVITY_LARGE', 0.001, { min: 0 })
export const FA2_SCALING_RATIO_LARGE = envFloat('VITE_FA2_SCALING_RATIO_LARGE', 8000, { min: 0 })

/**
 * Rescale the settled layout so its bounding box spans this many layout units.
 * With `itemSizesReference: 'screen'` (see useSigma), node `size` attributes are
 * screen pixels, but node POSITIONS live in this layout-coordinate space — so a
 * fixed span makes the graph's extent (and the px↔graph-unit conversion used by
 * the screen-space de-overlap pass) predictable on EVERY project, regardless of
 * how large the raw force-layout coordinates came out. Set 0 to disable.
 */
export const LAYOUT_NORMALIZE_SPAN = envInt('VITE_LAYOUT_NORMALIZE_SPAN', 9000, { min: 0 })
// Shape-preserving post-layout spread. ForceAtlas2 decides the organic silhouette;
// this pass scales each connected component around its own centroid and shifts
// smaller islands away from the main component without rerunning physics.
export const LAYOUT_BRANCH_ENABLED = envBool('VITE_LAYOUT_BRANCH_ENABLED', true)
export const LAYOUT_BRANCH_MIN_NODES = envInt('VITE_LAYOUT_BRANCH_MIN_NODES', 80, { min: 1 })
export const LAYOUT_BRANCH_STRENGTH = envFloat('VITE_LAYOUT_BRANCH_STRENGTH', 1.9, {
  min: 0,
  max: 2,
})
export const LAYOUT_BRANCH_LEVEL_GAP = envFloat('VITE_LAYOUT_BRANCH_LEVEL_GAP', 2200, {
  min: 0,
})
export const LAYOUT_BRANCH_JITTER = envFloat('VITE_LAYOUT_BRANCH_JITTER', 260, { min: 0 })
export const LAYOUT_BRANCH_COMPONENT_GAP = envFloat('VITE_LAYOUT_BRANCH_COMPONENT_GAP', 4200, {
  min: 0,
})

// ── Overlap removal (post-pass) ──────────────────────────────────────────────
// T8(b): the graphology-noverlap worker was removed entirely. It computed
// collisions in graph units from raw screen-px `size` attributes, guaranteeing
// ~25% under-separation (update/graph/03-ROOT-CAUSE.md Layer 1), and blocked the
// pipeline behind NOVERLAP_AUTO_STOP_MS = 22 s. `settleScreenOverlaps` below is
// the single de-overlap pass: it converts px radii to graph units via the live
// bounding-box factor (unitsPerPixel) and, with ZOOM_SIZE_POWER = 1.0, its result
// is correct at every zoom level (update/graph/02-SIGMA-INTERNALS.md §4).
// Final visual cleanup for Sigma's screen-sized nodes. This bounded pass
// converts px radii to graph units and only pushes still-touching visible nodes
// apart after the ForceAtlas2 worker stops.
export const LAYOUT_SCREEN_OVERLAP_ENABLED = envBool('VITE_LAYOUT_SCREEN_OVERLAP_ENABLED', true)
export const LAYOUT_SCREEN_OVERLAP_GAP_PX = envFloat('VITE_LAYOUT_SCREEN_OVERLAP_GAP_PX', 3, {
  min: 0,
})
export const LAYOUT_SCREEN_OVERLAP_ITERATIONS = envInt(
  'VITE_LAYOUT_SCREEN_OVERLAP_ITERATIONS',
  50,
  { min: 1 },
)
export const LAYOUT_SCREEN_OVERLAP_STRENGTH = envFloat(
  'VITE_LAYOUT_SCREEN_OVERLAP_STRENGTH',
  0.9,
  { min: 0, max: 1 },
)

/** Auto-stop the layout worker after this long. */
export const LAYOUT_AUTO_STOP_MS = envInt('VITE_LAYOUT_AUTO_STOP_MS', 8000, { min: 0 })
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
